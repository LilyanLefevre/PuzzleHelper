package com.lilyan_lefevre.puzzleit.feature.storage

import android.content.Context
import android.graphics.Bitmap
import com.lilyan_lefevre.puzzleit.shared.utils.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for image storage operations
 */
@Singleton
class ImageStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageUtils: ImageUtils
) {

    /**
     * Save captured image with proper orientation and create thumbnail
     * For Computer Vision, we preserve maximum quality and resolution
     */
    suspend fun saveProjectImage(bitmap: Bitmap, originalImagePath: String): ImageStorageResult {
        return withContext(Dispatchers.IO) {
            try {
                // Fix orientation based on EXIF data
                val orientedBitmap = imageUtils.rotateBitmap(bitmap, originalImagePath)
                
                // For CV, keep original resolution - only resize if absolutely necessary
                val finalBitmap = imageUtils.resizeBitmap(orientedBitmap)
                
                // Generate thumbnail (still small for UI)
                val thumbnailBitmap = imageUtils.generateThumbnail(finalBitmap)
                
                // Save main image with maximum quality
                val mainImagePath = imageUtils.saveBitmapToInternalStorage(
                    context, 
                    finalBitmap, 
                    "project_${System.currentTimeMillis()}.jpg"
                )
                
                // Save thumbnail
                val thumbnailPath = imageUtils.saveBitmapToInternalStorage(
                    context, 
                    thumbnailBitmap, 
                    "thumbnail_${System.currentTimeMillis()}.jpg"
                )
                
                // Clean up bitmaps properly
                try {
                    if (orientedBitmap != bitmap && orientedBitmap.isRecycled.not()) {
                        orientedBitmap.recycle()
                    }
                    if (finalBitmap != bitmap && finalBitmap != orientedBitmap && finalBitmap.isRecycled.not()) {
                        finalBitmap.recycle()
                    }
                    if (thumbnailBitmap.isRecycled.not()) {
                        thumbnailBitmap.recycle()
                    }
                } catch (e: Exception) {
                    // Log error but don't fail the operation
                    android.util.Log.w("ImageStorageManager", "Error recycling bitmaps: ${e.message}")
                }
                
                ImageStorageResult.Success(mainImagePath, thumbnailPath)
            } catch (e: Exception) {
                ImageStorageResult.Error(e)
            }
        }
    }

    /**
     * Delete project images
     */
    fun deleteProjectImages(imagePath: String, thumbnailPath: String): Boolean {
        val imageDeleted = imageUtils.deleteImageFile(context, imagePath)
        val thumbnailDeleted = imageUtils.deleteImageFile(context, thumbnailPath)
        return imageDeleted && thumbnailDeleted
    }

    /**
     * Get total storage used by project images
     */
    fun getProjectImageSize(imagePath: String, thumbnailPath: String): Long {
        val imageSize = imageUtils.getFileSize(context, imagePath)
        val thumbnailSize = imageUtils.getFileSize(context, thumbnailPath)
        return imageSize + thumbnailSize
    }

    /**
     * Check if image files exist
     */
    fun imagesExist(imagePath: String, thumbnailPath: String): Boolean {
        val imageFile = File(imagePath)
        val thumbnailFile = File(thumbnailPath)
        return imageFile.exists() && thumbnailFile.exists()
    }
}
