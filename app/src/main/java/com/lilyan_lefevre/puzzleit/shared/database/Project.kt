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
    val puzzleSize: Int, // e.g., "1000", "500"
    val difficulty: String = "medium", // easy, medium, hard
    val creationDate: Long = System.currentTimeMillis(),
    val imagePath: String,
    val thumbnailPath: String,
    val status: String = "active"
)
