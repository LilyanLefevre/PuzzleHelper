package com.lilyan_lefevre.puzzleit.feature.recognition

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Finds where the piece is with a small pretrained salient-object network (U2-Net-small, assets/segmenter.onnx):
 * it needs no table colour, so dark parts of the picture, textured tables and a hand in the frame are no problem.
 * [run] maps a CHW float tensor of [SIZE] x [SIZE] to the network's [SIZE] x [SIZE] saliency map.
 */
class PieceSegmenter(private val run: (FloatArray) -> FloatArray) {

    /** Saliency in 0..1 for every pixel of [img] (1 = the piece). */
    fun saliency(img: Raster): FloatArray {
        val s = SIZE
        val input = FloatArray(3 * s * s)
        for (y in 0 until s) for (x in 0 until s) {
            val sx = (x + .5f) * img.w / s - .5f; val sy = (y + .5f) * img.h / s - .5f
            for (c in 0..2) input[(c * s + y) * s + x] = (bilinear(sx, sy, img.w, img.h) { px, py -> (img.px[py * img.w + px] shr (16 - 8 * c) and 255) / 255f } - MEAN[c]) / STD[c]
        }
        val out = run(input)
        val lo = out.min(); val span = max(1e-6f, out.max() - lo)
        return FloatArray(img.w * img.h) { i ->
            val sx = (i % img.w + .5f) * s / img.w - .5f; val sy = (i / img.w + .5f) * s / img.h - .5f
            (bilinear(sx, sy, s, s) { px, py -> out[py * s + px] } - lo) / span
        }
    }

    private fun bilinear(x: Float, y: Float, w: Int, h: Int, at: (Int, Int) -> Float): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt(); val fx = x - x0; val fy = y - y0
        fun v(px: Int, py: Int) = at(min(max(px, 0), w - 1), min(max(py, 0), h - 1))
        return (v(x0, y0) * (1 - fx) + v(x0 + 1, y0) * fx) * (1 - fy) + (v(x0, y0 + 1) * (1 - fx) + v(x0 + 1, y0 + 1) * fx) * fy
    }

    companion object {
        const val SIZE = 320
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
