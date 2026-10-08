package com.lilyan_lefevre.puzzleit.feature.project.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Project entities
 */
@Dao
interface ProjectDao {

    @Insert
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("SELECT * FROM projects ORDER BY creationDate DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): Project?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectByIdFlow(id: String): Flow<Project?>

    @Query("SELECT * FROM projects WHERE status = :status ORDER BY creationDate DESC")
    fun getProjectsByStatus(status: String): Flow<List<Project>>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()
}
