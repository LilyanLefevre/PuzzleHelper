package com.lilyan_lefevre.puzzleit.feature.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.feature.project.ProjectRepository
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing puzzle working screen state
 */
@HiltViewModel
class PuzzleWorkingViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Load project data by ID
     */
    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val foundProject = projectRepository.getProjectById(projectId)
                if (foundProject != null) {
                    _project.value = foundProject
                } else {
                    _errorMessage.value = "Project not found"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load project: ${e.message}"
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
}
