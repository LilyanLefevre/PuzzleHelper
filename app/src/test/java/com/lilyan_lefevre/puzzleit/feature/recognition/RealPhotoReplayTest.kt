package com.lilyan_lefevre.puzzleit.feature.recognition

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Replays real photos pulled from a phone. Skipped unless PUZZLE_REAL_DIR points to a folder with:
 *   box.bmp, grid.txt ("cols rows"), capture *.bmp files, optionally truth.txt ("<file> <col> <row>", 0-based).
 * 24-bit BMP because Android unit tests have no ImageIO; convert with: sips -s format bmp x.jpg --out x.bmp
 * Writes <name>_mask.bmp next to each capture so the segmentation can be checked by eye.
 */
class RealPhotoReplayTest {

    @Test
    fun replay() {
        val dir = System.getenv("PUZZLE_REAL_DIR")?.let(::File)
        assumeTrue("PUZZLE_REAL_DIR not set", dir != null && dir.isDirectory)
        val (cols, rows) = File(dir!!, "grid.txt").readText().trim().split(Regex("\\s+")).map { it.toInt() }
        val truth = File(dir!!, "truth.txt").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }
            ?.associate { l -> l.split(Regex("\\s+")).let { it[0] to (it[1].toInt() to it[2].toInt()) } } ?: emptyMap()
        val matcher = PieceMatcher(Bmp.load(File(dir, "box.bmp")), cols * rows, Grid(cols, rows), TestReranker.fromEnv(), TestReranker.useNcc)
        var ok = 0; var known = 0
        for (f in dir!!.listFiles()!!.filter { it.name.endsWith(".bmp") && it.name != "box.bmp" && !it.name.contains("_mask") }.sortedBy { it.name }) {
            val photo = Bmp.load(f).centerSquare(PieceRecognizer.CROP)
            val ins = matcher.inspect(photo)
            val a = matcher.locate(photo)
            val verdict = when (a) {
                is Analysis.Found -> "(${a.match.best.col}, ${a.match.best.row}) rot=${a.match.best.rotationDeg} conf=${a.match.confidence} kind=${a.match.kind} " +
                    "alts=" + a.match.alternatives.joinToString { "(${it.col},${it.row})" }
                else -> a.toString()
            }
            val t = truth[f.name]
            // Rank of the true cell among up to 400 spread-out leads (colour matcher only): is it close or lost?
            val rank = t?.let { tt -> ins.mask?.let { m -> matcher.rank(LabImage.from(ins.image), m, ins.shape, 400, matcher.pixelSearch(ins.image, m)) }
                ?.let { mt -> (listOf(mt.best) + mt.alternatives).indexOfFirst { kotlin.math.abs(it.col - (tt.first + .5f)) <= 1.01f && kotlin.math.abs(it.row - (tt.second + .5f)) <= 1.01f } } }
            if (t != null && a is Analysis.Found) {
                known++
                if (kotlin.math.abs(a.match.best.col - (t.first + .5f)) <= 1.01f && kotlin.math.abs(a.match.best.row - (t.second + .5f)) <= 1.01f) ok++
            }
            println("REAL ${f.name}: sharp=${ins.sharpness} area=${ins.mask?.count { it }}/${ins.image.w * ins.image.h} " +
                "tilt=${ins.shape?.tilt} sides=${ins.shape?.sides} -> $verdict" + (t?.let { " truth=$it colourRank=${rank?.plus(1)}" } ?: ""))
            ins.mask?.let { m -> matcher.pixelSearch(ins.image, m)?.peaks(4)?.let { p -> println("REAL   pixel peaks: " + p.joinToString { "(%.1f,%.1f)=%.2f".format(it.first, it.second, it.third) }) } }
            // Debug image: photo dimmed outside the mask.
            val img = ins.image
            Bmp.save(Raster(img.w, img.h, IntArray(img.px.size) { i -> if (ins.mask?.get(i) == true) img.px[i] else (img.px[i] shr 2) and 0x3F3F3F }),
                File(dir, f.nameWithoutExtension + "_mask.bmp"))
        }
        if (known > 0) println("REAL accuracy: $ok/$known within one cell")
    }
}
