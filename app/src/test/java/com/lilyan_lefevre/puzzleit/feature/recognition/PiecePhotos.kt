package com.lilyan_lefevre.puzzleit.feature.recognition

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Fake phone photos of jigsaw pieces cut from a box image: classic outline (tabs, blanks, flat border sides), any
 * rotation and scale, table, side light, white balance, exposure, optics blur and sensor grain.
 */
object PiecePhotos {

    /** How the photo is taken. Defaults = the historical synthetic suite (wooden table, light from the left). */
    data class Shot(
        val deg: Double,
        val zoom: Float,
        val seed: Long,
        /** Strength of the side light across the photo (0 = even). */
        val light: Float = 0f,
        /** Direction the light grows towards, in degrees (0 = towards the right of the picture). */
        val lightAngle: Double = 0.0,
        /** Per-channel colour response of the camera (white balance), times [exposure]. */
        val gains: FloatArray = floatArrayOf(0.9f, 0.92f, 0.88f),
        val exposure: Float = 1f,
        val table: IntArray = intArrayOf(120, 86, 60),
    )

    fun rgb(r: Float, g: Float, b: Float) =
        (255 shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    /** Classic jigsaw outline in body units (body = [-.5,.5]^2). sides: 0 flat, 1 tab, -1 blank; order top, right, bottom, left. */
    fun inPiece(u: Float, v: Float, sides: IntArray): Boolean {
        val r = 0.17f; val off = 0.5f + 0.12f
        val kx = floatArrayOf(0f, off, 0f, -off); val ky = floatArrayOf(-off, 0f, off, 0f)
        val bx = floatArrayOf(0f, 0.5f - 0.12f, 0f, -0.5f + 0.12f); val by = floatArrayOf(-0.5f + 0.12f, 0f, 0.5f - 0.12f, 0f)
        for (k in 0..3) if (sides[k] == -1 && hypot((u - bx[k]).toDouble(), (v - by[k]).toDouble()) < r) return false
        if (abs(u) <= 0.5f && abs(v) <= 0.5f) return true
        for (k in 0..3) if (sides[k] == 1 && hypot((u - kx[k]).toDouble(), (v - ky[k]).toDouble()) < r) return true
        return false
    }

    /** Flat on the puzzle's border, random tab or blank inside. */
    fun sidesFor(col: Int, row: Int, cols: Int, rows: Int, rnd: Random) = intArrayOf(
        if (row == 0) 0 else if (rnd.nextBoolean()) 1 else -1,
        if (col == cols - 1) 0 else if (rnd.nextBoolean()) 1 else -1,
        if (row == rows - 1) 0 else if (rnd.nextBoolean()) 1 else -1,
        if (col == 0) 0 else if (rnd.nextBoolean()) 1 else -1,
    )

    /** Photo (380 px square) of the piece of cell (col,row), turned clockwise by [Shot.deg]. */
    fun photo(ref: Raster, grid: Grid, col: Int, row: Int, shot: Shot): Raster {
        val rnd = Random(shot.seed); val size = 380; val th = shot.deg * PI / 180
        val cw = ref.w / grid.cols.toFloat(); val ch = ref.h / grid.rows.toFloat()
        val cx = (col + .5f) * cw; val cy = (row + .5f) * ch
        val sides = sidesFor(col, row, grid.cols, grid.rows, rnd)
        val la = shot.lightAngle * PI / 180; val lx = cos(la).toFloat(); val ly = sin(la).toFloat()
        val (tr, tg, tb) = shot.table.map { it.toFloat() }
        val px = IntArray(size * size)
        for (v in 0 until size) for (u in 0 until size) {
            val du = (u - size / 2f) / shot.zoom; val dv = (v - size / 2f) / shot.zoom
            val dx = (cos(-th) * du - sin(-th) * dv).toFloat(); val dy = (sin(-th) * du + cos(-th) * dv).toFloat()
            val n = rnd.nextFloat() * 10 - 5
            if (!inPiece(dx / cw, dy / ch, sides)) {
                val t = 0.85f + 0.3f * u / size + 0.04f * sin(v / 9.0).toFloat()     // gradient + grain lines
                px[v * size + u] = rgb(tr * t + n, tg * t + n, tb * t + n); continue
            }
            val sx = (cx + dx).toInt().coerceIn(0, ref.w - 1); val sy = (cy + dy).toInt().coerceIn(0, ref.h - 1)
            val p = ref.px[sy * ref.w + sx]
            // Side light in the photo frame, whatever the piece's orientation.
            val l = (1f + shot.light * (lx * (u / size.toFloat() - 0.5f) + ly * (v / size.toFloat() - 0.5f))) * shot.exposure
            px[v * size + u] = rgb((p shr 16 and 255) * shot.gains[0] * l + n, (p shr 8 and 255) * shot.gains[1] * l + n, (p and 255) * shot.gains[2] * l + n)
        }
        return grain(blur(Raster(size, size, px)), shot.seed)
    }

    /** Sensor noise, added after the optics blur like on a real phone. */
    fun grain(r: Raster, seed: Long): Raster {
        val rnd = Random(seed * 31 + 7)
        return Raster(r.w, r.h, IntArray(r.px.size) { i ->
            val p = r.px[i]; val n = rnd.nextInt(17) - 8
            rgb((p shr 16 and 255) + n.toFloat(), (p shr 8 and 255) + n.toFloat(), (p and 255) + n.toFloat())
        })
    }

    /** 3x3 box blur: a slightly soft lens. */
    fun blur(r: Raster): Raster = Raster(r.w, r.h, IntArray(r.px.size) { i ->
        val x = i % r.w; val y = i / r.w
        var a = 0; var g = 0; var b = 0; var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = (x + dx).coerceIn(0, r.w - 1); val yy = (y + dy).coerceIn(0, r.h - 1); val p = r.px[yy * r.w + xx]
            a += p shr 16 and 255; g += p shr 8 and 255; b += p and 255; n++
        }
        (255 shl 24) or (a / n shl 16) or (g / n shl 8) or b / n
    })
}
