package com.lilyan_lefevre.puzzleit.feature.history.data

import android.content.Context
import android.graphics.Bitmap
import com.lilyan_lefevre.puzzleit.feature.recognition.Match
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Keeps every scanned piece of a puzzle (cut-out photo, leads) and what the person said about it. */
@Singleton
class ScanHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ScanRecordDao,
) {
    fun history(projectId: String): Flow<List<ScanRecord>> = dao.observe(projectId)

    /** Stores the scan and returns its id. The photo goes in the puzzle's folder, so deleting the puzzle deletes it too. */
    suspend fun record(projectId: String, match: Match, piece: Bitmap): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dir = File(context.filesDir, "$projectId/scans").apply { mkdirs() }
        val file = File(dir, "$now.jpg")
        file.outputStream().use { piece.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        dao.insert(ScanRecord(projectId = projectId, createdAt = now, piecePath = file.absolutePath, leads = encodeLeads(match)))
    }

    suspend fun evaluate(id: Long, correct: Boolean, lead: Int) {
        dao.evaluate(id, if (correct) Verdict.CORRECT else Verdict.WRONG, if (correct) lead else -1)
    }

    suspend fun delete(record: ScanRecord) = withContext(Dispatchers.IO) {
        dao.delete(record.id)
        File(record.piecePath).delete()
    }
}
