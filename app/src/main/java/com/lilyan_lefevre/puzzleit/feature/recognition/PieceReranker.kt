package com.lilyan_lefevre.puzzleit.feature.recognition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Re-orders the matcher's leads with a small contrastive network (tools/ai/train_reranker.py): the piece, turned the
 * way a lead says, and the box square under that lead must give close embeddings. Same crops and score mix as
 * tools/ai/rerank_experiment.py. [embed] maps CHW float tensors of [SIZE] x [SIZE] to embeddings.
 */
class PieceReranker(private val embed: (List<FloatArray>) -> List<FloatArray>) {

    /** [piece]: black (or transparent) outside the piece when [masked], else already the body square. */
    internal fun rerank(leads: List<Candidate>, piece: Raster, box: Raster, grid: Grid, masked: Boolean): List<Candidate> {
        val rots = leads.map { it.rotationDeg }.distinct()
        val inputs = rots.map { tensor(body(piece, it, masked)) } +
            leads.map { tensor(patch(box, grid, it.col, it.row)) }
        val e = embed(inputs).map(::unit)
        val sim = DoubleArray(leads.size) { i -> dot(e[rots.indexOf(leads[i].rotationDeg)], e[rots.size + i]) }
        val dist = DoubleArray(leads.size) { leads[it].distance.toDouble() }
        val score = z(sim).zip(z(dist)) { s, d -> s - COLOUR_WEIGHT * d }
        return leads.indices.sortedByDescending { score[it] }.map { leads[it].copy(score = score[it].toFloat()) }
    }

    /** Unit embeddings of the piece turned clockwise by each of [degs]. */
    internal fun embedPiece(piece: Raster, degs: List<Int>, masked: Boolean): List<FloatArray> =
        embed(degs.map { tensor(body(piece, it, masked)) }).map(::unit)

    /** Unit embeddings of the box square under each (col, row) of [spots], in small batches. */
    internal fun embedBox(box: Raster, grid: Grid, spots: List<Pair<Float, Float>>): List<FloatArray> =
        spots.chunked(128).flatMap { chunk -> embed(chunk.map { (c, r) -> tensor(patch(box, grid, c, r)) }).map(::unit) }

    /** The piece turned clockwise by [deg], then its body: the central square, scaled from the piece's area. */
    private fun body(piece: Raster, deg: Int, masked: Boolean): Raster {
        val p = rotate(piece, deg)
        if (!masked) return p
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in p.px.indices) if (gray(p.px[i]) > 8) { n++; sx += i % p.w; sy += i / p.w }
        if (n < 50) return p
        val side = sqrt(n.toDouble()) * BODY
        return crop(p, sx / n, sy / n, side, side)
    }

    private fun patch(box: Raster, grid: Grid, col: Float, row: Float): Raster {
        val cw = box.w / grid.cols.toDouble(); val ch = box.h / grid.rows.toDouble()
        val side = sqrt(cw * ch) * BODY
        return crop(box, col * cw, row * ch, side, side)
    }

    private fun gray(p: Int) = ((p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114) / 1000

    /** Window centred on (cx, cy); black outside the image. */
    private fun crop(r: Raster, cx: Double, cy: Double, w: Double, h: Double): Raster {
        val x0 = (cx - w / 2).toInt(); val y0 = (cy - h / 2).toInt()
        val nw = max(1, (cx + w / 2).toInt() - x0); val nh = max(1, (cy + h / 2).toInt() - y0)
        return Raster(nw, nh, IntArray(nw * nh) {
            val x = x0 + it % nw; val y = y0 + it / nw
            if (x in 0 until r.w && y in 0 until r.h) r.px[y * r.w + x] else BLACK
        })
    }

    /** Clockwise rotation, canvas grown to fit, bilinear, black fill. */
    private fun rotate(r: Raster, deg: Int): Raster {
        if (deg % 360 == 0) return r
        val a = deg * PI / 180; val c = cos(a); val s = sin(a)
        val nw = ceil(abs(r.w * c) + abs(r.h * s) - 1e-6).toInt(); val nh = ceil(abs(r.w * s) + abs(r.h * c) - 1e-6).toInt()
        val out = IntArray(nw * nh)
        for (y in 0 until nh) for (x in 0 until nw) {
            val dx = x + .5 - nw / 2.0; val dy = y + .5 - nh / 2.0
            val sx = c * dx + s * dy + r.w / 2.0 - .5; val sy = -s * dx + c * dy + r.h / 2.0 - .5
            out[y * nw + x] = bilinear(r, sx, sy)
        }
        return Raster(nw, nh, out)
    }

    private fun bilinear(r: Raster, x: Double, y: Double): Int {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt(); val fx = x - x0; val fy = y - y0
        var out = 0
        for (shift in intArrayOf(16, 8, 0)) {
            fun at(xx: Int, yy: Int) = if (xx in 0 until r.w && yy in 0 until r.h) (r.px[yy * r.w + xx] shr shift and 255).toDouble() else 0.0
            val v = (at(x0, y0) * (1 - fx) + at(x0 + 1, y0) * fx) * (1 - fy) + (at(x0, y0 + 1) * (1 - fx) + at(x0 + 1, y0 + 1) * fx) * fy
            out = out or (v.toInt().coerceIn(0, 255) shl shift)
        }
        return out or BLACK
    }

    /** SIZE x SIZE, ImageNet-normalised, CHW. Triangle-filter resize with the kernel widened when shrinking, like PIL. */
    private fun tensor(r: Raster): FloatArray {
        val wx = weights(r.w); val wy = weights(r.h)
        val tmp = FloatArray(SIZE * r.h * 3)                  // horizontal pass: SIZE x r.h
        for (y in 0 until r.h) for (x in 0 until SIZE) {
            val (from, w) = wx[x]
            for (ch in 0 until 3) {
                var acc = 0f
                for (k in w.indices) acc += w[k] * (r.px[y * r.w + from + k] shr (16 - 8 * ch) and 255)
                tmp[(y * SIZE + x) * 3 + ch] = acc
            }
        }
        val out = FloatArray(3 * SIZE * SIZE)
        for (y in 0 until SIZE) {
            val (from, w) = wy[y]
            for (x in 0 until SIZE) for (ch in 0 until 3) {
                var acc = 0f
                for (k in w.indices) acc += w[k] * tmp[((from + k) * SIZE + x) * 3 + ch]
                out[(ch * SIZE + y) * SIZE + x] = (Math.round(acc.coerceIn(0f, 255f)) / 255f - MEAN[ch]) / STD[ch]
            }
        }
        return out
    }

    private fun weights(n: Int): List<Pair<Int, FloatArray>> {
        val scale = n / SIZE.toDouble(); val support = max(scale, 1.0)
        return List(SIZE) { i ->
            val centre = (i + .5) * scale
            val from = max(0, (centre - support + .5).toInt()); val to = min(n, (centre + support + .5).toInt())
            val w = FloatArray(max(1, to - from)) { k -> max(0.0, 1 - abs((from + k - centre + .5) / support)).toFloat() }
            val sum = w.sum()
            from to FloatArray(w.size) { if (sum > 0) w[it] / sum else 1f / w.size }
        }
    }

    private fun unit(v: FloatArray): FloatArray { val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-9f); return FloatArray(v.size) { v[it] / n } }
    private fun dot(a: FloatArray, b: FloatArray): Double { var s = 0.0; for (i in a.indices) s += a[i] * b[i]; return s }
    private fun z(v: DoubleArray): DoubleArray {
        val m = v.average(); val sd = sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        return DoubleArray(v.size) { (v[it] - m) / (sd + 1e-9) }
    }

    companion object {
        const val SIZE = 96
        /** Best mix on both benchmarks: score = z(similarity) - 0.5 x z(colour distance), over the leads. */
        private const val COLOUR_WEIGHT = 0.5
        private const val BODY = 0.9
        private const val BLACK = 0xFF000000.toInt()
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
