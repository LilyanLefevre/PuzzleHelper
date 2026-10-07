package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.feature.project.ProjectRepository
import com.lilyan_lefevre.puzzleit.feature.recognition.Analysis
import com.lilyan_lefevre.puzzleit.feature.recognition.Candidate
import com.lilyan_lefevre.puzzleit.feature.recognition.Grid
import com.lilyan_lefevre.puzzleit.feature.recognition.Match
import com.lilyan_lefevre.puzzleit.feature.recognition.PieceMatcher
import com.lilyan_lefevre.puzzleit.feature.recognition.PieceRecognizer
import com.lilyan_lefevre.puzzleit.feature.recognition.toBitmap
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface ScanState {
    data object Idle : ScanState
    data object Analyzing : ScanState
    data class Result(val match: Match, val cutout: Bitmap, val selected: Int = 0) : ScanState {
        val shown: Candidate get() = if (selected == 0) match.best else match.alternatives[selected - 1]
    }
    data object Blurry : ScanState
    data object NoPiece : ScanState
}

/** Working screen state: project, reference image + matcher, and the scan state machine. */
@HiltViewModel
class PuzzleWorkingViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val recognizer: PieceRecognizer,
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _reference = MutableStateFlow<Pair<Bitmap, Grid>?>(null)
    val reference: StateFlow<Pair<Bitmap, Grid>?> = _reference.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var matcher: PieceMatcher? = null

    fun loadProject(projectId: String) {
        if (_project.value?.id == projectId) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val p = projectRepository.getProjectById(projectId)
                if (p == null) { _errorMessage.value = "Project not found"; return@launch }
                // Prefer the rectified box and the grid typed at creation; old projects fall back to a computed grid.
                val path = p.warpedPath.takeIf { it.isNotBlank() && File(it).exists() } ?: p.imagePath
                val grid = if (p.gridRows > 1 || p.gridCols > 1) Grid(p.gridCols, p.gridRows) else null
                val prepared = recognizer.prepare(path, p.puzzleSize, grid)
                if (prepared == null) { _errorMessage.value = "Reference image not found"; return@launch }
                matcher = prepared.matcher
                _reference.value = prepared.display to prepared.matcher.grid
                _project.value = p
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete project: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun analyze(photoPath: String) {
        val m = matcher ?: return
        _scan.value = ScanState.Analyzing
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val a = recognizer.locate(m, photoPath)
            withContext(Dispatchers.IO) { File(photoPath).delete() }
            // Let the scan animation breathe: an instant answer feels fake.
            val wait = 1400 - (System.currentTimeMillis() - started)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            _scan.value = when (a) {
                is Analysis.Found -> ScanState.Result(a.match, a.cutout.toBitmap())
                is Analysis.Blurry -> ScanState.Blurry
                Analysis.NoPiece -> ScanState.NoPiece
            }
        }
    }

    fun select(index: Int) {
        (_scan.value as? ScanState.Result)?.let { _scan.value = it.copy(selected = index) }
    }

    fun dismiss() { _scan.value = ScanState.Idle }

    fun clearError() { _errorMessage.value = null }
}
