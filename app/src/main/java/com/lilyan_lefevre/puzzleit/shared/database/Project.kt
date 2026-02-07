package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a puzzle project
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val puzzleSize: Int, // Total pieces
    val gridRows: Int = 1, // Number of pieces vertically
    val gridCols: Int = 1, // Number of pieces horizontally
    val difficulty: String = "medium", // easy, medium, hard
    val creationDate: Long = System.currentTimeMillis(),
    val imagePath: String,
    val thumbnailPath: String,
    val puzzleQuad: String? = null,
    val status: String = "active"
)
