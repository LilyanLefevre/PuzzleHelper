package com.lilyan_lefevre.puzzleit.feature.recognition

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
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
    /** Quarter turns (clockwise, after the piece's tilt) that the network chose for this place. */
    val quarter: Int = 0,
    /** Fused score (network similarity and colour, z-scored): what the leads are ordered by. */
    val score: Float = 0f,
    /** The network's and the colour matcher's z-scores for this place (higher similarity / lower colour distance = better). */
    val simZ: Float = 0f,
    val colourZ: Float = 0f,
    /** Rank of this place among [pool] candidates by network similarity and by colour (1 = best); 0 when the network did not search the box. */
    val simRank: Int = 0,
    val colourRank: Int = 0,
    val pool: Int = 0,
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

/** What the network says about a piece: its similarity with every candidate square for each quarter turn, and its own embeddings. */
internal class Evidence(val sims: Array<FloatArray>, val piece: List<FloatArray>, val cut: Raster? = null)

/**
 * Locates a photographed piece on the box image.
 *
 * Descriptor = mean Lab colour of a disc split in 1 centre + 2 rings x 8 sectors, sampled in a disc whose radius
 * is the piece's equivalent radius (scale-free). The piece is first straightened from its outline, so only the
 * 4 right-angle rotations are left to test (cyclic shifts of 2 sectors); flat sides restrict corner and edge pieces
 * to the matching border cells and to the single rotation that puts the flat sides outward.
 * Brightness/white balance are partly cancelled by mean-centring.
 */
class PieceMatcher(
    private val reference: Raster,
    pieces: Int,
    gridOverride: Grid? = null,
    private val reranker: PieceReranker? = null,
    useNcc: Boolean = true,
    private val segmenter: PieceSegmenter? = null,
    /** true: the box index is built on the first scan (tests); false: the app builds it in the background with [buildIndex]. */
    private val indexOnDemand: Boolean = true,
) {

    val grid: Grid
    private val lab: LabImage
    private val cellW: Float
    private val cellH: Float
    private val cands: List<Pair<Candidate, Descriptor>>
    private val patches: PatchSearch?

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
        patches = if (useNcc) PatchSearch(reference, grid) else null
    }

    /** [leads] = how many places to return (best first); the labelling tool asks for 8. */
    fun locate(photo: Raster, leads: Int = 4): Analysis {
        val img = stage("prepare") { photo.fit(PHOTO_SIDE) }
        val sharp = sharpness(img)
        if (sharp < MIN_SHARPNESS) return Analysis.Blurry(sharp)
        val pl = LabImage.from(img)
        val mask = stage("segment") { findPiece(img, pl) } ?: return Analysis.NoPiece
        val shape = stage("outline") { shape(mask, pl.w, pl.h) }
        val ncc = stage("pixels") { patches?.search(img, mask) }
        val cut = cutout(img, mask)
        val evidence = stage("network") { if (GLOBAL_EMBED) embedSims(cut, shape.tilt) else null }
        val match = stage("rank") { rank(pl, mask, shape, if (reranker != null && evidence == null) max(RERANK_LEADS, leads) else leads, ncc, evidence) } ?: return Analysis.NoPiece
        // With the network's evidence on every candidate the leads are already ordered by it; otherwise it re-ranks the colour leads.
        return Analysis.Found(if (evidence != null) match else stage("rerank") { rerank(match, cut, masked = true, keep = leads) }, sharp, cut)
    }

    /** Called with the name and duration (ms) of each stage of a scan: the device tests and the log read it. */
    var trace: ((String, Long) -> Unit)? = null

    private inline fun <T> stage(name: String, block: () -> T): T {
        val t0 = System.nanoTime()
        val r = block()
        trace?.invoke(name, (System.nanoTime() - t0) / 1_000_000)
        return r
    }

    /** True once [buildIndex] has finished (or there is no network): scans then search the whole box with it. */
    val indexReady: Boolean get() = reranker == null || index != null

    /**
     * Ranks the box positions for a piece whose pixels are [mask]. [shape] = its outline reading, or null to compare
     * colours only (any of the 4 right-angle rotations, no border constraint). Exposed for the dataset replays.
     */
    internal fun rank(pl: LabImage, mask: BooleanArray, shape: Shape?, leads: Int = 4, ncc: PatchSearch.Map? = null, evidence: Evidence? = null): Match? {
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in mask.indices) if (mask[i]) { n++; sx += i % pl.w; sy += i / pl.w }
        if (n == 0) return null
        val tilt = shape?.tilt ?: 0f
        val kind = shape?.kind ?: PieceKind.UNKNOWN
        // Straighten the piece first: rotations left to test are then exactly 0/90/180/270,
        // and its flat sides (if any) pin it to the border with a single possible rotation.
        val pd = describe(pl, (sx / n).toFloat(), (sy / n).toFloat(), sqrt(n / PI).toFloat(), mask, tilt)
        val flats = if (kind == PieceKind.EDGE || kind == PieceKind.CORNER) shape!!.flats else null

        val allowed = cands.map { BooleanArray(4) { true } }                // rotations left after the border constraint
        val index = ArrayList<Int>()                                          // cands index of each scored candidate
        val scored = cands.withIndex().mapNotNull { (ci, cd) ->
            val (c, d) = cd
            val border = borderSides(c)
            var bestD = Float.MAX_VALUE; var bestR = -1
            for (q in 0..3) {                              // q quarter-turns clockwise to put the piece back
                var dist = distance(pd, d, q)
                if (flats != null && flats.map { (it + q) % 4 }.toSet() != border) {
                    if (evidence == null || BORDER_PENALTY.isInfinite()) { allowed[ci][q] = false; continue }   // colours alone are too weak to drop the wall
                    dist *= BORDER_PENALTY                  // the outline reading is wrong often enough on real pieces: a cost, not a wall
                }
                if (flats == null && border.isNotEmpty() && kind == PieceKind.INTERIOR) dist *= 1.25f
                if (dist < bestD) { bestD = dist; bestR = q }
            }
            if (bestR < 0) null
            else { index += ci; c.copy(distance = bestD, rotationDeg = (((bestR * 90 - tilt) % 360 + 360) % 360).roundToInt() % 360) }
        }.let { if (ncc == null) it else withPixelEvidence(it, ncc) }
            .let { if (evidence == null) it.sortedBy { c -> c.distance } else fuse(it, index.map { i -> evidence.sims[i] }, index.map { i -> allowed[i] }, tilt, if (kind == PieceKind.INTERIOR) INTERIOR_BORDER_COST else 0.0) }
        if (scored.isEmpty()) return null

        // Non-maximum suppression: alternatives must be at least 1.5 cells away from earlier picks.
        val picks = ArrayList<Candidate>()
        val want = if (evidence != null) max(leads, VERIFY_LEADS) else leads
        for (c in scored) {
            if (picks.none { hypot(it.col - c.col, it.row - c.row) < 1.5f }) picks += c
            if (picks.size == want) break
        }
        if (evidence != null) {
            // Confidence from the fused scores themselves, so it always follows the order of the leads.
            val ranked = picks.map { p -> p.copy(simRank = scored.count { it.simZ > p.simZ } + 1, colourRank = scored.count { it.colourZ < p.colourZ } + 1, pool = scored.size) }
            val refined = stage("refine") { refine(ranked, evidence) }
            val checked = stage("verify") { verify(refined, evidence.cut) }
            val placed = withConfidence(checked, CONF_TEMPERATURE).take(leads)
            return Match(grid, placed[0], placed.drop(1), placed[0].confidence, precisionOf(placed[0].confidence), kind)
        }
        val dMed = scored.map { it.distance }.sorted()[scored.size / 2]
        val d1 = picks[0].distance
        val d2 = picks.getOrElse(1) { picks[0] }.distance
        // How much better than a random place, and how much better than the runner-up.
        val spread = ((dMed - d1) / dMed).coerceIn(0f, 1f)
        val margin = ((d2 - d1) / d2.coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
        // Both must hold: clearly better than a random place AND clearly better than the next distinct place.
        val conf = (100f * spread * min(1f, margin * 6f)).roundToInt().coerceIn(0, 99)
        val precision = precisionOf(conf)
        // Runner-ups get the best lead's confidence scaled by how far above a random place they stand.
        val leads = picks.mapIndexed { i, c ->
            val s = ((dMed - c.distance) / dMed).coerceIn(0f, 1f)
            c.copy(confidence = if (i == 0) conf else (conf * s / spread.coerceAtLeast(1e-3f)).roundToInt().coerceIn(0, conf))
        }
        return Match(grid, leads[0], leads.drop(1), conf, precision, kind)
    }

    /**
     * Every candidate gets the network's similarity (best allowed rotation) next to its colour distance, both z-scored over
     * all candidates: the network may find places the colour matcher ranks far down. Order = best fused score first.
     */
    private fun fuse(c: List<Candidate>, sims: List<FloatArray>, allowed: List<BooleanArray>, tilt: Float, borderCost: Double): List<Candidate> {
        val qBest = IntArray(c.size) { i -> (0..3).filter { allowed[i][it] }.maxByOrNull { sims[i][it] } ?: 0 }
        val s = DoubleArray(c.size) { sims[it][qBest[it]].toDouble() }
        val d = DoubleArray(c.size) { c[it].distance.toDouble() }
        fun z(v: DoubleArray): DoubleArray { val m = v.average(); val sd = sqrt(v.sumOf { (it - m) * (it - m) } / v.size).coerceAtLeast(1e-9); return DoubleArray(v.size) { (v[it] - m) / sd } }
        val zs = z(s); val zd = z(d)
        // A piece read as having no flat side cannot be on the puzzle's border: its candidates there lose [borderCost] z-units.
        val fused = DoubleArray(c.size) { zs[it] - EMBED_COLOUR_WEIGHT * zd[it] - (if (borderCost > 0 && borderSides(c[it]).isNotEmpty()) borderCost else 0.0) }
        return c.indices.sortedByDescending { fused[it] }
            .map { c[it].copy(rotationDeg = (((qBest[it] * 90 - tilt) % 360 + 360) % 360).roundToInt() % 360, quarter = qBest[it], score = fused[it].toFloat(), simZ = zs[it].toFloat(), colourZ = zd[it].toFloat()) }
    }

    private fun precisionOf(conf: Int) = when {
        conf >= CELL_CONF -> Precision.CELL
        conf >= ZONE_CONF -> Precision.ZONE
        else -> Precision.UNSURE
    }

    /** Probability of each of the best leads under a softmax of their scores: coherent with the order, and a share of 100 between them. */
    private fun withConfidence(leads: List<Candidate>, temperature: Double): List<Candidate> {
        val top = leads.take(CONF_PICKS)
        val peak = top.maxOf { it.score }
        val w = top.map { exp((it.score - peak) / temperature) }
        val total = w.sum()
        return leads.mapIndexed { i, c -> c.copy(confidence = if (i < w.size) (100 * w[i] / total).roundToInt().coerceIn(0, 99) else 0) }
    }

    /** A piece reduced to the box's scale: premultiplied colour and coverage, and its centre. */
    private class Sprite(val w: Int, val h: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray, val a: FloatArray, val cx: Float, val cy: Float, val area: Float)

    /** The cut-out piece shrunk (area average) so that its equivalent side is [VERIFY_CELL_PX] pixels, the scale of [verifyRef]. */
    private fun sprite(cut: Raster): Sprite? {
        var n = 0
        for (p in cut.px) if (p ushr 24 != 0) n++
        if (n < 100) return null
        val f = sqrt(n.toDouble()) / VERIFY_CELL_PX
        val w = ceil(cut.w / f).toInt().coerceAtLeast(1); val h = ceil(cut.h / f).toInt().coerceAtLeast(1)
        val r = FloatArray(w * h); val g = FloatArray(w * h); val b = FloatArray(w * h); val a = FloatArray(w * h)
        var sx = 0.0; var sy = 0.0; var sa = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = (x * f).toInt(); val x1 = min(cut.w, max(x0 + 1, ((x + 1) * f).toInt())); val y0 = (y * f).toInt(); val y1 = min(cut.h, max(y0 + 1, ((y + 1) * f).toInt()))
            var cr = 0f; var cg = 0f; var cb = 0f; var ca = 0f; var cnt = 0
            for (yy in y0 until y1) for (xx in x0 until x1) {
                val p = cut.px[yy * cut.w + xx]; cnt++
                if (p ushr 24 != 0) { cr += (p shr 16 and 255); cg += (p shr 8 and 255); cb += (p and 255); ca += 1f }
            }
            if (cnt > 0) { val i = y * w + x; r[i] = cr / cnt; g[i] = cg / cnt; b[i] = cb / cnt; a[i] = ca / cnt; sx += x * a[i]; sy += y * a[i]; sa += a[i] }
        }
        return Sprite(w, h, r, g, b, a, (sx / sa).toFloat(), (sy / sa).toFloat(), sa.toFloat())
    }

    /** The box at a scale where a cell is about [VERIFY_CELL_PX] pixels, for the verification. */
    private val verifyRef: Raster by lazy { reference.fit(min(VERIFY_CELL_PX * max(grid.cols, grid.rows), 2000)) }

    /**
     * Lays the piece on the box at [lead] (turned like the lead says, a few small shifts tried) and measures how well its pixels follow the
     * box's under a free gain and offset per colour channel: the squared error of that fit over the variance of the box there. Low = the
     * picture continues across the piece. Null when the lead is mostly outside the box.
     */
    private fun residual(sp: Sprite, lead: Candidate): Double? {
        val ref = verifyRef
        val cw = ref.w / grid.cols.toFloat(); val ch = ref.h / grid.rows.toFloat()
        val th = lead.rotationDeg * PI / 180; val co = kotlin.math.cos(th).toFloat(); val si = kotlin.math.sin(th).toFloat()
        val half = ceil(hypot(sp.w.toDouble(), sp.h.toDouble()) / 2).toInt() + 1
        var best: Double? = null
        for (dy in -1..1) for (dx in -1..1) {
            val ox = (lead.col + dx * VERIFY_SHIFT) * cw; val oy = (lead.row + dy * VERIFY_SHIFT) * ch
            val xs = Array(3) { FloatArray((2 * half + 1) * (2 * half + 1)) }; val ys = Array(3) { FloatArray(xs[0].size) }
            var n = 0
            for (yy in -half..half) for (xx in -half..half) {
                val bx = (ox + xx).roundToInt(); val by = (oy + yy).roundToInt()
                if (bx < 0 || by < 0 || bx >= ref.w || by >= ref.h) continue
                val px = co * xx + si * yy + sp.cx; val py = -si * xx + co * yy + sp.cy       // inverse of a clockwise turn about the piece's centre
                val x0 = floor(px).toInt(); val y0 = floor(py).toInt(); val fx = px - x0; val fy = py - y0
                if (x0 < 0 || y0 < 0 || x0 + 1 >= sp.w || y0 + 1 >= sp.h) continue
                fun at(arr: FloatArray) = (arr[y0 * sp.w + x0] * (1 - fx) + arr[y0 * sp.w + x0 + 1] * fx) * (1 - fy) + (arr[(y0 + 1) * sp.w + x0] * (1 - fx) + arr[(y0 + 1) * sp.w + x0 + 1] * fx) * fy
                val a = at(sp.a)
                if (a < 0.8f) continue
                val q = ref.px[by * ref.w + bx]
                xs[0][n] = at(sp.r) / a; xs[1][n] = at(sp.g) / a; xs[2][n] = at(sp.b) / a
                ys[0][n] = (q shr 16 and 255).toFloat(); ys[1][n] = (q shr 8 and 255).toFloat(); ys[2][n] = (q and 255).toFloat()
                n++
            }
            if (n < 0.5f * sp.area) continue
            var total = 0.0
            for (c in 0..2) {
                var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0; var syy = 0.0
                for (i in 0 until n) { val x = xs[c][i].toDouble(); val y = ys[c][i].toDouble(); sx += x; sy += y; sxx += x * x; sxy += x * y; syy += y * y }
                val vx = sxx / n - (sx / n) * (sx / n); val vy = syy / n - (sy / n) * (sy / n)
                val gain = if (vx > 1e-6) ((sxy / n - sx / n * sy / n) / vx).coerceIn(0.5, 2.0) else 1.0
                val off = (sy - gain * sx) / n
                val sse = syy - 2 * gain * sxy - 2 * off * sy + gain * gain * sxx + 2 * gain * off * sx + n * off * off
                total += sse / n / (vy + 25.0)
            }
            val r = total / 3
            if (best == null || r < best) best = r
        }
        return best
    }

    /**
     * What a person does with the overlay: lay the piece on the box at each lead and see whether the picture continues. The best
     * [VERIFY_LEADS] leads are re-scored with how well they pass that check (z-scored among themselves); the rest keep their place after them.
     */
    private fun verify(leads: List<Candidate>, cut: Raster?): List<Candidate> {
        if (RESID_WEIGHT <= 0.0) return leads
        val sp = cut?.let(::sprite) ?: return leads
        val head = leads.take(VERIFY_LEADS)
        val res = head.map { residual(sp, it) }
        val ok = res.filterNotNull()
        if (ok.size < 3) return leads
        val mean = ok.average()
        val sd = sqrt(ok.sumOf { (it - mean) * (it - mean) } / ok.size).coerceAtLeast(MIN_RESID_SD)
        // A lead that cannot be checked (mostly off the box) counts as one spread worse than the average.
        val adjusted = head.mapIndexed { i, c -> c.copy(score = (c.score - RESID_WEIGHT * ((res[i] ?: (mean + sd)) - mean) / sd).toFloat()) }
        return adjusted.sortedByDescending { it.score } + leads.drop(VERIFY_LEADS)
    }

    /**
     * The coarse candidates sit on half-cell steps, so the true place is up to a quarter cell away from the lattice point. For the
     * best leads the network looks at squares around the point and the place becomes the similarity-weighted centre of them.
     */
    private fun refine(picks: List<Candidate>, evidence: Evidence): List<Candidate> {
        val r = reranker ?: return picks
        return picks.mapIndexed { i, c ->
            if (i >= REFINE_LEADS) return@mapIndexed c
            val spots = ArrayList<Pair<Float, Float>>()
            for (dy in -REFINE_RADIUS..REFINE_RADIUS) for (dx in -REFINE_RADIUS..REFINE_RADIUS) spots += (c.col + dx * REFINE_STEP) to (c.row + dy * REFINE_STEP)
            val e = r.embedBox(reference, grid, spots)
            val p = evidence.piece[c.quarter]
            val sim = FloatArray(e.size) { k -> var acc = 0f; for (j in p.indices) acc += p[j] * e[k][j]; acc }
            val peak = sim.max()
            val w = FloatArray(sim.size) { exp((sim[it] - peak) / REFINE_SOFTNESS) }
            val total = w.sum()
            c.copy(col = spots.indices.sumOf { (spots[it].first * w[it]).toDouble() }.toFloat() / total, row = spots.indices.sumOf { (spots[it].second * w[it]).toDouble() }.toFloat() / total)
        }
    }

    /** The network's embedding of the box square under every candidate; null until [buildIndex] has run. */
    @Volatile private var index: List<FloatArray>? = null

    /**
     * Builds the box index, or reads it from [cache] when that file matches (it is written there otherwise). Blocking and a few
     * seconds per thousand squares on a phone: call it off the UI thread. Until it is done, scans use the colour matcher alone.
     * [stop] is polled between batches: when it says true the build is abandoned with a CancellationException.
     */
    fun buildIndex(cache: File? = null, stop: () -> Boolean = { false }) {
        val r = reranker ?: return
        if (index != null) return
        index = cache?.let(::readIndex) ?: r.embedBox(reference, grid, cands.map { it.first.col to it.first.row }, stop).also { cache?.let { f -> writeIndex(f, it) } }
    }

    private fun readIndex(f: File): List<FloatArray>? = runCatching {
        DataInputStream(f.inputStream().buffered()).use { s ->
            val n = s.readInt(); val d = s.readInt()
            if (n != cands.size || d <= 0) null else List(n) { FloatArray(d) { s.readFloat() } }
        }
    }.getOrNull()

    private fun writeIndex(f: File, v: List<FloatArray>) {
        runCatching {
            val tmp = File(f.path + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { s -> s.writeInt(v.size); s.writeInt(v.firstOrNull()?.size ?: 0); v.forEach { row -> row.forEach(s::writeFloat) } }
            tmp.renameTo(f)
        }
    }

    /** Per candidate, the similarity of the piece turned by each of the 4 quarter turns (after its tilt) with the box square there. */
    internal fun embedSims(cut: Raster, tilt: Float, masked: Boolean = true): Evidence? {
        val r = reranker ?: return null
        if (index == null && indexOnDemand) buildIndex()
        val index = index ?: return null
        val ep = r.embedPiece(cut, (0..3).map { q -> (((q * 90 - tilt) % 360 + 360) % 360).roundToInt() % 360 }, masked)
        return Evidence(Array(index.size) { i -> FloatArray(4) { q -> var acc = 0f; for (k in ep[q].indices) acc += ep[q][k] * index[i][k]; acc } }, ep, cut)
    }

    /** Lowers the colour distance where the piece's pixels correlate with the box (both z-scored over the candidates). */
    private fun withPixelEvidence(c: List<Candidate>, ncc: PatchSearch.Map): List<Candidate> {
        if (c.size < 2) return c
        val corr = FloatArray(c.size) { ncc.at(c[it].col, c[it].row) }
        val med = c.map { it.distance }.sorted()[c.size / 2]
        val ok = c.indices.filter { c[it].distance < 4 * med }
        val dm = ok.map { c[it].distance.toDouble() }.average(); val dsd = sqrt(ok.map { (c[it].distance - dm) * (c[it].distance - dm) }.average())
        val cm = corr.average(); val csd = sqrt(corr.map { (it - cm) * (it - cm).toDouble() }.average()).coerceAtLeast(1e-6)
        return c.mapIndexed { i, x -> x.copy(distance = max(0.01f, x.distance - (NCC_WEIGHT * dsd * (corr[i] - cm) / csd).toFloat())) }
    }

    /**
     * The network picks the best 4 of the [RERANK_LEADS] leads. Its best lead keeps the confidence it had as a runner-up,
     * so a disagreement with the colour matcher lowers the precision instead of claiming a certainty nobody measured.
     */
    internal fun rerank(match: Match, piece: Raster, masked: Boolean, keep: Int = 4): Match {
        val r = reranker ?: return match
        val ordered = withConfidence(r.rerank(listOf(match.best) + match.alternatives, piece, reference, grid, masked), RERANK_TEMPERATURE).take(keep)
        val conf = ordered[0].confidence
        return match.copy(best = ordered[0], alternatives = ordered.drop(1), confidence = conf, precision = precisionOf(conf))
    }

    internal fun pixelSearch(img: Raster, mask: BooleanArray) = patches?.search(img, mask)

    /** What the matcher sees, step by step: for the real-photo replay harness and debugging. */
    internal class Inspection(val image: Raster, val mask: BooleanArray?, val shape: Shape?, val sharpness: Float)

    internal fun inspect(photo: Raster): Inspection {
        val img = photo.fit(PHOTO_SIDE)
        val mask = findPiece(img, LabImage.from(img))
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

    /** The network's mask when there is one (and it finds a piece), else the table-colour segmentation. */
    private fun findPiece(img: Raster, pl: LabImage): BooleanArray? =
        segmenter?.let { maskFrom(it.saliency(img), img.w, img.h) } ?: segment(pl)

    /** Saliency above one half, speckles opened, the blob under the centre, holes filled; same size guard as [segment]. */
    private fun maskFrom(saliency: FloatArray, w: Int, h: Int): BooleanArray? {
        var fg = BooleanArray(w * h) { saliency[it] > 0.5f }
        fg = dilate(erode(fg, w, h), w, h)
        fg = pieceBlob(fg, w, h) ?: return null
        fg = fillHoles(fg, w, h)
        val area = fg.count { it }
        return if (area < w * h * 0.01f || area > w * h * 0.85f) null else fg
    }

    /**
     * Foreground = pixels far from the table, which is modelled on the image border as a lighting plane per Lab
     * channel (a lamp makes one side of the table brighter) fitted without the intruders there (hand, jeans, a
     * crease): a few outliers must not raise the threshold above the piece's dark parts. Pixels far from the table
     * seed the piece, and it grows into the pixels that are only a little off the table (a navy zone of the picture
     * on a dark table), so dark parts stay attached. An opening removes speckles and thin bridges.
     */
    private fun segment(img: LabImage): BooleanArray? {
        val w = img.w; val h = img.h
        val b = max(2, (min(w, h) * 0.05f).toInt())
        fun onBorder(x: Int, y: Int) = x < b || y < b || x >= w - b || y >= h - b
        val border = (0 until w * h).filter { onBorder(it % w, it / w) }
        // z = distance to the table plane, each channel scaled by its own spread (a textured table varies a lot in
        // lightness but little in hue, so a dark blue piece stays far from it).
        val plane = Array(3) { floatArrayOf(0f, 0f, 0f) }
        val spread = FloatArray(3) { SPREAD_FLOOR[it] }
        fun resid(i: Int, c: Int) = img.lab[i * 3 + c] - (plane[c][0] + plane[c][1] * (i % w) / w + plane[c][2] * (i / w) / h)
        fun zAt(i: Int): Float { var acc = 0f; for (c in 0..2) { val d = resid(i, c) / spread[c]; acc += d * d }; return sqrt(acc) }
        for (c in 0..2) plane[c][0] = border.map { img.lab[it * 3 + c] }.sorted().let { it[it.size / 2] }
        var keep = border
        repeat(3) {
            for (c in 0..2) {
                val dev = keep.map { abs(resid(it, c)) }.sorted()
                spread[c] = max(SPREAD_FLOOR[c], 1.4826f * dev[dev.size / 2])
            }
            keep = border.filter { zAt(it) < OUTLIER_Z }.takeIf { it.size > border.size / 3 } ?: keep
            for (c in 0..2) fitPlane(img, keep, c, w, h, plane[c])
        }
        val z = FloatArray(w * h) { zAt(it) }
        val bz = keep.map { z[it] }.sorted()
        val seedThr = max(Z_MIN, bz[(bz.size * 0.99f).toInt()] * 1.1f)
        val growThr = max(Z_GROW, bz[(bz.size * 0.95f).toInt()] * 1.1f)
        var fg = BooleanArray(w * h) { z[it] > seedThr }
        fg = dilate(erode(fg, w, h), w, h)                                   // opening: speckles out
        // Hysteresis: the piece's own seeds grow into the weaker pixels they touch, but only inside their convex hull
        // (a piece is nearly convex), so a lit patch of table next to it cannot be swallowed.
        fg = pieceBlob(fg, w, h) ?: return null
        val room = hullMask(fg, w, h, HULL_MARGIN)
        val stack = IntArray(w * h); var sp = 0
        for (i in fg.indices) if (fg[i]) stack[sp++] = i
        while (sp > 0) {
            val i = stack[--sp]; val x = i % w; val y = i / w
            for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1))
                if (j >= 0 && !fg[j] && room[j] && z[j] > growThr) { fg[j] = true; stack[sp++] = j }
        }
        fg = dilate(erode(fg, w, h), w, h)
        repeat(2) { fg = dilate(fg, w, h) }; repeat(2) { fg = erode(fg, w, h) }   // closing: glue the parts
        fg = pieceBlob(fg, w, h) ?: return null
        fg = fillHoles(fg, w, h)
        val area = fg.count { it }
        return if (area < w * h * 0.01f || area > w * h * 0.85f) null else fg
    }

    /** Pixels inside the convex hull of [m] (monotone chain), grown by [margin] px. */
    private fun hullMask(m: BooleanArray, w: Int, h: Int, margin: Float): BooleanArray {
        val pts = m.indices.filter { m[it] }.map { (it % w) to (it / w) }.sortedWith(compareBy({ it.first }, { it.second }))
        fun cross(o: Pair<Int, Int>, a: Pair<Int, Int>, b: Pair<Int, Int>) =
            (a.first - o.first).toLong() * (b.second - o.second) - (a.second - o.second).toLong() * (b.first - o.first)
        fun half(src: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val out = ArrayList<Pair<Int, Int>>()
            for (p in src) { while (out.size >= 2 && cross(out[out.size - 2], out[out.size - 1], p) <= 0) out.removeAt(out.size - 1); out += p }
            return out
        }
        val hull = half(pts).dropLast(1) + half(pts.reversed()).dropLast(1)   // counter-clockwise in image axes
        if (hull.size < 3) return m.copyOf()
        return BooleanArray(w * h) { i ->
            val x = i % w; val y = i / w
            hull.indices.all { k ->
                val a = hull[k]; val b = hull[(k + 1) % hull.size]
                // signed distance to the edge a->b, positive inside
                cross(a, b, x to y) / hypot((b.first - a.first).toFloat(), (b.second - a.second).toFloat()).coerceAtLeast(1e-3f) >= -margin
            }
        }
    }

    /** Least-squares plane v = p0 + p1 x/w + p2 y/h over [pts] of Lab channel [c]; leaves [p] alone if the system is singular. */
    private fun fitPlane(img: LabImage, pts: List<Int>, c: Int, w: Int, h: Int, p: FloatArray) {
        val m = Array(3) { DoubleArray(4) }
        for (i in pts) {
            val f = doubleArrayOf(1.0, (i % w).toDouble() / w, (i / w).toDouble() / h); val v = img.lab[i * 3 + c].toDouble()
            for (r in 0..2) { for (k in 0..2) m[r][k] += f[r] * f[k]; m[r][3] += f[r] * v }
        }
        for (col in 0..2) {                                                  // Gauss-Jordan with partial pivoting
            val piv = (col..2).maxByOrNull { abs(m[it][col]) }!!
            if (abs(m[piv][col]) < 1e-9) return
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            for (r in 0..2) if (r != col) { val k = m[r][col] / m[col][col]; for (j in col..3) m[r][j] -= k * m[col][j] }
        }
        for (k in 0..2) p[k] = (m[k][3] / m[k][k]).toFloat()
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

    /** The piece sits in the middle of the viewfinder with table around it: the blob under the centre, else the biggest one not cut by the image border, else the biggest. */
    private fun pieceBlob(m: BooleanArray, w: Int, h: Int): BooleanArray? {
        val centre = (h / 2) * w + w / 2
        val blobs = blobs(m, w, h)
        fun touchesBorder(b: IntArray) = b.any { val x = it % w; val y = it / w; x == 0 || y == 0 || x == w - 1 || y == h - 1 }
        val pick = blobs.firstOrNull { b -> b.any { it == centre } }
            ?: blobs.filter { !touchesBorder(it) }.maxByOrNull { it.size } ?: blobs.maxByOrNull { it.size } ?: return null
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
        private const val Z_GROW = 3.6f
        private const val OUTLIER_Z = 3.5f
        private const val HULL_MARGIN = 1f
        private val SPREAD_FLOOR = floatArrayOf(3f, 1.5f, 1.5f)
        private val FLOOR = floatArrayOf(4f, 3f, 3f)
        const val RERANK_LEADS = 30
        /** Softmax temperatures of the fused score (z units): the whole-box search, and the re-ranking of the colour leads. Calibrated on the benchmarks. */
        var CONF_TEMPERATURE = 0.5
        var RERANK_TEMPERATURE = 0.7
        private const val CONF_PICKS = 8
        var REFINE_LEADS = 4
        /** 3 x 3 squares per lead, 0.15 cell apart: 0.10 cell mean error on real hits against 0.08 for 5 x 5 at 0.125, for a third of the work. */
        var REFINE_STEP = 0.15f
        var REFINE_RADIUS = 1
        private const val REFINE_SOFTNESS = 0.03f
        var NCC_WEIGHT = 0.4f
        /** With the network searching the whole box: cost factor of a rotation whose flat sides do not match the border (infinity = a wall, which a wrong outline reading turns into a wrong answer). */
        var BORDER_PENALTY = 1.4f
        /** z-units taken off the places on the puzzle's border when the outline reads as interior (no flat side). */
        var INTERIOR_BORDER_COST = 2.0
        /** Weight (in z units) of the pixel check of the best leads; 0 turns it off. 0.7: real photos 35 -> 50 % right, hand-held Puzzle-Map 84 -> 81 %. */
        var RESID_WEIGHT = 0.7
        private const val VERIFY_LEADS = 8
        private const val VERIFY_CELL_PX = 40
        private const val VERIFY_SHIFT = 0.1f
        private const val MIN_RESID_SD = 0.15
        /** Search the whole box with the network (not only the colour matcher's top leads). */
        var GLOBAL_EMBED = true
        var EMBED_COLOUR_WEIGHT = 0.5
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
