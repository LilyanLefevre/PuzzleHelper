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

    /** Piece cut around ref-pixel (cx,cy), rotated clockwise by [deg], shown at [zoom]x on a grey table. */
    private fun piecePhoto(ref: Raster, cx: Float, cy: Float, side: Float, deg: Double, zoom: Float, seed: Long): Raster {
        val rnd = Random(seed); val size = 340; val th = deg * PI / 180
        val px = IntArray(size * size) { rgb(205f, 195f, 185f) }
        for (v in 0 until size) for (u in 0 until size) {
            val du = (u - size / 2f) / zoom; val dv = (v - size / 2f) / zoom
            val dx = (cos(-th) * du - sin(-th) * dv).toFloat(); val dy = (sin(-th) * du + cos(-th) * dv).toFloat()
            val inBody = abs(dx) < side / 2 && abs(dy) < side / 2
            val tab = hypot((dx - side / 2f).toDouble(), dy.toDouble()) < side * 0.22   // one knob on the right
            if (!inBody && !tab) continue
            val sx = (cx + dx).toInt().coerceIn(0, ref.w - 1); val sy = (cy + dy).toInt().coerceIn(0, ref.h - 1)
            val p = ref.px[sy * ref.w + sx]; val n = rnd.nextFloat() * 8 - 4
            px[v * size + u] = rgb((p shr 16 and 255) * 0.88f + n, (p shr 8 and 255) * 0.9f + n, (p and 255) * 0.85f + n)
        }
        return blur(Raster(size, size, px))
    }

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
    fun findsPiecesBackWithRotation() {
        val ref = boxArt(7)
        val t0 = System.nanoTime()
        val matcher = PieceMatcher(ref, pieces)
        val grid = matcher.grid
        val cw = refW / grid.cols.toFloat(); val ch = refH / grid.rows.toFloat()
        val rnd = Random(42)
        var near = 0; var exact = 0; var rotOk = 0; val n = 40
        val confOk = ArrayList<Int>(); val confBad = ArrayList<Int>()
        repeat(n) { t ->
            val col = 1 + rnd.nextInt(grid.cols - 2); val row = 1 + rnd.nextInt(grid.rows - 2)
            val deg = 90.0 * rnd.nextInt(4)
            val photo = piecePhoto(ref, (col + .5f) * cw, (row + .5f) * ch, cw, deg, 6f, t.toLong())
            val a = matcher.locate(photo.centerCrop(0.9f))
            val m = (a as? Analysis.Found)?.match ?: return@repeat
            val d = hypot(m.best.col - (col + .5f), m.best.row - (row + .5f))
            assertEquals(m.confidence, m.best.confidence)
            m.alternatives.forEach { assertTrue("lead confidence ${it.confidence} > best ${m.confidence}", it.confidence <= m.confidence) }
            if (d <= 1.01f) near++
            if (d <= 0.51f) { exact++; confOk += m.confidence } else confBad += m.confidence
            if (d <= 1.01f && m.best.rotationDeg == ((360 - deg.toInt()) % 360)) rotOk++
        }
        println("PIECES: n=$n near=$near exact=$exact rotOk=$rotOk confOk=$confOk confBad=$confBad ms=${(System.nanoTime() - t0) / 1_000_000}")
        assertTrue("near $near/$n", near >= n * 0.7)
        assertTrue("rotation $rotOk/$near", rotOk >= near * 0.7)
    }

    @Test
    fun rejectsBlurAndEmptyTable() {
        val ref = boxArt(7)
        val matcher = PieceMatcher(ref, pieces)
        val cw = refW / matcher.grid.cols.toFloat()
        var photo = piecePhoto(ref, 300f, 200f, cw, 0.0, 6f, 1)
        repeat(8) { photo = blur(photo) }
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
