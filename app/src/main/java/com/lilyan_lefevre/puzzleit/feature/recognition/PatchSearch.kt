package com.lilyan_lefevre.puzzleit.feature.recognition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Looks for the piece's pixels on the (small) box image: masked normalised cross-correlation of a disc inside the piece,
 * over every position, 12 rotations and 3 scales. Unlike the 5x5 colour descriptor it sees fine structure (white
 * gutters between photos, faces, text) and needs neither the outline reading nor the exact grid.
 */
internal class PatchSearch(reference: Raster, private val grid: Grid) {

    private val box: Raster
    private val chan: Array<FloatArray>

    init {
        val wide = min(grid.cols * CELL_PX, MAX_W).toFloat()
        box = reference.fit((wide * max(reference.w, reference.h) / reference.w).roundToInt().coerceAtLeast(8))
        chan = Array(3) { c -> FloatArray(box.w * box.h) { (box.px[it] shr (16 - 8 * c) and 255).toFloat() } }
    }

    /** Best correlation (-1..1) at each box pixel, over rotations and scales. */
    class Map(val w: Int, val h: Int, val v: FloatArray, private val grid: Grid) {
        /** The [k] best positions at least 1.5 cells apart, as (col, row, value), for the replay harness. */
        fun peaks(k: Int): List<Triple<Float, Float, Float>> {
            val all = v.indices.filter { v[it] > -1f }.sortedByDescending { v[it] }
            val out = ArrayList<Triple<Float, Float, Float>>()
            for (i in all) {
                val c = (i % w) * grid.cols / w.toFloat(); val r = (i / w) * grid.rows / h.toFloat()
                if (out.none { kotlin.math.hypot(it.first - c, it.second - r) < 1.5f }) out += Triple(c, r, v[i])
                if (out.size == k) break
            }
            return out
        }

        /** Best value within 0.4 cell of the point (gx, gy), in grid units. */
        fun at(gx: Float, gy: Float): Float {
            val cx = gx * w / grid.cols; val cy = gy * h / grid.rows
            val rx = (0.4f * w / grid.cols).toInt().coerceAtLeast(1); val ry = (0.4f * h / grid.rows).toInt().coerceAtLeast(1)
            var best = -1f
            for (y in max(0, cy.toInt() - ry)..min(h - 1, cy.toInt() + ry)) for (x in max(0, cx.toInt() - rx)..min(w - 1, cx.toInt() + rx)) best = max(best, v[y * w + x])
            return best
        }
    }

    fun search(photo: Raster, mask: BooleanArray): Map? {
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in mask.indices) if (mask[i]) { n++; sx += i % photo.w; sy += i / photo.w }
        if (n < 100) return null
        val cx = sx / n; val cy = sy / n; val side = sqrt(n.toDouble())
        val cell = sqrt(box.w / grid.cols.toDouble() * box.h / grid.rows)
        val out = FloatArray(box.w * box.h) { -1f }
        for (k in SCALES) {
            val s = k * cell * PIECE_SIDE / side                       // box px per photo px
            val radius = (BODY_RADIUS * side * s).coerceAtLeast(2.0)
            for (step in 0 until ROTATIONS) {
                val t = Template.build(photo, mask, cx, cy, s, radius, 2 * PI * step / ROTATIONS) ?: continue
                correlate(t, out)
            }
        }
        return Map(box.w, box.h, out, grid)
    }

    /** Pixels of the piece's disc, resampled at box resolution and rotated; zero-mean per channel. */
    private class Template(val dx: IntArray, val dy: IntArray, val t: Array<FloatArray>, val norm: FloatArray, val rx: Int, val ry: Int) {
        companion object {
            fun build(img: Raster, mask: BooleanArray, cx: Double, cy: Double, s: Double, radius: Double, a: Double): Template? {
                val r = radius.toInt() + 1
                val co = cos(a); val si = sin(a)
                val m = max(1, (1 / s).roundToInt().coerceAtMost(8))      // samples per axis to average one box pixel
                val xs = ArrayList<Int>(); val ys = ArrayList<Int>(); val vals = Array(3) { ArrayList<Float>() }
                for (y in -r..r) for (x in -r..r) {
                    if (x * x + y * y > radius * radius) continue
                    var cnt = 0; var tot = 0; val acc = FloatArray(3)
                    for (j in 0 until m) for (i in 0 until m) {
                        val ox = x + (i + .5) / m - .5; val oy = y + (j + .5) / m - .5
                        val px = (cx + (ox * co - oy * si) / s).roundToInt(); val py = (cy + (ox * si + oy * co) / s).roundToInt()
                        tot++
                        if (px < 0 || py < 0 || px >= img.w || py >= img.h || !mask[py * img.w + px]) continue
                        val p = img.px[py * img.w + px]
                        acc[0] += (p shr 16 and 255); acc[1] += (p shr 8 and 255); acc[2] += (p and 255); cnt++
                    }
                    if (cnt * 2 < tot) continue
                    xs += x; ys += y; for (c in 0..2) vals[c] += acc[c] / cnt
                }
                val n = xs.size
                if (n < MIN_PIXELS) return null
                val t = Array(3) { c -> FloatArray(n) { vals[c][it] } }
                val norm = FloatArray(3)
                for (c in 0..2) {
                    val mean = t[c].average().toFloat(); var ss = 0f
                    for (i in 0 until n) { t[c][i] -= mean; ss += t[c][i] * t[c][i] }
                    norm[c] = sqrt(ss)
                }
                return Template(xs.toIntArray(), ys.toIntArray(), t, norm, r, r)
            }
        }
    }

    private fun correlate(t: Template, out: FloatArray) {
        val n = t.dx.size
        val off = IntArray(n) { t.dy[it] * box.w + t.dx[it] }
        val eps = n * FLAT_STD * FLAT_STD
        for (y in t.ry until box.h - t.ry step STRIDE) for (x in t.rx until box.w - t.rx step STRIDE) {
            val base = y * box.w + x
            var sum = 0f
            for (c in 0..2) {
                val ch = chan[c]; val tc = t.t[c]
                var si = 0f; var si2 = 0f; var sit = 0f
                for (i in 0 until n) { val v = ch[base + off[i]]; si += v; si2 += v * v; sit += v * tc[i] }
                val varI = max(si2 - si * si / n, 0f) + eps
                sum += sit / (max(t.norm[c], 1e-3f) * sqrt(varI))
            }
            val v = sum / 3
            if (v > out[base]) out[base] = v
        }
    }

    companion object {
        private const val CELL_PX = 10
        private const val MAX_W = 260
        private const val ROTATIONS = 12
        private const val STRIDE = 2                     // [Map.at] looks 0.4 cell around, so every other pixel is enough
        private val SCALES = floatArrayOf(0.85f, 1f, 1.18f)
        private const val PIECE_SIDE = 1.07f                // a piece's equal-area square, in cells
        private const val BODY_RADIUS = 0.45                // of that side
        private const val MIN_PIXELS = 12
        private const val FLAT_STD = 3f                     // 8-bit levels: flat patches must not look perfectly correlated
    }
}
