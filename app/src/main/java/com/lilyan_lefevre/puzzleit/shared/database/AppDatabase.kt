package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for PuzzleHelper application
 */
@Database(
    entities = [Project::class],
    version = 4, // Incremented for grid dimensions
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
}
