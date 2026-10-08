package com.lilyan_lefevre.puzzleit.feature.project.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The project list, live from the database. */
@HiltViewModel
class ProjectListViewModel @Inject constructor(repository: ProjectRepository) : ViewModel() {
    val projects: StateFlow<List<Project>?> =
        repository.getAllProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
