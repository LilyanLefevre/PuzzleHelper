package com.lilyan_lefevre.puzzleit.feature.recognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Android glue: files/bitmaps <-> [Raster]. The algorithm itself lives in [PieceMatcher]. */
@Singleton
class PieceRecognizer @Inject constructor() {

    class Prepared(val display: Bitmap, val matcher: PieceMatcher)

    suspend fun prepare(referencePath: String, pieces: Int): Prepared? = withContext(Dispatchers.Default) {
        val bmp = decode(referencePath, 2400) ?: return@withContext null
        Prepared(bmp, PieceMatcher(bmp.toRaster(), pieces))
    }

    /** PRD: only the 70 % centre of the photo is analysed. */
    suspend fun locate(matcher: PieceMatcher, photoPath: String): Analysis = withContext(Dispatchers.Default) {
        val bmp = decode(photoPath, 1000) ?: return@withContext Analysis.NoPiece
        matcher.locate(bmp.toRaster().centerCrop(0.7f))
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
