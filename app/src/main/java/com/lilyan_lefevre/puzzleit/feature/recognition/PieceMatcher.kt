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

    /** Centred square whose side is [ratio] of the short side: the region under the viewfinder frame. */
    fun centerSquare(ratio: Float): Raster {
        val side = (min(w, h) * ratio).toInt()
        val x0 = (w - side) / 2; val y0 = (h - side) / 2
        return Raster(side, side, IntArray(side * side) { px[(y0 + it / side) * w + x0 + it % side] })
    }

    /** Central crop keeping [ratio] of each side. */
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

/** What the piece's outline says. A flat side can only lie on the puzzle's border. */
enum class PieceKind { CORNER, EDGE, INTERIOR, UNKNOWN }

/** Side profile, in the piece's own upright frame: index 0 = top, 1 = right, 2 = bottom, 3 = left. */
enum class Side { FLAT, TAB, BLANK }

data class Shape(
    /** Clockwise angle (0..90) that the piece's straight edges make with the photo axes. */
    val tilt: Float,
    val sides: List<Side>,
) {
    val flats: Set<Int> get() = sides.indices.filter { sides[it] == Side.FLAT }.toSet()
    val kind: PieceKind get() = when (flats.size) {
        0 -> PieceKind.INTERIOR
        1 -> PieceKind.EDGE
        2 -> if ((flats.first() + 1) % 4 in flats || (flats.first() + 3) % 4 in flats) PieceKind.CORNER else PieceKind.UNKNOWN
        else -> PieceKind.UNKNOWN
    }
}

data class Match(
    val grid: Grid,
    val best: Candidate,
    val alternatives: List<Candidate>,
    /** 0..100 */
    val confidence: Int,
    val precision: Precision,
    val kind: PieceKind = PieceKind.UNKNOWN,
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
 * is the piece's equivalent radius (scale-free). The piece is first straightened from its outline, so only the
 * 4 right-angle rotations are left to test (cyclic shifts of 2 sectors); flat sides restrict corner and edge pieces
 * to the matching border cells and to the single rotation that puts the flat sides outward.
 * Brightness/white balance are partly cancelled by mean-centring.
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
        val shape = shape(mask, pl.w, pl.h)
        val match = rank(pl, mask, shape) ?: return Analysis.NoPiece
        return Analysis.Found(match, sharp, cutout(img, mask))
    }

    /**
     * Ranks the box positions for a piece whose pixels are [mask]. [shape] = its outline reading, or null to compare
     * colours only (any of the 4 right-angle rotations, no border constraint). Exposed for the dataset replays.
     */
    internal fun rank(pl: LabImage, mask: BooleanArray, shape: Shape?, leads: Int = 4): Match? {
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in mask.indices) if (mask[i]) { n++; sx += i % pl.w; sy += i / pl.w }
        if (n == 0) return null
        val tilt = shape?.tilt ?: 0f
        val kind = shape?.kind ?: PieceKind.UNKNOWN
        // Straighten the piece first: rotations left to test are then exactly 0/90/180/270,
        // and its flat sides (if any) pin it to the border with a single possible rotation.
        val pd = describe(pl, (sx / n).toFloat(), (sy / n).toFloat(), sqrt(n / PI).toFloat(), mask, tilt)
        val flats = if (kind == PieceKind.EDGE || kind == PieceKind.CORNER) shape!!.flats else null

        val scored = cands.mapNotNull { (c, d) ->
            val border = borderSides(c)
            var bestD = Float.MAX_VALUE; var bestR = -1
            for (q in 0..3) {                              // q quarter-turns clockwise to put the piece back
                if (flats != null && flats.map { (it + q) % 4 }.toSet() != border) continue
                var dist = distance(pd, d, q)
                if (flats == null && border.isNotEmpty() && kind == PieceKind.INTERIOR) dist *= 1.25f
                if (dist < bestD) { bestD = dist; bestR = q }
            }
            if (bestR < 0) null
            else c.copy(distance = bestD, rotationDeg = (((bestR * 90 - tilt) % 360 + 360) % 360).roundToInt() % 360)
        }.sortedBy { it.distance }
        if (scored.isEmpty()) return null

        // Non-maximum suppression: alternatives must be at least 1.5 cells away from earlier picks.
        val picks = ArrayList<Candidate>()
        for (c in scored) {
            if (picks.none { hypot(it.col - c.col, it.row - c.row) < 1.5f }) picks += c
            if (picks.size == leads) break
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
        return Match(grid, leads[0], leads.drop(1), conf, precision, kind)
    }

    /** What the matcher sees, step by step: for the real-photo replay harness and debugging. */
    internal class Inspection(val image: Raster, val mask: BooleanArray?, val shape: Shape?, val sharpness: Float)

    internal fun inspect(photo: Raster): Inspection {
        val img = photo.fit(PHOTO_SIDE)
        val mask = segment(LabImage.from(img))
        return Inspection(img, mask, mask?.let { shape(it, img.w, img.h) }, sharpness(img))
    }

    // ---------------------------------------------------------------- internals

    private class Descriptor(val v: FloatArray, val ok: BooleanArray, val mean: FloatArray) // v: CELLS x 3, mean-centred

    /**
     * G x G grid of mean Lab colours over the body square, in the piece's upright frame (tilt removed).
     * Side = 0.9 x the side of a square of the same area: the body without its outline, scale-free.
     */
    private fun describe(img: LabImage, cx: Float, cy: Float, r: Float, mask: BooleanArray?, tiltDeg: Float = 0f): Descriptor {
        val t = (tiltDeg * PI / 180).toFloat(); val co = kotlin.math.cos(t); val si = kotlin.math.sin(t)
        val side = r * sqrt(PI.toFloat()) * BODY
        val sum = FloatArray(CELLS * 3); val cnt = IntArray(CELLS)
        val R = side * 0.75f
        val x0 = max(0, (cx - R).toInt()); val x1 = min(img.w - 1, (cx + R).toInt() + 1)
        val y0 = max(0, (cy - R).toInt()); val y1 = min(img.h - 1, (cy + R).toInt() + 1)
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * img.w + x
            if (mask != null && !mask[i]) continue
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val u = (dx * co + dy * si) / side + 0.5f; val v = (-dx * si + dy * co) / side + 0.5f
            if (u < 0f || v < 0f || u >= 1f || v >= 1f) continue
            val cell = (v * G).toInt() * G + (u * G).toInt()
            for (c in 0..2) sum[cell * 3 + c] += img.lab[i * 3 + c]
            cnt[cell]++
        }
        val ok = BooleanArray(CELLS) { cnt[it] >= 2 }
        val m = FloatArray(3); var nOk = 0
        for (c in 0 until CELLS) if (ok[c]) { for (k in 0..2) { sum[c * 3 + k] /= cnt[c]; m[k] += sum[c * 3 + k] }; nOk++ }
        if (nOk > 0) for (k in 0..2) m[k] /= nOk
        for (c in 0 until CELLS) if (ok[c]) for (k in 0..2) sum[c * 3 + k] -= m[k]
        // Side light = a lightness ramp across the piece in the photo: fit L = bx + cy over the grid and remove it.
        // Done for the box too, so both sides are compared on the same terms.
        run {
            var sxx = 0f; var syy = 0f; var sxl = 0f; var syl = 0f
            for (c in 0 until CELLS) if (ok[c]) {
                val gx = c % G - (G - 1) / 2f; val gy = c / G - (G - 1) / 2f
                sxx += gx * gx; syy += gy * gy; sxl += gx * sum[c * 3]; syl += gy * sum[c * 3]
            }
            val bx = if (sxx > 0f) sxl / sxx else 0f; val by = if (syy > 0f) syl / syy else 0f
            for (c in 0 until CELLS) if (ok[c]) sum[c * 3] -= bx * (c % G - (G - 1) / 2f) + by * (c / G - (G - 1) / 2f)
        }
        // Contrast/saturation differ between the camera and the print: scale each channel to unit spread.
        if (nOk > 1) for (k in 0..2) {
            var vv = 0f
            for (c in 0 until CELLS) if (ok[c]) vv += sum[c * 3 + k] * sum[c * 3 + k]
            val sd = max(sqrt(vv / nOk), FLOOR[k])
            for (c in 0 until CELLS) if (ok[c]) sum[c * 3 + k] /= sd
        }
        return Descriptor(sum, ok, m)
    }

    /** Grid cell of the piece that lands on candidate cell (x, y) once the piece is turned q quarter-turns clockwise. */
    private fun turned(x: Int, y: Int, q: Int): Int {
        var px = x; var py = y
        repeat(q) { val nx = py; val ny = G - 1 - px; px = nx; py = ny }   // undo one clockwise turn
        return py * G + px
    }

    /** Distance with the piece turned q quarter-turns clockwise. */
    private fun distance(p: Descriptor, c: Descriptor, q: Int): Float {
        var acc = 0f; var w = 0f
        for (y in 0 until G) for (x in 0 until G) {
            val jc = y * G + x; val j = turned(x, y, q)
            if (!p.ok[j] || !c.ok[jc]) continue
            val dl = p.v[j * 3] - c.v[jc * 3]; val da = p.v[j * 3 + 1] - c.v[jc * 3 + 1]; val db = p.v[j * 3 + 2] - c.v[jc * 3 + 2]
            acc += sqrt(dl * dl + da * da + db * db); w += 1f
        }
        if (w < MIN_CELLS) return Float.MAX_VALUE / 4
        val dm = hypot(p.mean[1] - c.mean[1], p.mean[2] - c.mean[2]) + 0.3f * abs(p.mean[0] - c.mean[0])
        return acc / w + 0.02f * dm + (CELLS - w) * 0.05f
    }

    /** The piece alone: bounding box of the mask, transparent elsewhere. */
    private fun cutout(img: Raster, mask: BooleanArray): Raster {
        var x0 = img.w; var y0 = img.h; var x1 = 0; var y1 = 0
        for (i in mask.indices) if (mask[i]) { val x = i % img.w; val y = i / img.w; x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y) }
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        return Raster(w, h, IntArray(w * h) { val i = (y0 + it / w) * img.w + x0 + it % w; if (mask[i]) img.px[i] else 0 })
    }

    /**
     * Foreground = pixels far from the table colour (sampled on the image border).
     * Otsu picks the cut between "table" and "piece" distances, so textured or unevenly lit tables still work;
     * an opening removes speckles and thin bridges; the blob under the centre (else the largest) is the piece.
     */
    private fun segment(img: LabImage): BooleanArray? {
        val w = img.w; val h = img.h
        val b = max(2, (min(w, h) * 0.05f).toInt())
        fun onBorder(x: Int, y: Int) = x < b || y < b || x >= w - b || y >= h - b
        // Table colour as a distribution: robust centre (median) and spread (MAD) per Lab channel.
        // A quilted or textured table varies a lot in lightness but little in hue, so a dark blue piece on a dark
        // grey table is still far from it once each channel is scaled by its own spread.
        val med = FloatArray(3); val spread = FloatArray(3)
        for (c in 0..2) {
            val vs = ArrayList<Float>()
            for (y in 0 until h) for (x in 0 until w) if (onBorder(x, y)) vs += img.lab[(y * w + x) * 3 + c]
            vs.sort(); med[c] = vs[vs.size / 2]
            val dev = vs.map { abs(it - med[c]) }.sorted()
            spread[c] = max(if (c == 0) 3f else 1.5f, 1.4826f * dev[dev.size / 2])
        }
        val z = FloatArray(w * h) { i ->
            var acc = 0f
            for (c in 0..2) { val d = (img.lab[i * 3 + c] - med[c]) / spread[c]; acc += d * d }
            sqrt(acc)
        }
        val bz = ArrayList<Float>()
        for (y in 0 until h) for (x in 0 until w) if (onBorder(x, y)) bz += z[y * w + x]
        bz.sort()
        // Anything clearly outside what the table border shows.
        val thr = max(Z_MIN, bz[(bz.size * 0.99f).toInt()] * 1.1f)
        var fg = BooleanArray(w * h) { z[it] > thr }
        fg = dilate(erode(fg, w, h), w, h)                                   // opening: speckles out
        repeat(2) { fg = dilate(fg, w, h) }; repeat(2) { fg = erode(fg, w, h) }   // closing: glue the parts
        fg = blobAtCentre(fg, w, h) ?: return null
        fg = fillHoles(fg, w, h)
        val area = fg.count { it }
        return if (area < w * h * 0.01f || area > w * h * 0.85f) null else fg
    }

    private fun otsu(v: FloatArray): Float {
        val top = v.maxOrNull()?.takeIf { it > 0f } ?: return 0f
        val bins = 128; val hist = IntArray(bins)
        for (x in v) hist[min(bins - 1, (x / top * bins).toInt())]++
        val total = v.size; var sumAll = 0.0
        for (i in 0 until bins) sumAll += i * hist[i].toDouble()
        var wB = 0; var sumB = 0.0; var best = 0.0; var cut = 0
        for (i in 0 until bins) {
            wB += hist[i]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; cut = i }
        }
        return (cut + 1) * top / bins
    }

    private fun erode(m: BooleanArray, w: Int, h: Int) = BooleanArray(m.size) { i ->
        val x = i % w; val y = i / w
        m[i] && x > 0 && y > 0 && x < w - 1 && y < h - 1 && m[i - 1] && m[i + 1] && m[i - w] && m[i + w]
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int) = BooleanArray(m.size) { i ->
        val x = i % w; val y = i / w
        m[i] || (x > 0 && m[i - 1]) || (x < w - 1 && m[i + 1]) || (y > 0 && m[i - w]) || (y < h - 1 && m[i + w])
    }

    /** Which sides of the puzzle a candidate touches (0 top, 1 right, 2 bottom, 3 left). */
    private fun borderSides(c: Candidate): Set<Int> = buildSet {
        if (c.row < 0.75f) add(0)
        if (c.col > grid.cols - 0.75f) add(1)
        if (c.row > grid.rows - 0.75f) add(2)
        if (c.col < 0.75f) add(3)
    }

    /**
     * Outline reading. The tilt is the angle where the mask's row/column projections show the sharpest steps
     * (the straight parts of the four sides line up). In that upright frame, each side is a TAB if the mask sticks
     * out past the body's edge in the middle of the side, a BLANK if it is hollow there, FLAT otherwise.
     */
    internal fun shape(mask: BooleanArray, w: Int, h: Int): Shape {
        val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
        for (i in mask.indices) if (mask[i]) { xs += i % w; ys += i / w }
        val cx = xs.average().toFloat(); val cy = ys.average().toFloat()
        fun score(deg: Float): Float {
            val t = deg * PI.toFloat() / 180; val c = kotlin.math.cos(t); val s = kotlin.math.sin(t)
            val n = 2 * (w + h); val rows = IntArray(n); val cols = IntArray(n)
            for (k in xs.indices) {
                val dx = xs[k] - cx; val dy = ys[k] - cy
                val u = (dx * c + dy * s + n / 2).toInt(); val v = (-dx * s + dy * c + n / 2).toInt()
                if (u in 0 until n) cols[u]++; if (v in 0 until n) rows[v]++
            }
            fun steps(hst: IntArray): Float {
                val j = IntArray(n - 1) { abs(hst[it + 1] - hst[it]) }.sortedDescending()
                return (j[0] + j[1]).toFloat()
            }
            return steps(rows) + steps(cols)
        }
        var best = 0f; var bestS = -1f
        var d = 0f
        while (d < 90f) { val sc = score(d); if (sc > bestS) { bestS = sc; best = d }; d += 2f }
        var f = best - 1.5f
        while (f <= best + 1.5f) { val sc = score(f); if (sc > bestS) { bestS = sc; best = f }; f += 0.5f }
        val tilt = ((best % 90f) + 90f) % 90f

        // Upright grid: sample the mask rotated back by the tilt.
        val side = (2 * hypot(w.toFloat(), h.toFloat())).toInt() / 2 + 2
        val t = tilt * PI.toFloat() / 180; val c = kotlin.math.cos(t); val s = kotlin.math.sin(t)
        var g = BooleanArray(side * side) { i ->
            val u = i % side - side / 2f; val v = i / side - side / 2f
            val x = (cx + u * c - v * s).roundToInt(); val y = (cy + u * s + v * c).roundToInt()
            x in 0 until w && y in 0 until h && mask[y * w + x]
        }
        val sides = ArrayList<Side>()
        for (k in 0..3) { sides += topSide(g, side); g = rotCcw(g, side) }
        return Shape(tilt, sides)
    }

    private fun rotCcw(g: BooleanArray, n: Int) = BooleanArray(n * n) { i -> val x = i % n; val y = i / n; g[x * n + (n - 1 - y)] }

    private fun topSide(g: BooleanArray, n: Int): Side {
        val rows = IntArray(n); val cols = IntArray(n)
        for (i in g.indices) if (g[i]) { rows[i / n]++; cols[i % n]++ }
        val rMax = rows.maxOrNull() ?: 0; val cMax = cols.maxOrNull() ?: 0
        if (rMax == 0) return Side.FLAT
        // Body = rows/columns that are mostly piece (tabs are narrow, blanks leave wide shoulders).
        val top = rows.indexOfFirst { it >= 0.4f * rMax }; val bottom = rows.indexOfLast { it >= 0.4f * rMax }
        val left = cols.indexOfFirst { it >= 0.4f * cMax }; val right = cols.indexOfLast { it >= 0.4f * cMax }
        val bw = right - left; val bh = bottom - top
        if (bw < 6 || bh < 6) return Side.FLAT
        fun fill(y0: Int, y1: Int): Float {
            var on = 0; var all = 0
            for (y in max(0, y0)..min(n - 1, y1)) for (x in left + (0.38f * bw).toInt()..left + (0.62f * bw).toInt()) { all++; if (g[y * n + x]) on++ }
            return if (all == 0) 0f else on / all.toFloat()
        }
        val out = fill(top - (0.22f * bh).toInt(), top - (0.07f * bh).toInt())
        val inside = fill(top + (0.05f * bh).toInt(), top + (0.17f * bh).toInt())
        return when {
            out > 0.3f -> Side.TAB
            inside < 0.5f -> Side.BLANK
            else -> Side.FLAT
        }
    }

    private fun blobAtCentre(m: BooleanArray, w: Int, h: Int): BooleanArray? {
        val centre = (h / 2) * w + w / 2
        val blobs = blobs(m, w, h)
        val pick = blobs.firstOrNull { centre in it.toSet() } ?: blobs.maxByOrNull { it.size } ?: return null
        return BooleanArray(m.size).also { o -> pick.forEach { o[it] = true } }
    }

    private fun blobs(m: BooleanArray, w: Int, h: Int): List<IntArray> {
        val seen = BooleanArray(m.size); val out = ArrayList<IntArray>()
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
            out += cur.toIntArray()
        }
        return out
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
        private const val G = 5
        private const val CELLS = G * G
        private const val BODY = 0.9f
        private const val CELL_PX = 20
        private const val MAX_SIDE = 1600
        private const val PHOTO_SIDE = 420
        private const val MIN_CELLS = 8f
        const val MIN_SHARPNESS = 12f
        private const val Z_MIN = 4f
        private val FLOOR = floatArrayOf(4f, 3f, 3f)
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
