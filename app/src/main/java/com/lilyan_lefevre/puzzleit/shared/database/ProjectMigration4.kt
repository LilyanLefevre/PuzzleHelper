package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 4 to version 5
 * Adds warpedPath field to Project table
 */
val ProjectMigration4 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN warpedPath TEXT NOT NULL DEFAULT ''")
    }
}
