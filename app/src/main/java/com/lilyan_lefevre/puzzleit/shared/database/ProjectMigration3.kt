package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 3 to version 4
 * Adds gridRows and gridCols fields to Project table
 */
val ProjectMigration3 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN gridRows INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE projects ADD COLUMN gridCols INTEGER NOT NULL DEFAULT 1")
    }
}
