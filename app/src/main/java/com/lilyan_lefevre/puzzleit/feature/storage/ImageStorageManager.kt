package com.lilyan_lefevre.puzzleit.feature.storage

import android.content.Context
import android.graphics.Bitmap
import com.lilyan_lefevre.puzzleit.shared.utils.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for image storage operations with organized directory structure
 */
@Singleton
class ImageStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageUtils: ImageUtils
) {

    enum class PhotoType(val dirName: String) {
        PUZZLE("puzzle"),
        PIECES("pieces")
    }

    enum class PhotoSubType(val dirName: String) {
        ORIGINAL("original"),
        EXTRAITES("extraites")
    }

    fun getPuzzleDir(projectId: String, type: PhotoType, subType: PhotoSubType): File {
        val puzzleDir = File(context.filesDir, "$projectId/${type.dirName}/${subType.dirName}")
        if (!puzzleDir.exists()) {
            puzzleDir.mkdirs()
        }
        return puzzleDir
    }

    private fun generateFileName(projectName: String, suffix: String): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedName = projectName.replace(Regex("[^a-zA-Z0-9]"), "_")
        return "${sanitizedName}_${suffix}_$timeStamp"
    }

    /**
     * Save the complete set of puzzle box images.
     * IMPORTANT: We physically rotate the original bitmap based on sourceImagePath EXIF
     * before saving to ensure future re-edits don't need EXIF data.
     */
    suspend fun saveProjectBundle(
        projectId: String,
        projectName: String,
        sourceImagePath: String,
        originalBitmap: Bitmap,
        warpedBitmap: Bitmap
    ): ProjectBundleResult {
        return withContext(Dispatchers.IO) {
            try {
                val fileNameBase = generateFileName(projectName, "puzzle")
                
                // Redress the original bitmap physically using EXIF from source
                val orientedOriginal = imageUtils.rotateBitmap(originalBitmap, sourceImagePath)
                
                // 1. Save Oriented Original in {projectId}/puzzle/original/
                val originalDir = getPuzzleDir(projectId, PhotoType.PUZZLE, PhotoSubType.ORIGINAL)
                val originalPath = imageUtils.saveBitmapToInternalStorage(
                    context, orientedOriginal, "${fileNameBase}_original.jpg", originalDir
                )
                
                // 2. Save Warped (High Res) in {projectId}/puzzle/extraites/
                val extractedDir = getPuzzleDir(projectId, PhotoType.PUZZLE, PhotoSubType.EXTRAITES)
                val warpedPath = imageUtils.saveBitmapToInternalStorage(
                    context, warpedBitmap, "${fileNameBase}_warped.jpg", extractedDir
                )
                
                // 3. Save Thumbnail in {projectId}/puzzle/original/
                val thumbBitmap = imageUtils.generateThumbnail(warpedBitmap)
                val thumbPath = imageUtils.saveBitmapToInternalStorage(
                    context, thumbBitmap, "${fileNameBase}_thumb.jpg", originalDir
                )
                
                if (orientedOriginal != originalBitmap) orientedOriginal.recycle()
                thumbBitmap.recycle()
                
                ProjectBundleResult.Success(originalPath, warpedPath, thumbPath)
            } catch (e: Exception) {
                ProjectBundleResult.Error(e)
            }
        }
    }

    suspend fun saveExtractedPieceImage(
        bitmap: Bitmap,
        projectId: String,
        projectName: String
    ): String {
        return withContext(Dispatchers.IO) {
            val targetDir = getPuzzleDir(projectId, PhotoType.PIECES, PhotoSubType.EXTRAITES)
            val fileName = generateFileName(projectName, "piece")
            imageUtils.saveBitmapToInternalStorage(context, bitmap, "$fileName.png", targetDir)
        }
    }

    suspend fun saveOriginalPieceImage(
        bitmap: Bitmap,
        projectId: String,
        projectName: String
    ): String {
        return withContext(Dispatchers.IO) {
            val targetDir = getPuzzleDir(projectId, PhotoType.PIECES, PhotoSubType.ORIGINAL)
            val fileName = generateFileName(projectName, "capture")
            imageUtils.saveBitmapToInternalStorage(context, bitmap, "$fileName.jpg", targetDir)
        }
    }

    fun deleteProjectImages(projectId: String): Boolean {
        val projectRoot = File(context.filesDir, projectId)
        return if (projectRoot.exists()) {
            projectRoot.deleteRecursively()
        } else true
    }
    
    sealed class ProjectBundleResult {
        data class Success(val originalPath: String, val warpedPath: String, val thumbPath: String) : ProjectBundleResult()
        data class Error(val exception: Throwable) : ProjectBundleResult()
    }
}
