package com.lilyan_lefevre.puzzleit.feature.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** Synthetic end-to-end check: fake "box art", fake piece photo (rotated, rescaled, dimmer, noisy), find it back. */
class PieceMatcherTest {

    private val refW = 600
    private val refH = 400
    private val pieces = 300

    private fun boxArt(seed: Long): Raster {
        val rnd = Random(seed)
        val blobs = List(90) {
            floatArrayOf(rnd.nextFloat() * refW, rnd.nextFloat() * refH, 15f + rnd.nextFloat() * 50f,
                rnd.nextFloat() * 255, rnd.nextFloat() * 255, rnd.nextFloat() * 255)
        }
        val px = IntArray(refW * refH)
        for (y in 0 until refH) for (x in 0 until refW) {
            var r = 0f; var g = 0f; var b = 0f; var wt = 1e-3f
            for (bl in blobs) {
                val d2 = (x - bl[0]) * (x - bl[0]) + (y - bl[1]) * (y - bl[1])
                val w = exp(-d2 / (2 * bl[2] * bl[2])); r += w * bl[3]; g += w * bl[4]; b += w * bl[5]; wt += w
            }
            val n = rnd.nextFloat() * 10 - 5
            px[y * refW + x] = rgb(r / wt + n, g / wt + n, b / wt + n)
        }
        return Raster(refW, refH, px)
    }

    private fun rgb(r: Float, g: Float, b: Float) =
        (255 shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    /** Classic jigsaw outline in body units (body = [-.5,.5]^2). sides: 0 flat, 1 tab, -1 blank; order top, right, bottom, left. */
    private fun inPiece(u: Float, v: Float, sides: IntArray): Boolean {
        val r = 0.17f; val off = 0.5f + 0.12f
        // knob centres for top/right/bottom/left
        val kx = floatArrayOf(0f, off, 0f, -off); val ky = floatArrayOf(-off, 0f, off, 0f)
        val bx = floatArrayOf(0f, 0.5f - 0.12f, 0f, -0.5f + 0.12f); val by = floatArrayOf(-0.5f + 0.12f, 0f, 0.5f - 0.12f, 0f)
        for (k in 0..3) if (sides[k] == -1 && hypot((u - bx[k]).toDouble(), (v - by[k]).toDouble()) < r) return false
        if (abs(u) <= 0.5f && abs(v) <= 0.5f) return true
        for (k in 0..3) if (sides[k] == 1 && hypot((u - kx[k]).toDouble(), (v - ky[k]).toDouble()) < r) return true
        return false
    }

    private fun sidesFor(col: Int, row: Int, cols: Int, rows: Int, rnd: Random) = intArrayOf(
        if (row == 0) 0 else if (rnd.nextBoolean()) 1 else -1,
        if (col == cols - 1) 0 else if (rnd.nextBoolean()) 1 else -1,
        if (row == rows - 1) 0 else if (rnd.nextBoolean()) 1 else -1,
        if (col == 0) 0 else if (rnd.nextBoolean()) 1 else -1,
    )

    /**
     * Photo of the piece of cell (col,row), turned clockwise by [deg], at [zoom]x, on a wooden-ish table with
     * a light gradient and grain - roughly what a phone sees.
     */
    private fun piecePhoto(ref: Raster, grid: Grid, col: Int, row: Int, deg: Double, zoom: Float, seed: Long): Raster {
        val rnd = Random(seed); val size = 380; val th = deg * PI / 180
        val cw = ref.w / grid.cols.toFloat(); val ch = ref.h / grid.rows.toFloat()
        val cx = (col + .5f) * cw; val cy = (row + .5f) * ch
        val sides = sidesFor(col, row, grid.cols, grid.rows, rnd)
        val px = IntArray(size * size)
        for (v in 0 until size) for (u in 0 until size) {
            val du = (u - size / 2f) / zoom; val dv = (v - size / 2f) / zoom
            val dx = (cos(-th) * du - sin(-th) * dv).toFloat(); val dy = (sin(-th) * du + cos(-th) * dv).toFloat()
            val n = rnd.nextFloat() * 10 - 5
            if (!inPiece(dx / cw, dy / ch, sides)) {
                val light = 0.85f + 0.3f * u / size + 0.04f * sin(v / 9.0).toFloat()     // gradient + grain lines
                px[v * size + u] = rgb(120f * light + n, 86f * light + n, 60f * light + n); continue
            }
            val sx = (cx + dx).toInt().coerceIn(0, ref.w - 1); val sy = (cy + dy).toInt().coerceIn(0, ref.h - 1)
            val p = ref.px[sy * ref.w + sx]
            px[v * size + u] = rgb((p shr 16 and 255) * 0.9f + n, (p shr 8 and 255) * 0.92f + n, (p and 255) * 0.88f + n)
        }
        return grain(blur(Raster(size, size, px)), seed)
    }

    /** Sensor noise, added after the optics blur like on a real phone. */
    private fun grain(r: Raster, seed: Long): Raster {
        val rnd = Random(seed * 31 + 7)
        return Raster(r.w, r.h, IntArray(r.px.size) { i ->
            val p = r.px[i]; val n = rnd.nextInt(17) - 8
            rgb((p shr 16 and 255) + n.toFloat(), (p shr 8 and 255) + n.toFloat(), (p and 255) + n.toFloat())
        })
    }

    private fun angleOk(got: Int, want: Int, tol: Int = 8): Boolean { val d = abs(((got - want) % 360 + 540) % 360 - 180); return d <= tol }

    private fun blur(r: Raster): Raster = Raster(r.w, r.h, IntArray(r.px.size) { i ->
        val x = i % r.w; val y = i / r.w
        var a = 0; var g = 0; var b = 0; var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = (x + dx).coerceIn(0, r.w - 1); val yy = (y + dy).coerceIn(0, r.h - 1); val p = r.px[yy * r.w + xx]
            a += p shr 16 and 255; g += p shr 8 and 255; b += p and 255; n++
        }
        (255 shl 24) or (a / n shl 16) or (g / n shl 8) or b / n
    })

    @Test
    fun findsInteriorPiecesBackWithRotation() {
        val ref = boxArt(7)
        val t0 = System.nanoTime()
        val matcher = PieceMatcher(ref, pieces)
        val grid = matcher.grid
        val rnd = Random(42)
        var near = 0; var rotOk = 0; var interior = 0; val n = 40
        repeat(n) { t ->
            val col = 1 + rnd.nextInt(grid.cols - 2); val row = 1 + rnd.nextInt(grid.rows - 2)
            val deg = if (t % 3 == 0) rnd.nextInt(360).toDouble() else 90.0 * rnd.nextInt(4)   // a third at any angle
            val a = matcher.locate(piecePhoto(ref, grid, col, row, deg, 6f, t.toLong()))
            val m = (a as? Analysis.Found)?.match ?: return@repeat
            if (m.kind == PieceKind.INTERIOR) interior++
            assertEquals(m.confidence, m.best.confidence)
            m.alternatives.forEach { assertTrue(it.confidence <= m.confidence) }
            if (hypot(m.best.col - (col + .5f), m.best.row - (row + .5f)) <= 1.01f) {
                near++
                if (angleOk(m.best.rotationDeg, ((360 - deg.toInt()) % 360))) rotOk++
            }
        }
        println("INTERIOR: n=$n near=$near rotOk=$rotOk interior=$interior ms=${(System.nanoTime() - t0) / 1_000_000}")
        assertTrue("near $near/$n", near >= n * 0.7)
        assertTrue("rotation $rotOk/$near", rotOk >= near * 0.8)
        assertTrue("outline read as interior $interior/$n", interior >= n * 0.8)
    }

    @Test
    fun cornerPiecesGoToTheirCornerTheRightWayUp() {
        val ref = boxArt(11)
        val matcher = PieceMatcher(ref, pieces); val g = matcher.grid
        val corners = listOf(0 to 0, g.cols - 1 to 0, g.cols - 1 to g.rows - 1, 0 to g.rows - 1)
        var ok = 0; var kinds = 0
        corners.forEachIndexed { i, (col, row) ->
            val deg = listOf(0.0, 90.0, 200.0, 315.0)[i]
            val m = (matcher.locate(piecePhoto(ref, g, col, row, deg, 6f, 100L + i)) as Analysis.Found).match
            if (m.kind == PieceKind.CORNER) kinds++
            println("CORNER ($col,$row) deg=$deg -> (${m.best.col},${m.best.row}) rot=${m.best.rotationDeg} kind=${m.kind}")
            if (abs(m.best.col - (col + .5f)) < .1f && abs(m.best.row - (row + .5f)) < .1f && angleOk(m.best.rotationDeg, (360 - deg.toInt()) % 360)) ok++
        }
        assertEquals(4, kinds)
        assertEquals(4, ok)
    }

    @Test
    fun edgePiecesStayOnTheBorder() {
        val ref = boxArt(13)
        val matcher = PieceMatcher(ref, pieces); val g = matcher.grid
        val rnd = Random(5)
        var onBorder = 0; var near = 0; var kinds = 0; val n = 12
        repeat(n) { t ->
            val (col, row) = when (t % 4) {
                0 -> 2 + rnd.nextInt(g.cols - 4) to 0
                1 -> g.cols - 1 to 2 + rnd.nextInt(g.rows - 4)
                2 -> 2 + rnd.nextInt(g.cols - 4) to g.rows - 1
                else -> 0 to 2 + rnd.nextInt(g.rows - 4)
            }
            val deg = rnd.nextInt(360).toDouble()
            val m = (matcher.locate(piecePhoto(ref, g, col, row, deg, 6f, 200L + t)) as Analysis.Found).match
            if (m.kind == PieceKind.EDGE) kinds++
            val b = m.best
            if (b.row < 0.75f || b.row > g.rows - 0.75f || b.col < 0.75f || b.col > g.cols - 0.75f) onBorder++
            if (hypot(b.col - (col + .5f), b.row - (row + .5f)) <= 1.01f && angleOk(b.rotationDeg, (360 - deg.toInt()) % 360)) near++
        }
        println("EDGE: n=$n kinds=$kinds onBorder=$onBorder near+rot=$near")
        assertTrue("edge kind $kinds/$n", kinds >= n - 1)
        assertTrue("on border $onBorder/$n", onBorder >= n - 1)
        assertTrue("near with rotation $near/$n", near >= n * 0.75)
    }

    @Test
    fun rejectsBlurAndEmptyTable() {
        val ref = boxArt(7)
        val matcher = PieceMatcher(ref, pieces)
        var photo = piecePhoto(ref, matcher.grid, 8, 6, 0.0, 6f, 1)
        repeat(8) { photo = blur(photo) }
        repeat(4) { photo = blur(photo) }
        assertTrue(matcher.locate(photo) is Analysis.Blurry)

        val table = Raster(200, 200, IntArray(200 * 200) { rgb(205f, 195f, 185f) + (it % 3) })
        assertTrue(matcher.locate(table) is Analysis.NoPiece || matcher.locate(table) is Analysis.Blurry)
    }

    @Test
    fun gridMatchesPieceCount() {
        val g = Grid.forPuzzle(1000, 1.5f)
        assertEquals(1000.0, g.count.toDouble(), 40.0)
    }
}
