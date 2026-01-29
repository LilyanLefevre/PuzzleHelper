package com.lilyan_lefevre.puzzleit.feature.storage

/**
 * Sealed class for image storage operations result
 */
sealed class ImageStorageResult {
    data class Success(val mainImagePath: String, val thumbnailPath: String) : ImageStorageResult()
    data class Error(val exception: Throwable) : ImageStorageResult()
}
