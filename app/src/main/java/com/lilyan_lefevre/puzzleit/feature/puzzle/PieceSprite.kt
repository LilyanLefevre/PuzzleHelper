package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.graphics.Bitmap
import kotlin.math.sqrt

/** The cut-out piece (transparent outside) with its centre and its equivalent side (its area is one cell), measured once on the alpha channel. */
class PieceSprite(val bitmap: Bitmap) {
    val cx: Float
    val cy: Float
    val side: Float

    init {
        val px = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in px.indices) if ((px[i] ushr 24) > 127) { n++; sx += i % bitmap.width; sy += i / bitmap.width }
        cx = if (n > 0) (sx / n).toFloat() else bitmap.width / 2f
        cy = if (n > 0) (sy / n).toFloat() else bitmap.height / 2f
        side = sqrt(n.coerceAtLeast(1).toDouble()).toFloat()
    }
}
