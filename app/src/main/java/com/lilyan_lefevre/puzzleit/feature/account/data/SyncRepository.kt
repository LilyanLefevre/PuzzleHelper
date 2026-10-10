package com.lilyan_lefevre.puzzleit.feature.account.data

import android.content.Context
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecord
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecordDao
import com.lilyan_lefevre.puzzleit.feature.history.data.Verdict
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhoto
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhotoDao
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** What a sync moved: items sent to the server and items brought back from it. */
data class SyncReport(val uploaded: Int, val downloaded: Int)

/**
 * Makes the server and the device hold the same puzzles, scans and progress photos. Puzzles are matched by their local id (the id they were
 * created with), scans and photos by their date within the puzzle. Nothing is ever overwritten with older data: a missing item is copied to
 * the side that lacks it, a verdict given on one side reaches the other, and a deletion made here is replayed on the server before anything is
 * downloaded. Conflicting edits of a puzzle's name: the device that syncs last wins.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: PocketBaseClient,
    private val store: AccountStore,
    private val projectDao: ProjectDao,
    private val scanDao: ScanRecordDao,
    private val photoDao: ProgressPhotoDao,
) {
    private val lock = Mutex()
    private var uploaded = 0
    private var downloaded = 0
    private var lastSyncAt = 0L

    private val _syncing = MutableStateFlow(false)
    /** True while a sync runs, for a discreet progress bar. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** The sync the list triggers each time it shows: nothing when signed out or when the last sync is recent. */
    suspend fun syncIfStale(maxAgeMs: Long = 30_000): SyncReport? {
        if (store.account.value == null || System.currentTimeMillis() - lastSyncAt < maxAgeMs) return null
        return sync()
    }

    suspend fun sync(): SyncReport = lock.withLock {
        _syncing.value = true
        try { run() } finally { _syncing.value = false }
    }

    private suspend fun run(): SyncReport {
        uploaded = 0; downloaded = 0
        val account = store.account.value ?: throw BackendException("not signed in")
        val server = account.server
        val session = client.refresh(server, store.token ?: throw BackendException("not signed in"))
        store.updateToken(session)
        val token = session.token

        replayDeletions(server, token)
        val remote = client.list(server, token, "puzzles").associateBy { it.getString("localId") }
        val local = projectDao.getAllProjects().first()

        for (p in local) {
            val record = remote[p.id] ?: client.create(server, token, "puzzles", puzzleFields(p, session.userId), puzzleFiles(p)).also { uploaded++ }
            if (remote[p.id] != null && record.getString("name") != p.name) client.update(server, token, "puzzles", record.getString("id"), mapOf("name" to p.name))
            syncScans(server, token, session.userId, p.id, record.getString("id"))
            syncPhotos(server, token, session.userId, p.id, record.getString("id"))
        }
        for ((localId, record) in remote) {
            if (local.any { it.id == localId }) continue
            pullPuzzle(server, token, record)
            syncScans(server, token, session.userId, localId, record.getString("id"))
            syncPhotos(server, token, session.userId, localId, record.getString("id"))
            downloaded++
        }
        lastSyncAt = System.currentTimeMillis()
        return SyncReport(uploaded, downloaded)
    }

    // ---- deletions made on this device ----

    private suspend fun replayDeletions(server: String, token: String) {
        for (key in store.pendingDeletions) {
            val parts = key.split(':')
            try {
                val puzzle = client.list(server, token, "puzzles", "localId=\"${parts[1]}\"").firstOrNull()
                when (parts[0]) {
                    "puzzle" -> puzzle?.let { client.delete(server, token, "puzzles", it.getString("id")) }
                    "scan", "photo" -> if (puzzle != null) {
                        val collection = if (parts[0] == "scan") "scans" else "progress_photos"
                        client.list(server, token, collection, "puzzle=\"${puzzle.getString("id")}\" && createdAt=${parts[2]}")
                            .forEach { client.delete(server, token, collection, it.getString("id")) }
                    }
                }
            } catch (e: BackendException) {
                if (e.code != 404) throw e
            }
            store.deletionDone(key)
        }
    }

    // ---- puzzles ----

    private fun puzzleFields(p: Project, owner: String) = mapOf(
        "owner" to owner, "localId" to p.id, "name" to p.name, "pieces" to p.puzzleSize.toString(), "gridRows" to p.gridRows.toString(),
        "gridCols" to p.gridCols.toString(), "difficulty" to p.difficulty, "createdAt" to p.creationDate.toString(),
        "puzzleQuad" to p.puzzleQuad.orEmpty(), "status" to p.status,
    )

    private fun puzzleFiles(p: Project) = mapOf("image" to p.imagePath, "warped" to p.warpedPath, "thumb" to p.thumbnailPath)
        .mapValues { File(it.value) }.filterValues { it.isFile }

    private suspend fun pullPuzzle(server: String, token: String, r: JSONObject) {
        val id = r.getString("localId")
        val dir = File(context.filesDir, "$id/puzzle")
        val image = File(dir, "original/box.jpg"); val warped = File(dir, "extraites/box_warped.jpg"); val thumb = File(dir, "original/thumb.jpg")
        client.download(server, token, r, "image", image); client.download(server, token, r, "warped", warped); client.download(server, token, r, "thumb", thumb)
        fun path(f: File) = if (f.isFile) f.absolutePath else ""
        projectDao.insertProject(
            Project(
                id = id, name = r.getString("name"), puzzleSize = r.optInt("pieces", 1000), gridRows = r.optInt("gridRows", 1), gridCols = r.optInt("gridCols", 1),
                difficulty = r.optString("difficulty", "medium"), creationDate = r.optLong("createdAt", System.currentTimeMillis()),
                imagePath = path(image).ifEmpty { path(warped) }, thumbnailPath = path(thumb).ifEmpty { path(image) }, warpedPath = path(warped),
                puzzleQuad = r.optString("puzzleQuad").ifEmpty { null }, status = r.optString("status", "active"),
            )
        )
    }

    // ---- scans ----

    private suspend fun syncScans(server: String, token: String, owner: String, projectId: String, remoteId: String) {
        val remote = client.list(server, token, "scans", "puzzle=\"$remoteId\"").associateBy { it.getLong("createdAt") }
        val local = scanDao.all(projectId).associateBy { it.createdAt }
        for ((date, s) in local) {
            val r = remote[date]
            if (r == null) {
                client.create(server, token, "scans", mapOf("owner" to owner, "puzzle" to remoteId, "createdAt" to date.toString(), "leads" to s.leads,
                    "verdict" to s.verdict, "chosenLead" to s.chosenLead.toString()), mapOf("piece" to File(s.piecePath)).filterValues { it.isFile })
                uploaded++
            } else if (s.verdict != Verdict.UNKNOWN && r.optString("verdict") != s.verdict) {
                client.update(server, token, "scans", r.getString("id"), mapOf("verdict" to s.verdict, "chosenLead" to s.chosenLead.toString()))
            }
        }
        for ((date, r) in remote) {
            val s = local[date]
            if (s == null) {
                val file = File(context.filesDir, "$projectId/scans/$date.jpg")
                client.download(server, token, r, "piece", file)
                scanDao.insert(ScanRecord(projectId = projectId, createdAt = date, piecePath = file.absolutePath, leads = r.optString("leads"),
                    verdict = r.optString("verdict", Verdict.UNKNOWN).ifEmpty { Verdict.UNKNOWN }, chosenLead = r.optInt("chosenLead", -1)))
                downloaded++
            } else if (s.verdict == Verdict.UNKNOWN && r.optString("verdict").let { it.isNotEmpty() && it != Verdict.UNKNOWN }) {
                scanDao.evaluate(s.id, r.getString("verdict"), r.optInt("chosenLead", -1))
            }
        }
    }

    // ---- progress photos ----

    private suspend fun syncPhotos(server: String, token: String, owner: String, projectId: String, remoteId: String) {
        val remote = client.list(server, token, "progress_photos", "puzzle=\"$remoteId\"").associateBy { it.getLong("createdAt") }
        val local = photoDao.all(projectId).associateBy { it.createdAt }
        for ((date, p) in local) {
            if (remote[date] != null || !File(p.path).isFile) continue
            client.create(server, token, "progress_photos", mapOf("owner" to owner, "puzzle" to remoteId, "createdAt" to date.toString()), mapOf("photo" to File(p.path)))
            uploaded++
        }
        for ((date, r) in remote) {
            if (local[date] != null) continue
            val file = File(context.filesDir, "$projectId/progress/$date.jpg")
            client.download(server, token, r, "photo", file)
            photoDao.insert(ProgressPhoto(projectId = projectId, createdAt = date, path = file.absolutePath))
            downloaded++
        }
    }
}
