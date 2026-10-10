package com.lilyan_lefevre.puzzleit.feature.progress.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressPhotoDao {
    @Insert
    suspend fun insert(photo: ProgressPhoto): Long

    @Query("SELECT * FROM progress_photos WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observe(projectId: String): Flow<List<ProgressPhoto>>

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun delete(id: Long)
}
