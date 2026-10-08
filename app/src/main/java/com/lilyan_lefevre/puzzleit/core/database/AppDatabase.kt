package com.lilyan_lefevre.puzzleit.core.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectDao

/**
 * Room database for PuzzleHelper application
 */
@Database(
    entities = [Project::class],
    version = 5, // Incremented for warpedPath
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
}
