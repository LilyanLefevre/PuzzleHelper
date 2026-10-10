package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanHistoryRepository
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectRepository
import com.lilyan_lefevre.puzzleit.feature.recognition.Analysis
import com.lilyan_lefevre.puzzleit.feature.recognition.Candidate
import com.lilyan_lefevre.puzzleit.feature.recognition.Grid
import com.lilyan_lefevre.puzzleit.feature.recognition.Match
import com.lilyan_lefevre.puzzleit.feature.recognition.PieceMatcher
import com.lilyan_lefevre.puzzleit.feature.recognition.PieceRecognizer
import com.lilyan_lefevre.puzzleit.feature.recognition.toBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data object Analyzing : ScanState
    /** [recordId] is the scan in the history (0 when it could not be stored), [evaluated] that the person already said if the lead was right. */
    data class Result(val match: Match, val cutout: Bitmap, val selected: Int = 0, val recordId: Long = 0, val evaluated: Boolean = false) : ScanState {
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
    private val history: ScanHistoryRepository,
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _reference = MutableStateFlow<Pair<Bitmap, Grid>?>(null)
    val reference: StateFlow<Pair<Bitmap, Grid>?> = _reference.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** A string resource to show before leaving the screen. */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private var matcher: PieceMatcher? = null
    private var watching: String? = null
    private var preparedFor: Pair<String, Int>? = null
    private var deleting = false

    /**
     * Follows the project live: a rename only refreshes the header, a new photo or piece count re-prepares the matcher,
     * and a deletion closes the screen.
     */
    fun loadProject(projectId: String) {
        if (watching == projectId) return
        watching = projectId
        viewModelScope.launch {
            projectRepository.getProjectByIdFlow(projectId).collect { p ->
                if (p == null) {
                    if (deleting || _project.value != null) _deleted.value = true else _error.value = R.string.project_not_found
                    return@collect
                }
                _project.value = p
                // The rectified box, else the original photo. The rows x columns typed at creation are NOT used: nobody knows them
                // before the border is complete; the size of a piece comes from the piece count (printed on the box).
                val path = p.warpedPath.takeIf { it.isNotBlank() && File(it).exists() } ?: p.imagePath
                val key = path to p.puzzleSize
                if (key == preparedFor) return@collect
                _isLoading.value = true
                try {
                    val prepared = recognizer.prepare(path, p.puzzleSize)
                    if (prepared == null) { _error.value = R.string.reference_missing; return@collect }
                    matcher = prepared.matcher
                    preparedFor = key
                    _reference.value = prepared.display to prepared.matcher.grid
                    _scan.value = ScanState.Idle
                } catch (e: Exception) {
                    _error.value = R.string.reference_missing
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    fun delete() {
        val id = watching ?: return
        deleting = true
        viewModelScope.launch {
            if (projectRepository.deleteProject(id).isFailure) { deleting = false; _error.value = R.string.project_deletion_failed }
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
                is Analysis.Found -> {
                    val cutout = a.cutout.toBitmap()
                    // A failed save must not hide the answer: the scan just stays out of the history.
                    val id = runCatching { history.record(watching.orEmpty(), a.match, cutout) }.getOrDefault(0L)
                    ScanState.Result(a.match, cutout, recordId = id)
                }
                is Analysis.Blurry -> ScanState.Blurry
                Analysis.NoPiece -> ScanState.NoPiece
            }
        }
    }

    fun select(index: Int) {
        (_scan.value as? ScanState.Result)?.let { _scan.value = it.copy(selected = index, evaluated = false) }
    }

    /** The person says whether the lead on screen is where the piece goes (feeds the history, and later the training data). */
    fun evaluate(correct: Boolean) {
        val r = _scan.value as? ScanState.Result ?: return
        _scan.value = r.copy(evaluated = true)
        if (r.recordId != 0L) viewModelScope.launch { history.evaluate(r.recordId, correct, r.selected) }
    }

    fun dismiss() { _scan.value = ScanState.Idle }

    override fun onCleared() { recognizer.cancelWarmUp() }

    fun errorShown() { _error.value = null }
}
