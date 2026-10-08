package com.lilyan_lefevre.puzzleit.feature.recognition

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 24/32-bit BMP read/write: Android unit tests have no ImageIO. Convert with `sips -s format bmp in.jpg --out out.bmp`. */
object Bmp {
    fun load(f: File): Raster {
        val buf = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val off = buf.getInt(10); val w = buf.getInt(18); val hRaw = buf.getInt(22); val bpp = buf.getShort(28).toInt()
        val h = kotlin.math.abs(hRaw); val bytes = bpp / 8; val stride = (w * bytes + 3) / 4 * 4
        val a = buf.array()
        return Raster(w, h, IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val row = if (hRaw > 0) h - 1 - y else y
            val o = off + row * stride + x * bytes
            (255 shl 24) or ((a[o + 2].toInt() and 255) shl 16) or ((a[o + 1].toInt() and 255) shl 8) or (a[o].toInt() and 255)
        })
    }

    fun save(r: Raster, f: File) {
        val stride = (r.w * 3 + 3) / 4 * 4
        val buf = ByteBuffer.allocate(54 + stride * r.h).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('B'.code.toByte()).put('M'.code.toByte()).putInt(54 + stride * r.h).putInt(0).putInt(54)
            .putInt(40).putInt(r.w).putInt(r.h).putShort(1).putShort(24).putInt(0).putInt(stride * r.h).putInt(2835).putInt(2835).putInt(0).putInt(0)
        for (y in r.h - 1 downTo 0) {
            for (x in 0 until r.w) { val p = r.px[y * r.w + x]; buf.put((p and 255).toByte()).put((p shr 8 and 255).toByte()).put((p shr 16 and 255).toByte()) }
            repeat(stride - r.w * 3) { buf.put(0) }
        }
        f.writeBytes(buf.array())
    }
}
