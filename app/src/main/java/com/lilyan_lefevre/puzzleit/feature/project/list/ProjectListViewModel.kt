package com.lilyan_lefevre.puzzleit.feature.project.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.feature.account.data.AccountStore
import com.lilyan_lefevre.puzzleit.feature.account.data.BackendException
import com.lilyan_lefevre.puzzleit.feature.account.data.SyncRepository
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The project list, live from the database, and the sync with the server each time it shows (when signed in). */
@HiltViewModel
class ProjectListViewModel @Inject constructor(
    repository: ProjectRepository,
    private val account: AccountStore,
    private val sync: SyncRepository,
) : ViewModel() {

    val projects: StateFlow<List<Project>?> =
        repository.getAllProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncing: StateFlow<Boolean> = sync.syncing

    /** Brings the server's puzzles here (and ours there); the list updates itself through Room. Offline or off server: nothing to show, the list stays as it is. */
    fun refresh() {
        viewModelScope.launch {
            try {
                sync.syncIfStale()
            } catch (e: BackendException) {
                // A token the server no longer accepts: ask for the password again on the account screen rather than failing in silence forever.
                if (e.code == 401) account.signOut()
            }
        }
    }
}
