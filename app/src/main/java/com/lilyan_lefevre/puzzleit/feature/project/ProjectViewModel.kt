package com.lilyan_lefevre.puzzleit.feature.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for project list and creation operations
 */
@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
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
     * Create a new project
     */
    fun createProject(
        imagePath: String,
        thumbnailPath: String,
        name: String,
        puzzleSize: Int,
        gridRows: Int,
        gridCols: Int,
        difficulty: String,
        puzzleQuad: String?
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val project = projectRepository.createProject(
                    imagePath, 
                    thumbnailPath, 
                    name, 
                    puzzleSize, 
                    gridRows,
                    gridCols,
                    difficulty, 
                    puzzleQuad
                )
                _projectCreated.value = project
                _errorMessage.value = null
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
                // We reuse projectCreated flow to notify the UI of success
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
