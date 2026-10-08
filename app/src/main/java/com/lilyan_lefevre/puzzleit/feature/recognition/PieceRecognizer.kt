package com.lilyan_lefevre.puzzleit.feature.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android glue: files/bitmaps <-> [Raster]. The algorithm itself lives in [PieceMatcher]. */
@Singleton
class PieceRecognizer @Inject constructor(@ApplicationContext private val context: Context?) {

    class Prepared(val display: Bitmap, val matcher: PieceMatcher)

    suspend fun prepare(referencePath: String, pieces: Int, grid: Grid? = null): Prepared? = withContext(Dispatchers.Default) {
        val bmp = decode(referencePath, 2400) ?: return@withContext null
        Prepared(bmp, PieceMatcher(bmp.toRaster(), pieces, grid))
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
