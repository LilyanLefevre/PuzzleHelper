package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 1 to version 2
 * Adds puzzleSize and difficulty fields to Project table
 */
val ProjectMigration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns to projects table
        database.execSQL("ALTER TABLE projects ADD COLUMN puzzleSize TEXT NOT NULL DEFAULT '1000 pieces'")
        database.execSQL("ALTER TABLE projects ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'medium'")
    }
}
