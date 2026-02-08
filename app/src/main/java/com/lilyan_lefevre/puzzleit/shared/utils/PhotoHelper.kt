package com.lilyan_lefevre.puzzleit.shared.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class to handle camera intent and file creation
 */
object PhotoHelper {

    /**
     * Creates a temporary image file in a dedicated 'temp' subdirectory
     */
    fun createImageFile(context: Context, prefix: String = "JPEG_"): File {
        val storageDir = File(context.filesDir, "temp")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile(
            "${prefix}${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    /**
     * Creates a camera intent with the specified output file
     */
    fun createCameraIntent(context: Context, photoFile: File): Intent? {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoURI: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        return takePictureIntent
    }

    /**
     * Cleans up all files in the temp directory
     */
    fun cleanupTempFiles(context: Context) {
        val storageDir = File(context.filesDir, "temp")
        if (storageDir.exists()) {
            storageDir.deleteRecursively()
        }
    }
}
