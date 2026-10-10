package com.lilyan_lefevre.puzzleit.feature.account

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lilyan_lefevre.puzzleit.core.database.AppDatabase
import com.lilyan_lefevre.puzzleit.feature.account.data.AccountStore
import com.lilyan_lefevre.puzzleit.feature.account.data.PocketBaseClient
import com.lilyan_lefevre.puzzleit.feature.account.data.SyncRepository
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanHistoryRepository
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecord
import com.lilyan_lefevre.puzzleit.feature.history.data.Verdict
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhoto
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sync against a real PocketBase (opt-in: POCKETBASE_URL=http://127.0.0.1:8090 with server/pb_migrations applied, see server/README.md).
 * A second phone is simulated by emptying the local database and files, then syncing again.
 */
@RunWith(AndroidJUnit4::class)
class SyncIntegrationTest {

    private val server = System.getenv("POCKETBASE_URL")
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val client = PocketBaseClient(OkHttpClient())
    // The Keystore behind the encrypted preferences does not exist on the JVM: plain preferences here.
    private val prefs = context.getSharedPreferences("test-account", Context.MODE_PRIVATE)

    private fun freshDevice(): Pair<AppDatabase, SyncRepository> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        return db to SyncRepository(context, client, AccountStore(prefs), db.projectDao(), db.scanRecordDao(), db.progressPhotoDao())
    }

    private fun file(path: String, content: String) = File(context.filesDir, path).apply { parentFile!!.mkdirs(); writeText(content) }

    @Test
    fun `a puzzle, its scans and photos reach a second phone and deletions follow`() = runBlocking {
        assumeTrue("POCKETBASE_URL not set", server != null)
        val store = AccountStore(prefs)
        val email = "sync-${System.nanoTime()}@test.dev"
        store.signIn(server!!, client.register(server, email, "Password12345"))

        // Phone 1 has a puzzle with a scan and a progress photo.
        val (db1, sync1) = freshDevice()
        val box = file("p1/puzzle/original/box.jpg", "box"); val piece = file("p1/scans/100.jpg", "piece"); val photo = file("p1/progress/200.jpg", "photo")
        db1.projectDao().insertProject(Project(id = "p1", name = "La vague", puzzleSize = 1000, imagePath = box.path, thumbnailPath = box.path))
        db1.scanRecordDao().insert(ScanRecord(projectId = "p1", createdAt = 100, piecePath = piece.path, leads = "0.5,0.5,90,80"))
        db1.progressPhotoDao().insert(ProgressPhoto(projectId = "p1", createdAt = 200, path = photo.path))
        assertEquals(3, sync1.sync().uploaded)
        assertEquals(0, sync1.sync().uploaded)                                         // nothing to send twice

        // Phone 2 is empty: everything comes down, with its files.
        File(context.filesDir, "p1").deleteRecursively()
        val (db2, sync2) = freshDevice()
        assertEquals(3, sync2.sync().downloaded)
        val pulled = db2.projectDao().getProjectById("p1")!!
        assertEquals("La vague", pulled.name); assertEquals(1000, pulled.puzzleSize); assertEquals("box", File(pulled.imagePath).readText())
        val scan = db2.scanRecordDao().all("p1").single()
        assertEquals("piece", File(scan.piecePath).readText()); assertEquals("photo", File(db2.progressPhotoDao().all("p1").single().path).readText())

        // A verdict given on phone 2 reaches phone 1.
        db2.scanRecordDao().evaluate(scan.id, Verdict.CORRECT, 0)
        sync2.sync(); sync1.sync()
        assertEquals(Verdict.CORRECT, db1.scanRecordDao().all("p1").single().verdict)

        // A scan deleted on phone 1 is deleted on the server, and phone 1 does not download it again.
        // (Known limit: phone 2 still has it and would send it back; deletions are not propagated between phones yet.)
        val repo1 = ScanHistoryRepository(context, db1.scanRecordDao(), store)
        repo1.delete(db1.scanRecordDao().all("p1").single())
        sync1.sync()
        assertTrue(client.list(server, store.token!!, "scans").isEmpty())
        assertTrue(db1.scanRecordDao().all("p1").isEmpty())
        db1.close(); db2.close()
    }
}
