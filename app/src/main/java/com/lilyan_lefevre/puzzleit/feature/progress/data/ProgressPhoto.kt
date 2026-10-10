package com.lilyan_lefevre.puzzleit.feature.progress.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lilyan_lefevre.puzzleit.feature.project.data.Project

/** A photo of the puzzle as it stands on the table at one date. Deleted with its puzzle. */
@Entity(
    tableName = "progress_photos",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val createdAt: Long,
    val path: String,
)
