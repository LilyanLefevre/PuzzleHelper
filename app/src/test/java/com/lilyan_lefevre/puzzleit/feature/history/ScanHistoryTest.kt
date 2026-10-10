package com.lilyan_lefevre.puzzleit.feature.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lilyan_lefevre.puzzleit.core.database.AppDatabase
import com.lilyan_lefevre.puzzleit.core.database.ProjectMigration5
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecord
import com.lilyan_lefevre.puzzleit.feature.history.data.Verdict
import com.lilyan_lefevre.puzzleit.feature.history.data.decodeLeads
import com.lilyan_lefevre.puzzleit.feature.history.data.encodeLeads
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhoto
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.recognition.Candidate
import com.lilyan_lefevre.puzzleit.feature.recognition.Grid
import com.lilyan_lefevre.puzzleit.feature.recognition.Match
import com.lilyan_lefevre.puzzleit.feature.recognition.Precision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The scan history and the progress photos: leads survive their text form, a verdict is kept, and both go with their puzzle. */
@RunWith(AndroidJUnit4::class)
class ScanHistoryTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.projectDao().insertProject(Project(id = "p", name = "P", puzzleSize = 500, imagePath = "i", thumbnailPath = "t"))
        }
    }

    @After
    fun closeDb() = db.close()

    private fun match() = Match(
        grid = Grid(20, 10),
        best = Candidate(col = 5f, row = 2.5f, distance = 0f, rotationDeg = 90, confidence = 81),
        alternatives = listOf(Candidate(col = 15f, row = 7.5f, distance = 0f, rotationDeg = 180, confidence = 12)),
        confidence = 81,
        precision = Precision.CELL,
    )

    @Test
    fun `leads are stored as fractions of the box and read back`() {
        val leads = decodeLeads(encodeLeads(match()))
        assertEquals(2, leads.size)
        assertEquals(0.25f, leads[0].x, 1e-3f)
        assertEquals(0.25f, leads[0].y, 1e-3f)
        assertEquals(90, leads[0].rotationDeg)
        assertEquals(0.75f, leads[1].x, 1e-3f)
        assertEquals(12, leads[1].confidence)
    }

    @Test
    fun `a verdict is kept with the confirmed lead, and the history shows that lead`() = runBlocking {
        val dao = db.scanRecordDao()
        val id = dao.insert(ScanRecord(projectId = "p", createdAt = 1, piecePath = "x", leads = encodeLeads(match())))
        assertEquals(Verdict.UNKNOWN, dao.get(id)!!.verdict)

        dao.evaluate(id, Verdict.CORRECT, 1)

        val saved = db.scanRecordDao().observe("p").first().single()
        assertEquals(Verdict.CORRECT, saved.verdict)
        assertEquals(0.75f, saved.shownLead!!.x, 1e-3f)
    }

    @Test
    fun `an existing database gets the new tables and keeps its puzzles`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("migration-test")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test").addMigrations(ProjectMigration5).allowMainThreadQueries().build()

        // A version 5 database: today's schema without the two new tables.
        open().apply {
            projectDao().insertProject(Project(id = "old", name = "Old", puzzleSize = 300, imagePath = "i", thumbnailPath = "t"))
            openHelper.writableDatabase.apply {
                execSQL("DROP TABLE scans"); execSQL("DROP TABLE progress_photos"); execSQL("PRAGMA user_version = 5")
            }
            close()
        }
        // Room validates the migrated schema against the entities and throws when they differ.
        val migrated = open()
        assertEquals("Old", migrated.projectDao().getProjectById("old")!!.name)
        val id = migrated.scanRecordDao().insert(ScanRecord(projectId = "old", createdAt = 1, piecePath = "x", leads = encodeLeads(match())))
        assertEquals(Verdict.UNKNOWN, migrated.scanRecordDao().get(id)!!.verdict)
        migrated.close()
    }

    @Test
    fun `scans and progress photos are deleted with their puzzle`() = runBlocking {
        val id = db.scanRecordDao().insert(ScanRecord(projectId = "p", createdAt = 1, piecePath = "x", leads = encodeLeads(match())))
        db.progressPhotoDao().insert(ProgressPhoto(projectId = "p", createdAt = 1, path = "y"))

        db.projectDao().deleteProject(db.projectDao().getProjectById("p")!!)

        assertNull(db.scanRecordDao().get(id))
        assertEquals(0, db.progressPhotoDao().observe("p").first().size)
    }
}
