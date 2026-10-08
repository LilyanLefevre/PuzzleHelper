package com.lilyan_lefevre.puzzleit.feature.project

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.feature.project.data.ImageStorageManager
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for project list and creation operations
 */
@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val imageStorageManager: ImageStorageManager
) : ViewModel() {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _projectCreated = MutableStateFlow<Project?>(null)
    val projectCreated: StateFlow<Project?> = _projectCreated.asStateFlow()

    init {
        loadProjects()
    }

    /**
     * Load all projects
     */
    fun loadProjects() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                projectRepository.getAllProjects().collect { projectList ->
                    _projects.value = projectList
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load projects: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Create a new project with organized storage and all image versions
     */
    fun createProject(
        tempImagePath: String,
        name: String,
        puzzleSize: Int,
        gridRows: Int,
        gridCols: Int,
        difficulty: String,
        puzzleQuad: String?,
        originalBitmap: Bitmap,
        warpedBitmap: Bitmap
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val projectId = UUID.randomUUID().toString()
                
                // Save organized images (Redressing Original using EXIF from tempImagePath)
                val result = imageStorageManager.saveProjectBundle(
                    projectId, name, tempImagePath, originalBitmap, warpedBitmap
                )
                
                if (result is ImageStorageManager.ProjectBundleResult.Success) {
                    val project = projectRepository.createProject(
                        id = projectId,
                        imagePath = result.originalPath,
                        thumbnailPath = result.thumbPath,
                        warpedPath = result.warpedPath,
                        name = name,
                        puzzleSize = puzzleSize,
                        gridRows = gridRows,
                        gridCols = gridCols,
                        difficulty = difficulty,
                        puzzleQuad = puzzleQuad
                    )
                    _projectCreated.value = project
                } else if (result is ImageStorageManager.ProjectBundleResult.Error) {
                    _errorMessage.value = "Failed to save images: ${result.exception.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create project: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update an existing project
     */
    fun updateProject(project: Project) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                projectRepository.updateProject(project)
                _projectCreated.value = project
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update project: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear project created/updated event
     */
    fun clearProjectCreated() {
        _projectCreated.value = null
    }
}
