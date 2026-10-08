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

    private fun rgb(r: Float, g: Float, b: Float) = PiecePhotos.rgb(r, g, b)

    private fun piecePhoto(ref: Raster, grid: Grid, col: Int, row: Int, deg: Double, zoom: Float, seed: Long, light: Float = 0f) =
        PiecePhotos.photo(ref, grid, col, row, PiecePhotos.Shot(deg, zoom, seed, light))

    private fun blur(r: Raster) = PiecePhotos.blur(r)

    private fun angleOk(got: Int, want: Int, tol: Int = 8): Boolean { val d = abs(((got - want) % 360 + 540) % 360 - 180); return d <= tol }


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

    /**
     * The user's report: same edge piece, same lighting, flat side to the right then to the left of the photo.
     * The answer must not depend on how the piece was laid down.
     */
    @Test
    fun answerDoesNotDependOnHowThePieceIsLaidUnderSideLight() {
        val ref = boxArt(17)
        val matcher = PieceMatcher(ref, pieces); val g = matcher.grid
        val rnd = Random(9)
        var same = 0; var right = 0; val n = 12
        repeat(n) { t ->
            val (col, row) = when (t % 4) {
                0 -> 2 + rnd.nextInt(g.cols - 4) to 0
                1 -> g.cols - 1 to 2 + rnd.nextInt(g.rows - 4)
                2 -> 2 + rnd.nextInt(g.cols - 4) to g.rows - 1
                else -> 1 + rnd.nextInt(g.cols - 2) to 1 + rnd.nextInt(g.rows - 2)
            }
            val base = rnd.nextInt(360).toDouble()
            val a = (matcher.locate(piecePhoto(ref, g, col, row, base, 6f, 300L + t, light = 0.6f)) as Analysis.Found).match.best
            val b = (matcher.locate(piecePhoto(ref, g, col, row, base + 180, 6f, 400L + t, light = 0.6f)) as Analysis.Found).match.best
            // Two answers each within one cell of the truth can be two cells apart: that is still the same place.
            if (hypot(a.col - b.col, a.row - b.row) <= 2.01f) same++
            if (hypot(a.col - (col + .5f), a.row - (row + .5f)) <= 1.01f) right++
            if (hypot(b.col - (col + .5f), b.row - (row + .5f)) <= 1.01f) right++
        }
        println("SIDELIGHT: n=$n sameAnswer=$same correct=$right/${2 * n}")
        assertTrue("same answer both ways $same/$n", same >= n - 1)
        assertTrue("correct $right/${2 * n}", right >= 2 * n * 0.75)
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
