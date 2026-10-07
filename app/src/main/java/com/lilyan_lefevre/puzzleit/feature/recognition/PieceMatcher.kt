package com.lilyan_lefevre.puzzleit.feature.recognition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** ARGB pixels, row-major. Pure Kotlin so the whole algorithm runs on the JVM. */
class Raster(val w: Int, val h: Int, val px: IntArray) {
    init { require(px.size == w * h) }

    /** Box-average downscale so that the longest side is at most [maxSide]. */
    fun fit(maxSide: Int): Raster {
        val f = max(w, h).toFloat() / maxSide
        if (f <= 1f) return this
        val nw = (w / f).toInt().coerceAtLeast(1)
        val nh = (h / f).toInt().coerceAtLeast(1)
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            val y0 = (y * f).toInt(); val y1 = max(y0 + 1, min(h, ((y + 1) * f).toInt()))
            for (x in 0 until nw) {
                val x0 = (x * f).toInt(); val x1 = max(x0 + 1, min(w, ((x + 1) * f).toInt()))
                var r = 0; var g = 0; var b = 0
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = px[yy * w + xx]; r += p shr 16 and 255; g += p shr 8 and 255; b += p and 255
                }
                val n = (y1 - y0) * (x1 - x0)
                out[y * nw + x] = (255 shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
            }
        }
        return Raster(nw, nh, out)
    }

    /** Central crop keeping [ratio] of each side (PRD: 70 % centre of the piece photo). */
    fun centerCrop(ratio: Float): Raster {
        val cw = (w * ratio).toInt(); val ch = (h * ratio).toInt()
        val x0 = (w - cw) / 2; val y0 = (h - ch) / 2
        return Raster(cw, ch, IntArray(cw * ch) { px[(y0 + it / cw) * w + x0 + it % cw] })
    }
}

/** Lab image, 3 floats per pixel. */
internal class LabImage(val w: Int, val h: Int, val lab: FloatArray) {
    companion object {
        fun from(r: Raster): LabImage {
            val out = FloatArray(r.w * r.h * 3)
            for (i in r.px.indices) {
                val p = r.px[i]
                toLab(p shr 16 and 255, p shr 8 and 255, p and 255, out, i * 3)
            }
            return LabImage(r.w, r.h, out)
        }

        private fun lin(c: Int): Float { val v = c / 255f; return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f) }
        private fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f

        fun toLab(r8: Int, g8: Int, b8: Int, out: FloatArray, o: Int) {
            val r = lin(r8); val g = lin(g8); val b = lin(b8)
            val fx = f((0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.9505f)
            val fy = f(0.2126f * r + 0.7152f * g + 0.0722f * b)
            val fz = f((0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.089f)
            out[o] = 116f * fy - 16f; out[o + 1] = 500f * (fx - fy); out[o + 2] = 200f * (fy - fz)
        }
    }
}

data class Grid(val cols: Int, val rows: Int) {
    val count get() = cols * rows

    companion object {
        /** Virtual pre-cut: the grid whose cell count is closest to [pieces] for this aspect ratio. */
        fun forPuzzle(pieces: Int, aspect: Float): Grid {
            val cols = sqrt(pieces * aspect.toDouble()).roundToInt().coerceAtLeast(1)
            return Grid(cols, (pieces / cols.toDouble()).roundToInt().coerceAtLeast(1))
        }
    }
}

/** A place on the box image, in grid units (cell centre = col + .5 / row + .5). */
data class Candidate(
    val col: Float, val row: Float,
    val distance: Float,
    /** Clockwise degrees to turn the piece (as photographed) so it sits like on the box. */
    val rotationDeg: Int,
    /** 0..100, this lead's own confidence (the best lead's equals [Match.confidence]). */
    val confidence: Int = 0,
) {
    val cell: Pair<Int, Int> get() = col.toInt() to row.toInt()
}

enum class Precision { CELL, ZONE, UNSURE }

data class Match(
    val grid: Grid,
    val best: Candidate,
    val alternatives: List<Candidate>,
    /** 0..100 */
    val confidence: Int,
    val precision: Precision,
)

sealed interface Analysis {
    data class Found(val match: Match, val sharpness: Float, val cutout: Raster) : Analysis
    data class Blurry(val sharpness: Float) : Analysis
    data object NoPiece : Analysis
}

/**
 * Locates a photographed piece on the box image.
 *
 * Descriptor = mean Lab colour of a disc split in 1 centre + 2 rings x 8 sectors, sampled in a disc whose radius
 * is the piece's equivalent radius (scale-free). Rotation = cyclic shift of the sectors (8 steps of 45 deg, which
 * includes the 4 right-angle rotations). Brightness/white balance are partly cancelled by mean-centring.
 */
class PieceMatcher(reference: Raster, pieces: Int, gridOverride: Grid? = null) {

    val grid: Grid
    private val lab: LabImage
    private val cellW: Float
    private val cellH: Float
    private val cands: List<Pair<Candidate, Descriptor>>

    init {
        grid = gridOverride ?: Grid.forPuzzle(pieces, reference.w / reference.h.toFloat())
        // ~20 px per cell is enough for 17 colour samples; cap the total size for huge puzzles.
        val side = (grid.cols * CELL_PX).coerceAtMost(MAX_SIDE).coerceAtLeast(1)
        val ref = reference.fit(max(side, (grid.rows * CELL_PX).coerceAtMost(MAX_SIDE)))
        lab = LabImage.from(ref)
        cellW = ref.w / grid.cols.toFloat(); cellH = ref.h / grid.rows.toFloat()
        val r = sqrt(cellW * cellH / PI.toFloat())
        val out = ArrayList<Pair<Candidate, Descriptor>>()
        // Half-cell stride: the real piece is never aligned with our virtual cut.
        var gy = 0.5f
        while (gy <= grid.rows - 0.5f + 1e-3f) {
            var gx = 0.5f
            while (gx <= grid.cols - 0.5f + 1e-3f) {
                out += Candidate(gx, gy, 0f, 0) to describe(lab, gx * cellW, gy * cellH, r, null)
                gx += 0.5f
            }
            gy += 0.5f
        }
        cands = out
    }

    fun locate(photo: Raster): Analysis {
        val img = photo.fit(PHOTO_SIDE)
        val sharp = sharpness(img)
        if (sharp < MIN_SHARPNESS) return Analysis.Blurry(sharp)
        val pl = LabImage.from(img)
        val mask = segment(pl) ?: return Analysis.NoPiece
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in mask.indices) if (mask[i]) { n++; sx += i % pl.w; sy += i / pl.w }
        val pd = describe(pl, (sx / n).toFloat(), (sy / n).toFloat(), sqrt(n / PI).toFloat(), mask)

        val scored = cands.map { (c, d) ->
            var bestD = Float.MAX_VALUE; var bestK = 0
            for (k in 0 until SECTORS) { val dist = distance(pd, d, k); if (dist < bestD) { bestD = dist; bestK = k } }
            c.copy(distance = bestD, rotationDeg = (360 - bestK * 45) % 360)
        }.sortedBy { it.distance }

        // Non-maximum suppression: alternatives must be at least 1.5 cells away from earlier picks.
        val picks = ArrayList<Candidate>()
        for (c in scored) {
            if (picks.none { hypot(it.col - c.col, it.row - c.row) < 1.5f }) picks += c
            if (picks.size == 4) break
        }
        val dMed = scored[scored.size / 2].distance
        val d1 = picks[0].distance
        val d2 = picks.getOrElse(1) { picks[0] }.distance
        // How much better than a random place, and how much better than the runner-up.
        val spread = ((dMed - d1) / dMed).coerceIn(0f, 1f)
        val margin = ((d2 - d1) / d2.coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
        // Both must hold: clearly better than a random place AND clearly better than the next distinct place.
        val conf = (100f * spread * min(1f, margin * 6f)).roundToInt().coerceIn(0, 99)
        val precision = when {
            conf >= CELL_CONF -> Precision.CELL
            conf >= ZONE_CONF -> Precision.ZONE
            else -> Precision.UNSURE
        }
        // Runner-ups get the best lead's confidence scaled by how far above a random place they stand.
        val leads = picks.mapIndexed { i, c ->
            val s = ((dMed - c.distance) / dMed).coerceIn(0f, 1f)
            c.copy(confidence = if (i == 0) conf else (conf * s / spread.coerceAtLeast(1e-3f)).roundToInt().coerceIn(0, conf))
        }
        return Analysis.Found(Match(grid, leads[0], leads.drop(1), conf, precision), sharp, cutout(img, mask))
    }

    // ---------------------------------------------------------------- internals

    private class Descriptor(val v: FloatArray, val ok: BooleanArray, val mean: FloatArray) // v: CELLS x 3, mean-centred

    private fun describe(img: LabImage, cx: Float, cy: Float, r: Float, mask: BooleanArray?): Descriptor {
        val sum = FloatArray(CELLS * 3); val cnt = IntArray(CELLS)
        val R = r * RADIUS
        val x0 = max(0, (cx - R).toInt()); val x1 = min(img.w - 1, (cx + R).toInt() + 1)
        val y0 = max(0, (cy - R).toInt()); val y1 = min(img.h - 1, (cy + R).toInt() + 1)
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val d = hypot(dx, dy) / r
            if (d >= RADIUS) continue
            val i = y * img.w + x
            if (mask != null && !mask[i]) continue
            val cell = when {
                d < 0.35f -> 0
                else -> {
                    var a = atan2(dy, dx); if (a < 0) a += (2 * PI).toFloat()
                    val s = ((a / (2 * PI)) * SECTORS).toInt() % SECTORS
                    1 + (if (d < 0.65f) 0 else SECTORS) + s
                }
            }
            for (c in 0..2) sum[cell * 3 + c] += img.lab[i * 3 + c]
            cnt[cell]++
        }
        val ok = BooleanArray(CELLS) { cnt[it] >= 2 }
        val m = FloatArray(3); var nOk = 0
        for (c in 0 until CELLS) if (ok[c]) { for (k in 0..2) { sum[c * 3 + k] /= cnt[c]; m[k] += sum[c * 3 + k] }; nOk++ }
        if (nOk > 0) for (k in 0..2) m[k] /= nOk
        for (c in 0 until CELLS) if (ok[c]) for (k in 0..2) sum[c * 3 + k] -= m[k]
        return Descriptor(sum, ok, m)
    }

    /** Distance with the piece rotated by k sectors: piece[j] is compared with candidate[j - k]. */
    private fun distance(p: Descriptor, c: Descriptor, k: Int): Float {
        var acc = 0f; var w = 0f
        for (j in 0 until CELLS) {
            val jc = if (j == 0) 0 else 1 + ((j - 1) / SECTORS) * SECTORS + (((j - 1) % SECTORS - k) % SECTORS + SECTORS) % SECTORS
            if (!p.ok[j] || !c.ok[jc]) continue
            val dl = p.v[j * 3] - c.v[jc * 3]; val da = p.v[j * 3 + 1] - c.v[jc * 3 + 1]; val db = p.v[j * 3 + 2] - c.v[jc * 3 + 2]
            acc += sqrt(0.6f * dl * dl + da * da + db * db); w += 1f
        }
        if (w < MIN_CELLS) return Float.MAX_VALUE / 4
        // Mean colour counts only lightly (lighting / printing differ), plus a tax for cells that don't overlap.
        val dm = hypot(p.mean[1] - c.mean[1], p.mean[2] - c.mean[2]) + 0.3f * abs(p.mean[0] - c.mean[0])
        return acc / w + 0.25f * dm + (CELLS - w) * 0.4f
    }

    /** The piece alone: bounding box of the mask, transparent elsewhere. */
    private fun cutout(img: Raster, mask: BooleanArray): Raster {
        var x0 = img.w; var y0 = img.h; var x1 = 0; var y1 = 0
        for (i in mask.indices) if (mask[i]) { val x = i % img.w; val y = i / img.w; x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y) }
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        return Raster(w, h, IntArray(w * h) { val i = (y0 + it / w) * img.w + x0 + it % w; if (mask[i]) img.px[i] else 0 })
    }

    /** Foreground = pixels far from the border colour; keeps the largest blob with holes filled. */
    private fun segment(img: LabImage): BooleanArray? {
        val w = img.w; val h = img.h
        val b = max(2, (min(w, h) * 0.06f).toInt())
        val bg = FloatArray(3)
        for (c in 0..2) {
            val vs = ArrayList<Float>()
            for (y in 0 until h) for (x in 0 until w) if (x < b || y < b || x >= w - b || y >= h - b) vs += img.lab[(y * w + x) * 3 + c]
            vs.sort(); bg[c] = vs[vs.size / 2]
        }
        val dist = FloatArray(w * h) { i ->
            val dl = img.lab[i * 3] - bg[0]; val da = img.lab[i * 3 + 1] - bg[1]; val db = img.lab[i * 3 + 2] - bg[2]
            sqrt(dl * dl * 0.5f + da * da + db * db)
        }
        // Noise level of the background drives the threshold.
        val bd = ArrayList<Float>()
        for (y in 0 until h) for (x in 0 until w) if (x < b || y < b || x >= w - b || y >= h - b) bd += dist[y * w + x]
        bd.sort()
        val thr = max(14f, bd[(bd.size * 0.95f).toInt()] * 1.6f)
        var fg = BooleanArray(w * h) { dist[it] > thr }
        fg = largestBlob(fg, w, h) ?: return null
        fg = fillHoles(fg, w, h)
        val area = fg.count { it }
        return if (area < w * h * 0.03f || area > w * h * 0.9f) null else fg
    }

    private fun largestBlob(m: BooleanArray, w: Int, h: Int): BooleanArray? {
        val seen = BooleanArray(m.size); var best: IntArray? = null
        val stack = IntArray(m.size)
        for (s in m.indices) {
            if (!m[s] || seen[s]) continue
            var sp = 0; stack[sp++] = s; seen[s] = true
            val cur = ArrayList<Int>()
            while (sp > 0) {
                val i = stack[--sp]; cur += i
                val x = i % w; val y = i / w
                if (x > 0 && m[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && m[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && m[i - w] && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && m[i + w] && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            if (best == null || cur.size > best.size) best = cur.toIntArray()
        }
        val b = best ?: return null
        return BooleanArray(m.size).also { o -> b.forEach { o[it] = true } }
    }

    private fun fillHoles(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(m.size); val stack = IntArray(m.size); var sp = 0
        fun push(i: Int) { if (!m[i] && !outside[i]) { outside[i] = true; stack[sp++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]; val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        return BooleanArray(m.size) { !outside[it] }
    }

    companion object {
        const val SECTORS = 8
        private const val CELLS = 1 + 2 * SECTORS
        private const val RADIUS = 0.92f
        private const val CELL_PX = 20
        private const val MAX_SIDE = 1600
        private const val PHOTO_SIDE = 220
        private const val MIN_CELLS = 8f
        const val MIN_SHARPNESS = 12f
        const val CELL_CONF = 55
        const val ZONE_CONF = 30

        /** Variance of the Laplacian of the luminance: low = blurry. */
        fun sharpness(r: Raster): Float {
            val g = FloatArray(r.w * r.h) { val p = r.px[it]; 0.299f * (p shr 16 and 255) + 0.587f * (p shr 8 and 255) + 0.114f * (p and 255) }
            var s = 0.0; var s2 = 0.0; var n = 0
            for (y in 1 until r.h - 1) for (x in 1 until r.w - 1) {
                val i = y * r.w + x
                val l = (g[i - 1] + g[i + 1] + g[i - r.w] + g[i + r.w] - 4 * g[i]).toDouble()
                s += l; s2 += l * l; n++
            }
            return if (n == 0) 0f else (s2 / n - (s / n) * (s / n)).toFloat()
        }
    }
}
