package com.lilyan_lefevre.puzzleit.feature.camera

import android.graphics.Bitmap
import android.util.Log
import com.lilyan_lefevre.puzzleit.feature.storage.ImageStorageManager
import com.lilyan_lefevre.puzzleit.feature.storage.ImageStorageResult
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for camera operations and image storage
 */
@Singleton
class CameraRepository @Inject constructor(
    private val imageStorageManager: ImageStorageManager
) {

    companion object {
        private const val TAG = "CameraRepository"
    }

    /**
     * Save captured image without creating project
     */
    suspend fun saveCapturedImage(bitmap: Bitmap, imagePath: String): Result<ImageStorageResult.Success> {
        return try {
            Log.d(TAG, "Saving captured image")
            
            // Save image with proper orientation
            when (val storageResult = imageStorageManager.saveProjectImage(bitmap, imagePath)) {
                is ImageStorageResult.Success -> {
                    Log.d(TAG, "Image saved successfully: ${storageResult.mainImagePath}")
                    Result.success(storageResult)
                }
                is ImageStorageResult.Error -> {
                    Log.e(TAG, "Image storage failed", storageResult.exception)
                    Result.failure(storageResult.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving image", e)
            Result.failure(e)
        }
    }
}
