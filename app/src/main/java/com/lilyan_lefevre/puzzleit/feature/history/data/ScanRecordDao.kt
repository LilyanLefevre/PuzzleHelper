package com.lilyan_lefevre.puzzleit.feature.history.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {
    @Insert
    suspend fun insert(record: ScanRecord): Long

    @Query("UPDATE scans SET verdict = :verdict, chosenLead = :lead WHERE id = :id")
    suspend fun evaluate(id: Long, verdict: String, lead: Int)

    @Query("SELECT * FROM scans WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observe(projectId: String): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scans WHERE projectId = :projectId")
    suspend fun all(projectId: String): List<ScanRecord>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun get(id: Long): ScanRecord?

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)
}
