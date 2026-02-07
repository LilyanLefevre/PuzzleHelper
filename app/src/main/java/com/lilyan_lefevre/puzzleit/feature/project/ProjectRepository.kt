package com.lilyan_lefevre.puzzleit.feature.project

import com.lilyan_lefevre.puzzleit.feature.storage.ImageStorageManager
import com.lilyan_lefevre.puzzleit.shared.database.Project
import com.lilyan_lefevre.puzzleit.shared.database.ProjectDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for project data operations
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val imageStorageManager: ImageStorageManager
) {

    /**
     * Get all projects as a flow
     */
    fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects()
    }

    /**
     * Get project by ID
     */
    suspend fun getProjectById(id: String): Project? {
        return projectDao.getProjectById(id)
    }

    /**
     * Get project by ID as a flow
     */
    fun getProjectByIdFlow(id: String): Flow<Project?> {
        return projectDao.getProjectByIdFlow(id)
    }

    /**
     * Create a new project with automatic naming
     */
    suspend fun createProject(
        imagePath: String,
        thumbnailPath: String,
        name: String,
        puzzleSize: Int,
        gridRows: Int,
        gridCols: Int,
        difficulty: String,
        puzzleQuad: String?
    ): Project {
        val project = Project(
            name = name,
            puzzleSize = puzzleSize,
            gridRows = gridRows,
            gridCols = gridCols,
            difficulty = difficulty,
            imagePath = imagePath,
            thumbnailPath = thumbnailPath,
            puzzleQuad = puzzleQuad,
            status = "active"
        )
        projectDao.insertProject(project)
        return project
    }

    /**
     * Update an existing project
     */
    suspend fun updateProject(project: Project) {
        projectDao.updateProject(project)
    }

    /**
     * Delete a project with associated files
     */
    suspend fun deleteProject(projectId: String): Result<Boolean> {
        return try {
            val project = projectDao.getProjectById(projectId)
            if (project != null) {
                // Delete associated files first
                imageStorageManager.deleteProjectImages(
                    project.imagePath, 
                    project.thumbnailPath
                )
                
                // Then delete database record
                projectDao.deleteProject(project)
                
                Result.success(true)
            } else {
                Result.failure(Exception("Project not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a project (legacy method for backward compatibility)
     */
    suspend fun deleteProject(project: Project) {
        // Delete associated files first
        imageStorageManager.deleteProjectImages(project.imagePath, project.thumbnailPath)
        // Then delete database record
        projectDao.deleteProject(project)
    }

    /**
     * Delete project by ID
     */
    suspend fun deleteProjectById(id: String) {
        projectDao.deleteProjectById(id)
    }

    /**
     * Get projects by status
     */
    fun getProjectsByStatus(status: String): Flow<List<Project>> {
        return projectDao.getProjectsByStatus(status)
    }
}
