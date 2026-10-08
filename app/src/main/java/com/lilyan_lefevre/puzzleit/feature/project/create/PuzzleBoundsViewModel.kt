package com.lilyan_lefevre.puzzleit.feature.project.create

import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Box framing screen: suggests the box corners, then rectifies the photo once the user confirms them. */
@HiltViewModel
class PuzzleBoundsViewModel @Inject constructor(
    private val processor: BoxImageProcessor,
) : ViewModel() {

    data class Bounds(val quadJson: String, val rectifiedPath: String)

    private val _detected = MutableStateFlow<List<PointF>?>(null)
    val detected: StateFlow<List<PointF>?> = _detected.asStateFlow()

    private val _confirmed = MutableStateFlow<Bounds?>(null)
    val confirmed: StateFlow<Bounds?> = _confirmed.asStateFlow()

    private var detectionStarted = false

    fun detect(imagePath: String) {
        if (detectionStarted) return
        detectionStarted = true
        viewModelScope.launch {
            _detected.value = runCatching { processor.detectBounds(imagePath) }
                .onFailure { Log.e(TAG, "Box detection failed", it) }.getOrNull()
        }
    }

    /** Falls back to the unrectified photo if rectification fails, so the user is never stuck. */
    fun confirm(imagePath: String, quad: List<PointF>) {
        viewModelScope.launch {
            val path = runCatching { processor.rectify(imagePath, quad) }
                .onFailure { Log.e(TAG, "Rectification failed", it) }.getOrDefault(imagePath)
            _confirmed.value = Bounds(processor.quadToJson(quad), path)
        }
    }

    private companion object { const val TAG = "PuzzleBounds" }
}
