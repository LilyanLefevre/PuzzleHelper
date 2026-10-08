package com.lilyan_lefevre.puzzleit.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: puzzle size and difficulty. */
val ProjectMigration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN puzzleSize TEXT NOT NULL DEFAULT '1000 pieces'")
        db.execSQL("ALTER TABLE projects ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'medium'")
    }
}

/** v2 -> v3: box corners chosen by the user. */
val ProjectMigration2 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN puzzleQuad TEXT")
    }
}

/** v3 -> v4: grid rows and columns typed at creation. */
val ProjectMigration3 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN gridRows INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE projects ADD COLUMN gridCols INTEGER NOT NULL DEFAULT 1")
    }
}

/** v4 -> v5: rectified box image. */
val ProjectMigration4 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN warpedPath TEXT NOT NULL DEFAULT ''")
    }
}
