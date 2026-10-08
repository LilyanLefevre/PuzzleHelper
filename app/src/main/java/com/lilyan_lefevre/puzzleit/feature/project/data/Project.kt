package com.lilyan_lefevre.puzzleit.feature.project.data

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
    val gridRows: Int = 1,
    val gridCols: Int = 1,
    val difficulty: String = "medium",
    val creationDate: Long = System.currentTimeMillis(),
    val imagePath: String, // Original image path
    val thumbnailPath: String, // Thumbnail image path
    val warpedPath: String = "", // Redressed/High-res puzzle box image path
    val puzzleQuad: String? = null,
    val status: String = "active"
)
