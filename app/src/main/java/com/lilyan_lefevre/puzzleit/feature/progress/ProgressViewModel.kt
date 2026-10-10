package com.lilyan_lefevre.puzzleit.feature.progress

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.core.image.PhotoHelper
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhoto
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The progress photos of one puzzle (its id comes in the fragment's arguments), live from Room. */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val repository: ProgressRepository,
) : ViewModel() {

    private val projectId = savedState.get<String>("projectId").orEmpty()

    val photos: StateFlow<List<ProgressPhoto>> = repository.photos(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The file the camera is writing to; it outlives a screen rotation. */
    private var pending: File? = null

    fun newPhotoFile(): File = PhotoHelper.createImageFile(context, "progress_").also { pending = it }

    /** The camera is back: keep the photo, or drop the empty file when the person cancelled. */
    fun photoTaken(success: Boolean) {
        val file = pending ?: return
        pending = null
        if (success && file.length() > 0) viewModelScope.launch { repository.add(projectId, file) } else file.delete()
    }

    fun delete(photo: ProgressPhoto) {
        viewModelScope.launch { repository.delete(photo) }
    }
}
