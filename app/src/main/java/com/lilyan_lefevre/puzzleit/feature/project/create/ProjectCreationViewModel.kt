package com.lilyan_lefevre.puzzleit.feature.project.create

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.feature.project.data.ImageStorageManager
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectRepository
import com.lilyan_lefevre.puzzleit.feature.project.data.deleteBoxFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Create or edit a project: holds the photo draft (survives rotation), validates the form, saves. */
@HiltViewModel
class ProjectCreationViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val storage: ImageStorageManager,
    private val processor: BoxImageProcessor,
) : ViewModel() {

    /** Box photo as taken, its rectified version and the corners used. */
    data class Draft(val originalPath: String? = null, val rectifiedPath: String? = null, val quadJson: String? = null) {
        val preview: String? get() = rectifiedPath ?: originalPath
    }

    sealed interface Saved {
        data class Created(val project: Project) : Saved
        data object Updated : Saved
    }

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    private val _existing = MutableStateFlow<Project?>(null)
    val existing: StateFlow<Project?> = _existing.asStateFlow()

    private val _saved = MutableStateFlow<Saved?>(null)
    val saved: StateFlow<Saved?> = _saved.asStateFlow()

    /** A string resource to show, or a raw message from an exception. */
    private val _error = MutableStateFlow<Any?>(null)
    val error: StateFlow<Any?> = _error.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val isEditing: Boolean get() = _existing.value != null

    fun loadForEdit(projectId: String) {
        if (_existing.value?.id == projectId) return
        viewModelScope.launch {
            val p = repository.getProjectById(projectId) ?: return@launch
            _existing.value = p
            if (_draft.value.originalPath == null) {
                _draft.value = Draft(p.imagePath, p.warpedPath.ifEmpty { p.thumbnailPath }, p.puzzleQuad)
            }
        }
    }

    fun onPhotoTaken(path: String) {
        // A retake replaces the previous capture, which would otherwise stay in the cache.
        val own = _existing.value?.let { listOf(it.imagePath, it.thumbnailPath, it.warpedPath) }.orEmpty()
        _draft.value.let { listOf(it.originalPath, it.rectifiedPath) }.filterNotNull().filter { it !in own && it != path }.forEach { File(it).delete() }
        _draft.value = Draft(path, null, processor.defaultQuadJson())
    }

    fun onBoundsConfirmed(quadJson: String, rectifiedPath: String) {
        _draft.value = _draft.value.copy(quadJson = quadJson, rectifiedPath = rectifiedPath)
    }

    /** Grid closest to [pieces] for the box's aspect ratio, or null without a photo. */
    fun suggestGrid(pieces: Int): GridProcessor.GridConfig? {
        val path = _draft.value.preview ?: return null
        val (w, h) = processor.imageSize(path) ?: return null
        return GridProcessor.calculateBestGrid(pieces, w, h)
    }

    fun save(name: String, pieces: Int?, rows: Int?, cols: Int?) {
        val d = _draft.value
        val problem: Int? = when {
            d.originalPath == null -> R.string.please_take_photo
            name.isBlank() -> R.string.please_enter_project_name
            pieces == null -> R.string.please_enter_number_of_pieces
            else -> null
        }
        if (problem != null) { _error.value = problem; return }
        val original = d.originalPath!!
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val existing = _existing.value
                if (existing != null) {
                    var updated = existing.copy(name = name.trim(), puzzleSize = pieces!!, gridRows = rows ?: 1, gridCols = cols ?: 1, puzzleQuad = d.quadJson)
                    // A new photo or a new framing: the draft files are temporary, so they are copied into the project like on creation.
                    if (original != existing.imagePath || d.rectifiedPath != existing.warpedPath.ifEmpty { existing.thumbnailPath }) {
                        val originalBmp = processor.decode(original) ?: run { _error.value = R.string.photo_load_failed; return@launch }
                        val warpedBmp = d.rectifiedPath?.let { processor.decode(it) } ?: originalBmp
                        when (val r = storage.saveProjectBundle(existing.id, name.trim(), original, originalBmp, warpedBmp)) {
                            is ImageStorageManager.ProjectBundleResult.Success -> {
                                updated = updated.copy(imagePath = r.originalPath, thumbnailPath = r.thumbPath, warpedPath = r.warpedPath)
                                existing.deleteBoxFiles()
                                processor.discardTemporary(original, d.rectifiedPath)
                            }
                            is ImageStorageManager.ProjectBundleResult.Error -> { _error.value = r.exception.message; return@launch }
                        }
                    }
                    repository.updateProject(updated)
                    _saved.value = Saved.Updated
                } else {
                    val originalBmp = processor.decode(original) ?: run { _error.value = R.string.photo_load_failed; return@launch }
                    val warpedBmp = d.rectifiedPath?.let { processor.decode(it) } ?: originalBmp
                    val id = UUID.randomUUID().toString()
                    when (val r = storage.saveProjectBundle(id, name.trim(), original, originalBmp, warpedBmp)) {
                        is ImageStorageManager.ProjectBundleResult.Success -> {
                            val p = repository.createProject(id, r.originalPath, r.thumbPath, r.warpedPath, name.trim(),
                                pieces!!, rows ?: 1, cols ?: 1, "medium", d.quadJson)
                            discardDraft()
                            _saved.value = Saved.Created(p)
                        }
                        is ImageStorageManager.ProjectBundleResult.Error -> _error.value = r.exception.message
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Leaving without saving: temporary captures are deleted, project files never. */
    fun discardDraft() {
        processor.discardTemporary(_draft.value.originalPath, _draft.value.rectifiedPath)
        if (!isEditing) _draft.value = Draft()
    }

    fun errorShown() { _error.value = null }
    fun savedHandled() { _saved.value = null }

    @StringRes fun titleRes(): Int = if (isEditing) R.string.edit_project_title else R.string.create_puzzle_title
}
