package com.lilyan_lefevre.puzzleit.feature.project

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
    private val projectDao: ProjectDao
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
     * Create a new project with automatic naming
     */
    suspend fun createProject(imagePath: String, thumbnailPath: String, name: String, puzzleSize: Int, difficulty: String): Project {
        val project = Project(
            name = name,
            puzzleSize = puzzleSize,
            difficulty = difficulty,
            imagePath = imagePath,
            thumbnailPath = thumbnailPath,
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
     * Delete a project
     */
    suspend fun deleteProject(project: Project) {
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
