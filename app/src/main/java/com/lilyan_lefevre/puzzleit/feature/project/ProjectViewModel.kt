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
 * ViewModel for project operations
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

    // If this is a one-shot event you clear after handling, StateFlow is OK but
    // SharedFlow would be another option. Here we keep your clear* API.
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
    fun createProject(imagePath: String, thumbnailPath: String, name: String, puzzleSize: Int, difficulty: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val project = projectRepository.createProject(imagePath, thumbnailPath, name, puzzleSize, difficulty)
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
     * Delete a project
     */
    fun deleteProject(project: Project) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                projectRepository.deleteProject(project)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete project: ${e.message}"
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
     * Clear project created event
     */
    fun clearProjectCreated() {
        _projectCreated.value = null
    }
}

