package com.lilyan_lefevre.puzzleit.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecord
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecordDao
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhoto
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhotoDao
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectDao

/**
 * Room database for PuzzleHelper application
 */
@Database(
    entities = [Project::class, ScanRecord::class, ProgressPhoto::class],
    version = 6, // 6: scan history and progress photos
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun scanRecordDao(): ScanRecordDao
    abstract fun progressPhotoDao(): ProgressPhotoDao
}
