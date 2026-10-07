package com.lilyan_lefevre.puzzleit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File
import java.io.FileOutputStream
import java.util.Random

/** Synthetic "box art" and piece photos, built with Canvas so the device decodes real JPEGs. */
object TestImages {
    const val W = 900
    const val H = 600

    fun boxArt(seed: Long = 7): Bitmap {
        val rnd = Random(seed)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(120, 130, 120))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(160) {
            val r = 30f + rnd.nextFloat() * 90f
            val col = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            p.shader = RadialGradient(rnd.nextFloat() * W, rnd.nextFloat() * H, r, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        }
        return bmp
    }

    /** A square piece cut at ([col],[row]) of a [cols]x[rows] grid, turned clockwise by [deg], photographed on a table. */
    fun piecePhoto(art: Bitmap, cols: Int, rows: Int, col: Int, row: Int, deg: Float, zoom: Float = 6f): Bitmap {
        val cw = art.width / cols; val ch = art.height / rows
        val piece = Bitmap.createBitmap(art, col * cw, row * ch, cw, ch)
        val out = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(Color.rgb(205, 195, 185))
        val m = Matrix().apply { postTranslate(-cw / 2f, -ch / 2f); postScale(zoom, zoom); postRotate(deg); postTranslate(450f, 450f) }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 }
        c.drawBitmap(piece, m, paint)
        return out
    }

    fun save(b: Bitmap, dir: File, name: String): File =
        File(dir, name).also { f -> FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) } }

    fun blurred(b: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(b, b.width / 24, b.height / 24, true)
        return Bitmap.createScaledBitmap(small, b.width, b.height, true)
    }

    fun emptyTable(): Bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.rgb(205, 195, 185)) }
}
