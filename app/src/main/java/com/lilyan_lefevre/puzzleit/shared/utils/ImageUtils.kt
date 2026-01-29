package com.lilyan_lefevre.puzzleit.shared.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.lilyan_lefevre.puzzleit.shared.constants.ImageConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import androidx.core.graphics.scale

/**
 * Utility class for image operations
 */
class ImageUtils @Inject constructor()  {

    /**
     * Save bitmap to internal storage with compression
     */
    fun saveBitmapToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        filename: String? = null
    ): String {
        val fileName = filename ?: "${UUID.randomUUID()}.jpg"
        val file = File(context.filesDir, fileName)
        
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, ImageConstants.JPEG_QUALITY, out)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to save image", e)
        }
        
        return file.absolutePath
    }

    /**
     * Resize bitmap to fit within max dimensions while maintaining aspect ratio
     * For Computer Vision, we keep original resolution when possible
     */
    fun resizeBitmap(
        bitmap: Bitmap, 
        maxWidth: Int = ImageConstants.MAX_IMAGE_WIDTH, 
        maxHeight: Int = ImageConstants.MAX_IMAGE_HEIGHT
    ): Bitmap {
        // For CV, if max dimensions are unlimited, return original bitmap
        if (maxWidth == Integer.MAX_VALUE && maxHeight == Integer.MAX_VALUE) {
            return bitmap
        }
        
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    /**
     * Generate thumbnail from bitmap
     */
    fun generateThumbnail(bitmap: Bitmap): Bitmap {
        return bitmap.scale(ImageConstants.THUMBNAIL_SIZE, ImageConstants.THUMBNAIL_SIZE)
    }

    /**
     * Rotate bitmap based on EXIF data
     */
    fun rotateBitmap(bitmap: Bitmap, imagePath: String): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            else -> bitmap
        }
    }

    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Delete file from internal storage
     */
    fun deleteImageFile(context: Context, imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get file size in bytes
     */
    fun getFileSize(context: Context, imagePath: String): Long {
        return try {
            val file = File(imagePath)
            if (file.exists()) file.length() else 0L
        } catch (e: Exception) {
            0L
        }
    }
}