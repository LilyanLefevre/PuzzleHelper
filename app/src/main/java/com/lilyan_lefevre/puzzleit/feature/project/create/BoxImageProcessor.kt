package com.lilyan_lefevre.puzzleit.feature.project.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import com.lilyan_lefevre.puzzleit.core.image.ImageUtils
import com.lilyan_lefevre.puzzleit.core.image.PhotoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Image work behind project creation: find the box in the photo, rectify it, read sizes, clean temp files. */
@Singleton
class BoxImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageUtils: ImageUtils,
) {

    /** Largest 4-sided contour of the photo, as normalised corners TL, TR, BR, BL; null if none. */
    suspend fun detectBounds(imagePath: String): List<PointF>? = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return@withContext null
        val corrected = imageUtils.rotateBitmap(bitmap, imagePath)

        val mat = Mat(); val gray = Mat(); val edges = Mat()
        Utils.bitmapToMat(corrected, mat)
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var largest: MatOfPoint2f? = null; var maxArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area <= 5000) continue
            val c2f = MatOfPoint2f(*contour.toArray())
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * Imgproc.arcLength(c2f, true), true)
            if (approx.total() == 4L && area > maxArea) { largest = approx; maxArea = area }
        }
        val result = largest?.let { quad ->
            val (fw, fh) = imageUtils.readOrientedDimensions(imagePath)
            orderCorners(quad.toArray()).map { PointF((it.x * options.inSampleSize / fw).toFloat(), (it.y * options.inSampleSize / fh).toFloat()) }
        }
        mat.release(); gray.release(); edges.release()
        if (corrected != bitmap) corrected.recycle()
        bitmap.recycle()
        result
    }

    /** Rectifies the box inside [quad] and saves it as a temporary JPEG; returns its path. */
    suspend fun rectify(imagePath: String, quad: List<PointF>): String = withContext(Dispatchers.IO) {
        val original = BitmapFactory.decodeFile(imagePath) ?: error("Failed to load $imagePath")
        val corrected = imageUtils.rotateBitmap(original, imagePath)
        val warped = imageUtils.warpPerspectiveWithQuad(corrected, quad)
        val out = File(context.filesDir, "temp_warped_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { warped.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (corrected != original) corrected.recycle()
        warped.recycle(); original.recycle()
        out.absolutePath
    }

    suspend fun decode(path: String): Bitmap? = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }

    fun imageSize(path: String): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    /** Deletes temporary captures, never files already stored inside a project's folder. */
    fun discardTemporary(vararg paths: String?) {
        val inProject = Regex("/[a-f0-9\\-]{36}/")
        paths.filterNotNull().map(::File).filter { it.exists() && !inProject.containsMatchIn(it.path) }.forEach { it.delete() }
        PhotoHelper.cleanupTempFiles(context)
    }

    fun quadToJson(points: List<PointF>): String =
        JSONArray().apply { points.forEach { put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) } }.toString()

    fun defaultQuadJson(): String = quadToJson(listOf(PointF(0.1f, 0.1f), PointF(0.9f, 0.1f), PointF(0.9f, 0.9f), PointF(0.1f, 0.9f)))

    private fun orderCorners(points: Array<Point>): List<Point> {
        val sums = points.map { it.x + it.y }; val diffs = points.map { it.y - it.x }
        return listOf(
            points[sums.indexOf(sums.min())], points[diffs.indexOf(diffs.min())],
            points[sums.indexOf(sums.max())], points[diffs.indexOf(diffs.max())],
        )
    }
}
