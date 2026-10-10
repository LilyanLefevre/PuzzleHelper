package com.lilyan_lefevre.puzzleit.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanHistoryRepository
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The scans of one puzzle (its id comes in the fragment's arguments), live from Room. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ScanHistoryRepository,
) : ViewModel() {

    val scans: StateFlow<List<ScanRecord>> = repository.history(savedState.get<String>("projectId").orEmpty())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(record: ScanRecord) {
        viewModelScope.launch { repository.delete(record) }
    }
}
