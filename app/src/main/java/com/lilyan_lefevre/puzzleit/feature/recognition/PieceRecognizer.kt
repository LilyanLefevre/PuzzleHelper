package com.lilyan_lefevre.puzzleit.feature.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android glue: files/bitmaps <-> [Raster]. The algorithm itself lives in [PieceMatcher]. */
@Singleton
class PieceRecognizer @Inject constructor(@ApplicationContext private val context: Context?) {

    /** [warm] completes once the box index is built and the first scan's cold costs are paid (it runs in the background). */
    class Prepared(val display: Bitmap, val matcher: PieceMatcher, val warm: Job)

    // The box index and the warm-up scan run on a thread of their own, at the lowest priority: on a phone with few cores they used to
    // take both threads of Dispatchers.Default and a scan the person asked for waited until they were done.
    private val background = Executors.newSingleThreadExecutor { r -> Thread(r, "box-index").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + background)
    private var warmJob: Job? = null

    /** Abandons the background work of the last [prepare] (the person left the table: it would only compete with what comes next). */
    fun cancelWarmUp() { warmJob?.cancel() }

    /** Loaded once; null without a context (JVM tests) or if the model cannot be read: the colour matcher then works alone. */
    private var rerankerBytes = 0
    private val reranker: PieceReranker? by lazy {
        runCatching {
            val bytes = context!!.assets.open("reranker.onnx").use { it.readBytes() }
            rerankerBytes = bytes.size
            val e = OnnxEmbedder(bytes)
            PieceReranker(e::embed)
        }.onFailure { android.util.Log.e("PieceRecognizer", "re-ranker unavailable", it) }.getOrNull()
    }

    /** Cuts the piece out of the photo; null (table-colour segmentation instead) if the model cannot be read. */
    private val segmenter: PieceSegmenter? by lazy {
        runCatching { PieceSegmenter(OnnxSegmenter(context!!.assets.open("segmenter.onnx").use { it.readBytes() })::run) }
            .onFailure { android.util.Log.e("PieceRecognizer", "segmenter unavailable", it) }.getOrNull()
    }

    suspend fun prepare(referencePath: String, pieces: Int, grid: Grid? = null): Prepared? = withContext(Dispatchers.Default) {
        val bmp = decode(referencePath, 2400) ?: return@withContext null
        val matcher = PieceMatcher(bmp.toRaster(), pieces, grid, reranker, segmenter = segmenter, indexOnDemand = false)
        matcher.trace = { stage, ms -> android.util.Log.d("PieceTrace", "$stage $ms ms") }
        // The index of the box (network embeddings of every square) is the slow part: built in the background and kept next to
        // the reference image, named after the model and grid so a change of either rebuilds it. Scans meanwhile use colours only.
        val cache = File(referencePath.substringBeforeLast('.') + "_index_${matcher.grid.cols}x${matcher.grid.rows}_$rerankerBytes.bin")
        warmJob?.cancel()
        val warm = scope.launch {
            runCatching {
                matcher.buildIndex(cache) { !isActive }
                // A first scan pays for loading the networks and warming the code (about two seconds on a phone): do it before the person scans.
                matcher.locate(warmUpPhoto())
            }.onFailure { android.util.Log.e("PieceRecognizer", "warm-up failed", it) }
        }
        warmJob = warm
        Prepared(bmp, matcher, warm)
    }

    /**
     * Only the square under the viewfinder frame is analysed (half the photo's short side): what the user sees,
     * with a ring of table around the piece for the background estimate.
     */
    suspend fun locate(matcher: PieceMatcher, photoPath: String): Analysis = withContext(Dispatchers.Default) {
        val bmp = decode(photoPath, 1600) ?: return@withContext Analysis.NoPiece
        val a = matcher.locate(bmp.toRaster().centerSquare(CROP))
        archive(photoPath, a)
        a
    }

    /**
     * Keeps the last 200 captures with their verdict on the device (never sent anywhere) so real-world failures can be
     * replayed: adb pull /sdcard/Android/data/com.lilyan_lefevre.puzzleit/files/captures
     */
    private fun archive(photoPath: String, a: Analysis) {
        val dir = context?.getExternalFilesDir("captures") ?: return
        runCatching {
            val stamp = System.currentTimeMillis()
            File(photoPath).copyTo(File(dir, "$stamp.jpg"), overwrite = true)
            val verdict = when (a) {
                is Analysis.Found -> "found col=${a.match.best.col} row=${a.match.best.row} rot=${a.match.best.rotationDeg} " +
                    "conf=${a.match.confidence} kind=${a.match.kind} sharp=${a.sharpness}"
                is Analysis.Blurry -> "blurry sharp=${a.sharpness}"
                Analysis.NoPiece -> "no-piece"
            }
            File(dir, "$stamp.txt").writeText(verdict + "\n")
            dir.listFiles()?.sortedByDescending { it.name }?.drop(2 * KEEP)?.forEach { it.delete() }
        }
    }

    companion object {
        const val CROP = 0.5f
        private const val KEEP = 200
    }

    /** A table with a light piece-sized blob in the middle: enough for every stage of a scan to run once. */
    private fun warmUpPhoto(): Raster {
        val n = 420
        return Raster(n, n, IntArray(n * n) { i ->
            val x = i % n - n / 2; val y = i / n - n / 2
            if (x * x + y * y < 70 * 70) 0xFFD8C8A8.toInt() else 0xFF3A3530.toInt()
        })
    }

    private fun decode(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || !File(path).exists()) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

fun Bitmap.toRaster(): Raster {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    return Raster(width, height, px)
}

fun Raster.toBitmap(): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
