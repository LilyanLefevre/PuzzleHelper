package com.lilyan_lefevre.puzzleit.feature.account.data

import android.content.Context
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecord
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecordDao
import com.lilyan_lefevre.puzzleit.feature.history.data.Verdict
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhoto
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhotoDao
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectDao
import com.lilyan_lefevre.puzzleit.feature.project.data.deleteBoxFiles
import com.lilyan_lefevre.puzzleit.feature.project.data.photoId
import com.lilyan_lefevre.puzzleit.feature.project.data.photoStamp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** What a sync moved: items sent to the server and items brought back from it. */
data class SyncReport(val uploaded: Int, val downloaded: Int)

/**
 * Makes the server and the device hold the same puzzles, scans and progress photos. Puzzles are matched by their local id (the id they were
 * created with), scans and photos by their date within the puzzle. Nothing is ever overwritten with older data: a missing item is copied to
 * the side that lacks it, a verdict given on one side reaches the other, and a deletion made here is replayed on the server (and recorded
 * there, so the other phones delete it too) before anything is downloaded, and of two box photos the newest wins. Likewise the latest edit of a puzzle's
 * name, piece count and grid wins, whichever device made it (a puzzle edited on two phones keeps only the later edit as a whole).
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    /** A sync that outlives the screen: the app calls it when it goes to the background, so what changed is on the server before the person leaves. */
    fun syncInBackground() {
        if (store.account.value == null) return
        scope.launch {
            try {
                sync()
            } catch (e: BackendException) {
                if (e.code == 401) store.signOut()
            }
        }
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

        replayDeletions(server, token, session.userId)
        applyRemoteDeletions(server, token)
        val remote = client.list(server, token, "puzzles").associateBy { it.getString("localId") }
        val local = projectDao.getAllProjects().first()

        for (p in local) {
            val existing = remote[p.id]
            val record = existing ?: client.create(server, token, "puzzles", puzzleFields(p, session.userId), puzzleFiles(p)).also { uploaded++ }
            if (existing != null) syncPuzzle(server, token, p, existing)
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

    private suspend fun replayDeletions(server: String, token: String, owner: String) {
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
            // The record the other phones look for: they delete the same thing instead of sending it back.
            try {
                client.create(server, token, "deletions", mapOf("owner" to owner, "key" to key))
            } catch (e: BackendException) {
                if (e.code != 400) throw e                                          // 400: already recorded
            }
            store.deletionDone(key)
        }
    }

    /** What the person deleted on another phone: deleted here too, before anything is sent, or this phone would put it back on the server. */
    private suspend fun applyRemoteDeletions(server: String, token: String) {
        for (r in client.list(server, token, "deletions")) {
            val parts = r.getString("key").split(':')
            val date = parts.getOrNull(2)?.toLongOrNull()
            when (parts[0]) {
                "puzzle" -> projectDao.getProjectById(parts[1])?.let {
                    projectDao.deleteProject(it)                                      // its scans and photos go with it (foreign key)
                    File(context.filesDir, it.id).deleteRecursively()
                }
                "scan" -> scanDao.all(parts[1]).filter { it.createdAt == date }.forEach { scanDao.delete(it.id); File(it.piecePath).delete() }
                "photo" -> photoDao.all(parts[1]).filter { it.createdAt == date }.forEach { photoDao.delete(it.id); File(it.path).delete() }
            }
        }
    }

    // ---- puzzles ----

    private fun puzzleFields(p: Project, owner: String) = mapOf(
        "owner" to owner, "localId" to p.id, "name" to p.name, "pieces" to p.puzzleSize.toString(), "gridRows" to p.gridRows.toString(),
        "gridCols" to p.gridCols.toString(), "difficulty" to p.difficulty, "createdAt" to p.creationDate.toString(),
        "puzzleQuad" to p.puzzleQuad.orEmpty(), "status" to p.status, "photoId" to p.photoId,
        "updatedAt" to p.updatedAt.toString(),
    )

    /** A puzzle both sides have: its name, piece count and grid (the latest edit wins) and its box photo (the newest one wins), whichever side they are on. */
    private suspend fun syncPuzzle(server: String, token: String, local: Project, r: JSONObject) {
        val fields = mutableMapOf<String, String>()
        var files = emptyMap<String, File>()
        var p = local

        val remoteEdit = r.optLong("updatedAt")
        if (p.updatedAt > remoteEdit) {
            fields += mapOf("name" to p.name, "pieces" to p.puzzleSize.toString(), "gridRows" to p.gridRows.toString(), "gridCols" to p.gridCols.toString(),
                "updatedAt" to p.updatedAt.toString())
        } else if (p.updatedAt < remoteEdit) {
            p = p.copy(name = r.getString("name"), puzzleSize = r.optInt("pieces", p.puzzleSize), gridRows = r.optInt("gridRows", p.gridRows),
                gridCols = r.optInt("gridCols", p.gridCols), updatedAt = remoteEdit)
            projectDao.updateProject(p)
            downloaded++
        }

        val remotePhoto = r.optString("photoId")
        val localStamp = photoStamp(p.photoId)
        val remoteStamp = photoStamp(remotePhoto)
        when {
            localStamp.isEmpty() -> {}                                                // no date in the name: nothing to compare
            remotePhoto.isEmpty() -> fields["photoId"] = p.photoId                     // a record from before photos were compared: same photo, just name it
            localStamp > remoteStamp -> { fields["photoId"] = p.photoId; fields["puzzleQuad"] = p.puzzleQuad.orEmpty(); files = puzzleFiles(p); uploaded++ }
            localStamp < remoteStamp -> pullBoxPhoto(server, token, p, r)
        }
        if (fields.isNotEmpty()) client.update(server, token, "puzzles", r.getString("id"), fields, files)
    }

    private suspend fun pullBoxPhoto(server: String, token: String, p: Project, r: JSONObject) {
        val (image, warped, thumb) = boxFiles(p.id, r.getString("photoId"))
        client.download(server, token, r, "image", image); client.download(server, token, r, "warped", warped); client.download(server, token, r, "thumb", thumb)
        projectDao.updateProject(p.copy(
            imagePath = image.pathIfFile().ifEmpty { warped.pathIfFile() }, thumbnailPath = thumb.pathIfFile().ifEmpty { image.pathIfFile() },
            warpedPath = warped.pathIfFile(), puzzleQuad = r.optString("puzzleQuad").ifEmpty { null },
        ))
        p.deleteBoxFiles()                                                             // the old photo, once the new one is in place
        downloaded++
    }

    private fun boxFiles(projectId: String, photoId: String): Triple<File, File, File> {
        val dir = File(context.filesDir, "$projectId/puzzle")
        return Triple(File(dir, "original/${photoId}_original.jpg"), File(dir, "extraites/${photoId}_warped.jpg"), File(dir, "original/${photoId}_thumb.jpg"))
    }

    private fun File.pathIfFile() = if (isFile) absolutePath else ""

    private fun puzzleFiles(p: Project) = mapOf("image" to p.imagePath, "warped" to p.warpedPath, "thumb" to p.thumbnailPath)
        .mapValues { File(it.value) }.filterValues { it.isFile }

    private suspend fun pullPuzzle(server: String, token: String, r: JSONObject) {
        val id = r.getString("localId")
        val (image, warped, thumb) = boxFiles(id, r.optString("photoId").ifEmpty { "box" })
        client.download(server, token, r, "image", image); client.download(server, token, r, "warped", warped); client.download(server, token, r, "thumb", thumb)
        projectDao.insertProject(
            Project(
                id = id, name = r.getString("name"), puzzleSize = r.optInt("pieces", 1000), gridRows = r.optInt("gridRows", 1), gridCols = r.optInt("gridCols", 1),
                difficulty = r.optString("difficulty", "medium"), creationDate = r.optLong("createdAt", System.currentTimeMillis()),
                imagePath = image.pathIfFile().ifEmpty { warped.pathIfFile() }, thumbnailPath = thumb.pathIfFile().ifEmpty { image.pathIfFile() }, warpedPath = warped.pathIfFile(),
                puzzleQuad = r.optString("puzzleQuad").ifEmpty { null }, status = r.optString("status", "active"), updatedAt = r.optLong("updatedAt"),
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
