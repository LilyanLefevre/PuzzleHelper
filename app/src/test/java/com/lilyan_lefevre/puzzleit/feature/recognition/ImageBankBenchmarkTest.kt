package com.lilyan_lefevre.puzzleit.feature.recognition

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Semi-synthetic benchmark on real images (paintings, photos): each image becomes a 500-piece puzzle, and pieces are
 * "photographed" with random rotation, side light, white balance, exposure and table colour (PiecePhotos). The full
 * pipeline runs: segmentation on the table, outline reading, colour matching.
 * Skipped unless PUZZLE_IMAGES_DIR holds *.bmp images (tools/dataset/prepare_image_bank.py).
 * The flat column = share of cells with almost no texture (sky, sea, plain backgrounds): the expected hard cases.
 */
class ImageBankBenchmarkTest {

    private val tables = listOf(intArrayOf(120, 86, 60), intArrayOf(48, 36, 30), intArrayOf(205, 195, 185), intArrayOf(70, 72, 78), intArrayOf(40, 90, 50))

    private class Score { var n = 0; var found = 0; var exact = 0; var near = 0; var top4 = 0; var rot = 0; var kind = 0
        fun add(o: Score) { n += o.n; found += o.found; exact += o.exact; near += o.near; top4 += o.top4; rot += o.rot; kind += o.kind }
        private fun p(x: Int) = "${100 * x / n.coerceAtLeast(1)}%".padStart(5)
        override fun toString() = "found ${p(found)} exact ${p(exact)} ±1 ${p(near)} top4 ${p(top4)} rotation ${p(rot)} outline ${p(kind)}" }

    /** Share of grid cells whose lightness barely varies. */
    private fun flatness(img: Raster, g: Grid): Int {
        val lab = LabImage.from(img); val cw = img.w / g.cols; val ch = img.h / g.rows
        var flat = 0
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            var s = 0.0; var s2 = 0.0; var n = 0
            for (y in r * ch until (r + 1) * ch step 2) for (x in c * cw until (c + 1) * cw step 2) {
                val l = lab.lab[(y * img.w + x) * 3].toDouble(); s += l; s2 += l * l; n++
            }
            if (sqrt(s2 / n - (s / n) * (s / n)) < 4.0) flat++
        }
        return 100 * flat / g.count
    }

    @Test
    fun benchmark() {
        val dir = System.getenv("PUZZLE_IMAGES_DIR")?.let(::File)
        assumeTrue("PUZZLE_IMAGES_DIR not set", dir != null && dir.isDirectory)
        val dump = LeadDump("bank")
        val total = Score()
        val buckets = linkedMapOf("textured (<15% flat)" to Score(), "mixed (15-40%)" to Score(), "flat-heavy (>40%)" to Score())
        val rows = ArrayList<Pair<Int, String>>()
        for (f in dir!!.listFiles()!!.filter { it.name.endsWith(".bmp") }.sortedBy { it.name }) {
            val img = Bmp.load(f)
            val matcher = PieceMatcher(img, 500)
            val g = matcher.grid
            val rnd = Random(f.name.hashCode().toLong())
            val score = Score()
            val cells = ArrayList<Pair<Int, Int>>()
            // 24 pieces per image: the 4 corners, 6 edge pieces (sides in turn), 14 interior pieces.
            listOf(0 to 0, g.cols - 1 to 0, g.cols - 1 to g.rows - 1, 0 to g.rows - 1).forEach(cells::add)
            repeat(6) { cells += when (it % 4) {
                0 -> 1 + rnd.nextInt(g.cols - 2) to 0; 1 -> g.cols - 1 to 1 + rnd.nextInt(g.rows - 2)
                2 -> 1 + rnd.nextInt(g.cols - 2) to g.rows - 1; else -> 0 to 1 + rnd.nextInt(g.rows - 2) } }
            repeat(14) { cells += 1 + rnd.nextInt(g.cols - 2) to 1 + rnd.nextInt(g.rows - 2) }
            for ((i, cell) in cells.withIndex()) {
                val (col, row) = cell
                val cw = img.w / g.cols.toFloat()
                val shot = PiecePhotos.Shot(
                    deg = rnd.nextInt(360).toDouble(),
                    zoom = (110f + rnd.nextInt(50)) / cw,               // the piece spans ~110-160 px of the 380 px photo
                    seed = rnd.nextLong(),
                    light = rnd.nextFloat() * 0.6f, lightAngle = rnd.nextInt(360).toDouble(),
                    gains = FloatArray(3) { 0.85f + rnd.nextFloat() * 0.3f }, exposure = 0.8f + rnd.nextFloat() * 0.35f,
                    table = tables[rnd.nextInt(tables.size)],
                )
                val photo = PiecePhotos.photo(img, g, col, row, shot)
                score.n++
                if (dump.enabled) {
                    val ins = matcher.inspect(photo)
                    val mask = ins.mask
                    val lm = mask?.let { matcher.rank(LabImage.from(ins.image), it, ins.shape, LeadDump.LEADS) }
                    if (mask != null && lm != null) {
                        val cut = Raster(ins.image.w, ins.image.h, IntArray(mask.size) { if (mask[it]) ins.image.px[it] else 0xFF000000.toInt() })
                        dump.add(f, g, cut, col, row, (360 - shot.deg.toInt()) % 360, lm)
                    }
                }
                val m = (matcher.locate(photo) as? Analysis.Found)?.match ?: continue
                score.found++
                val wantKind = when { i < 4 -> PieceKind.CORNER; i < 10 -> PieceKind.EDGE; else -> PieceKind.INTERIOR }
                if (m.kind == wantKind) score.kind++
                fun near(c: Candidate) = abs(c.col - (col + .5f)) <= 1.01f && abs(c.row - (row + .5f)) <= 1.01f
                if (hypot(m.best.col - (col + .5f), m.best.row - (row + .5f)) <= 0.51f) score.exact++
                if (near(m.best)) score.near++
                if ((listOf(m.best) + m.alternatives).any(::near)) score.top4++
                val want = (360 - shot.deg.toInt()) % 360
                if (abs(((m.best.rotationDeg - want) % 360 + 540) % 360 - 180) <= 10) score.rot++
            }
            total.add(score)
            val flat = flatness(img, g)
            buckets.values.elementAt(when { flat < 15 -> 0; flat <= 40 -> 1; else -> 2 }).add(score)
            rows += flat to "BANK ${f.nameWithoutExtension.take(28).padEnd(28)} flat ${"$flat%".padStart(4)} | $score"
        }
        rows.sortedBy { it.first }.forEach { println(it.second) }
        buckets.forEach { (k, v) -> println("BANK ${k.padEnd(28)} ${"${v.n}".padStart(4)} pcs | $v") }
        println("BANK ${"ALL".padEnd(28)} ${"${total.n}".padStart(4)} pcs | $total")
    }
}
