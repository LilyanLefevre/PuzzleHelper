package com.lilyan_lefevre.puzzleit.shared.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.lilyan_lefevre.puzzleit.shared.constants.ImageConstants
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.math.hypot

/**
 * Utility class for image operations
 */
class ImageUtils @Inject constructor() {

    /**
     * Save bitmap to internal storage. 
     * Uses PNG if the bitmap has alpha channel, otherwise JPEG.
     * @param targetDir The subdirectory where to save the image (optional)
     */
    fun saveBitmapToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        filename: String? = null,
        targetDir: File? = null
    ): String {
        val hasAlpha = bitmap.hasAlpha()
        val extension = if (hasAlpha) ".png" else ".jpg"
        val fileName = filename ?: "${UUID.randomUUID()}$extension"
        
        val baseDir = targetDir ?: context.filesDir
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        
        val file = File(baseDir, fileName)

        try {
            FileOutputStream(file).use { out ->
                if (hasAlpha) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, ImageConstants.JPEG_QUALITY, out)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to save image to ${file.absolutePath}", e)
        }

        return file.absolutePath
    }

    /**
     * Perform perspective warp on a bitmap using a quadrilateral
     */
    fun warpPerspectiveWithQuad(bitmap: Bitmap, quad: List<PointF>): Bitmap {
        val srcWidth = bitmap.width.toDouble()
        val srcHeight = bitmap.height.toDouble()

        val srcPoints = listOf(
            Point(quad[0].x * srcWidth, quad[0].y * srcHeight),
            Point(quad[1].x * srcWidth, quad[1].y * srcHeight),
            Point(quad[2].x * srcWidth, quad[2].y * srcHeight),
            Point(quad[3].x * srcWidth, quad[3].y * srcHeight)
        )
        val srcMat = Converters.vector_Point2f_to_Mat(srcPoints)

        val widthTop = hypot(srcPoints[1].x - srcPoints[0].x, srcPoints[1].y - srcPoints[0].y)
        val widthBottom = hypot(srcPoints[2].x - srcPoints[3].x, srcPoints[2].y - srcPoints[3].y)
        val targetWidth = widthTop.coerceAtLeast(widthBottom).toInt()

        val heightLeft = hypot(srcPoints[3].x - srcPoints[0].x, srcPoints[3].y - srcPoints[0].y)
        val heightRight = hypot(srcPoints[2].x - srcPoints[1].x, srcPoints[2].y - srcPoints[1].y)
        val targetHeight = heightLeft.coerceAtLeast(heightRight).toInt()

        if (targetWidth <= 0 || targetHeight <= 0) return createBitmap(1, 1)

        val dstPoints = listOf(
            Point(0.0, 0.0),
            Point(targetWidth.toDouble(), 0.0),
            Point(targetWidth.toDouble(), targetHeight.toDouble()),
            Point(0.0, targetHeight.toDouble())
        )
        val dstMat = Converters.vector_Point2f_to_Mat(dstPoints)

        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val srcMatImage = Mat()
        Utils.bitmapToMat(bitmap, srcMatImage)

        val dstMatImage = Mat()
        Imgproc.warpPerspective(
            srcMatImage,
            dstMatImage,
            perspectiveTransform,
            Size(targetWidth.toDouble(), targetHeight.toDouble())
        )

        val resultBitmap = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dstMatImage, resultBitmap)

        srcMat.release()
        dstMat.release()
        perspectiveTransform.release()
        srcMatImage.release()
        dstMatImage.release()

        return resultBitmap
    }

    /**
     * Extracts a piece from a bitmap using thresholding and masking.
     * Returns a bitmap where the background is TRANSPARENT.
     */
    fun extractPieceWithMask(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        if (mat.channels() < 4) {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2RGBA)
        }
        
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        
        val meanVal = Core.mean(binary).`val`[0]
        if (meanVal > 127) { 
            Core.bitwise_not(binary, binary)
        }

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
        
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        var largestContour: MatOfPoint? = null
        var maxArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > maxArea) {
                maxArea = area
                largestContour = contour
            }
        }
        
        val resultMat = Mat.zeros(mat.size(), CvType.CV_8UC4)
        
        if (largestContour != null) {
            val mask = Mat.zeros(binary.size(), CvType.CV_8U)
            Imgproc.drawContours(mask, listOf(largestContour), -1, Scalar(255.0), -1)
            mat.copyTo(resultMat, mask)
            
            val rect = Imgproc.boundingRect(largestContour)
            val croppedMat = Mat(resultMat, rect)
            val resultBitmap = Bitmap.createBitmap(rect.width, rect.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(croppedMat, resultBitmap)
            
            mask.release()
            croppedMat.release()
            mat.release()
            gray.release()
            binary.release()
            resultMat.release()
            kernel.release()
            
            return resultBitmap
        } else {
            val resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)
            mat.release()
            gray.release()
            binary.release()
            resultMat.release()
            kernel.release()
            return resultBitmap
        }
    }

    /**
     * Rotate bitmap based on EXIF data
     */
    fun rotateBitmap(bitmap: Bitmap, imagePath: String): Bitmap {
        val exif = try {
            ExifInterface(imagePath)
        } catch (e: Exception) {
            return bitmap
        }

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    /**
     * Read oriented dimensions of an image
     */
    fun readOrientedDimensions(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var w = opts.outWidth
        var h = opts.outHeight
        if (w <= 0 || h <= 0) return Pair(1, 1)

        val exif = try {
            ExifInterface(path)
        } catch (e: Exception) {
            return Pair(w, h)
        }

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270
        ) {
            val tmp = w
            w = h
            h = tmp
        }
        return Pair(w, h)
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
     * Resize bitmap to fit within max dimensions while maintaining aspect ratio
     */
    fun resizeBitmap(
        bitmap: Bitmap,
        maxWidth: Int = ImageConstants.MAX_IMAGE_WIDTH,
        maxHeight: Int = ImageConstants.MAX_IMAGE_HEIGHT
    ): Bitmap {
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
}
