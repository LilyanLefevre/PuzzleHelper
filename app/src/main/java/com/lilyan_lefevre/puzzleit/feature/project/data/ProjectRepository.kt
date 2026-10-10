package com.lilyan_lefevre.puzzleit.feature.project.data

import com.lilyan_lefevre.puzzleit.feature.account.data.AccountStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Repository for project data operations
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val imageStorageManager: ImageStorageManager,
    private val account: AccountStore,
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
     * Create a new project with a specific ID
     */
    suspend fun createProject(
        id: String,
        imagePath: String,
        thumbnailPath: String,
        warpedPath: String,
        name: String,
        puzzleSize: Int,
        gridRows: Int,
        gridCols: Int,
        difficulty: String,
        puzzleQuad: String?
    ): Project {
        val project = Project(
            id = id,
            name = name,
            puzzleSize = puzzleSize,
            gridRows = gridRows,
            gridCols = gridCols,
            difficulty = difficulty,
            imagePath = imagePath,
            thumbnailPath = thumbnailPath,
            warpedPath = warpedPath,
            puzzleQuad = puzzleQuad,
            status = "active"
        )
        projectDao.insertProject(project)
        account.localChange()
        return project
    }

    /**
     * Update an existing project
     */
    suspend fun updateProject(project: Project) {
        projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
        account.localChange()
    }

    /**
     * Delete a project with associated files
     */
    suspend fun deleteProject(projectId: String): Result<Boolean> {
        return try {
            val project = projectDao.getProjectById(projectId)
            if (project != null) {
                imageStorageManager.deleteProjectImages(project.id)
                projectDao.deleteProject(project)
                account.markDeleted("puzzle:$projectId")
                Result.success(true)
            } else {
                Result.failure(Exception("Project not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get projects by status
     */
    fun getProjectsByStatus(status: String): Flow<List<Project>> {
        return projectDao.getProjectsByStatus(status)
    }
}
