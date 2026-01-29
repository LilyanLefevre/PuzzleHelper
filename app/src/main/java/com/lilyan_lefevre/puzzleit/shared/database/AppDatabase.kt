package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for PuzzleHelper application
 */
@Database(
    entities = [Project::class],
    version = 2, // Increment version for new fields
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
}
