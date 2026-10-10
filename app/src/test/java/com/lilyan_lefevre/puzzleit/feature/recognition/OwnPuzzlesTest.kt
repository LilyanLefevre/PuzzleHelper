package com.lilyan_lefevre.puzzleit.feature.recognition

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The owner's own puzzles: simulated pieces cut from each box vs the real phone photos labelled with
 * tools/dataset/label_pieces.html. Skipped unless PUZZLE_OWN_DIR holds one folder per puzzle with
 * box.bmp, grid.txt ("cols rows"), truth.txt ("<capture>.bmp col row rot cx cy", from label_pieces.html) and the capture BMPs in all/.
 * Same scoring on both sides, so the gap between them is the realism the simulator is missing.
 */
class OwnPuzzlesTest {

    private class Score {
        var n = 0; var found = 0; var exact = 0; var near = 0; var top4 = 0; var rot = 0; var err = 0.0; var errN = 0
        fun add(m: Match?, tx: Float, ty: Float, wantRot: Int?) {
            n++
            if (m == null) return
            found++
            fun d(c: Candidate) = hypot(c.col - tx, c.row - ty)
            if (d(m.best) <= 0.51f) exact++
            if (d(m.best) <= 1.01f) { err += d(m.best); errN++ }
            if (d(m.best) <= 1.01f) near++
            if ((listOf(m.best) + m.alternatives).take(4).any { d(it) <= 1.01f }) top4++
            // The label is a quarter turn, the real tilt is free: compare within half a quarter turn.
            if (wantRot != null && abs(((m.best.rotationDeg - wantRot) % 360 + 540) % 360 - 180) <= 45) rot++
        }
        private fun p(x: Int) = "${100 * x / n.coerceAtLeast(1)}%".padStart(5)
        override fun toString() = "n=${n.toString().padStart(3)} found ${p(found)} exact ${p(exact)} +-1 ${p(near)} top4 ${p(top4)} rotation ${p(rot)} mean-err ${String.format(java.util.Locale.ROOT, "%.2f", if (errN > 0) err / errN else 0.0)} cells (n=$errN)"
    }

    private val tables = listOf(intArrayOf(48, 36, 30), intArrayOf(70, 72, 78), intArrayOf(40, 40, 42))

    @Test
    fun simulatedVersusReal() {
        val root = System.getenv("PUZZLE_OWN_DIR")?.let(::File)
        assumeTrue("PUZZLE_OWN_DIR not set", root != null && root.isDirectory)
        val reranker = TestReranker.fromEnv()
        System.getenv("PUZZLE_BORDER_PENALTY")?.toFloatOrNull()?.let { PieceMatcher.BORDER_PENALTY = it }
        for (dir in root!!.listFiles()!!.filter { File(it, "box.bmp").exists() }.sortedBy { it.name }) {
            val (cols, rows) = File(dir, "grid.txt").readText().trim().split(Regex("\\s+")).map { it.toInt() }
            val box = Bmp.load(File(dir, "box.bmp"))
            val matcher = PieceMatcher(box, cols * rows, Grid(cols, rows), reranker, TestReranker.useNcc, TestReranker.segmenter)
            val g = matcher.grid
            fun simulate(wide: Boolean): Score {
                val rnd = Random(dir.name.hashCode().toLong())
                val sc = Score()
                repeat(60) {
                    val col = rnd.nextInt(g.cols); val row = rnd.nextInt(g.rows)
                    // wide = the camera variations measured on the real photos (up to x2 per channel), narrow = the historical ones.
                    val gainLo = if (wide) 0.6f else 0.85f; val gainSpan = if (wide) 0.9f else 0.3f
                    val expo = if (wide) 0.6f + rnd.nextFloat() * 1.2f else 0.8f + rnd.nextFloat() * 0.35f
                    val shot = PiecePhotos.Shot(
                        deg = rnd.nextInt(360).toDouble(), zoom = (110f + rnd.nextInt(50)) / (box.w / g.cols.toFloat()), seed = rnd.nextLong(),
                        light = rnd.nextFloat() * 0.6f, lightAngle = rnd.nextInt(360).toDouble(),
                        gains = FloatArray(3) { gainLo + rnd.nextFloat() * gainSpan }, exposure = expo,
                        table = tables[rnd.nextInt(tables.size)],
                    )
                    val m = (matcher.locate(PiecePhotos.photo(box, g, col, row, shot), leads = 8) as? Analysis.Found)?.match
                    m?.let { TestReranker.calib(if (wide) "own-sim-wide" else "own-sim", it, col + .5f, row + .5f) }
                    sc.add(m, col + .5f, row + .5f, (360 - shot.deg.toInt()) % 360)
                }
                return sc
            }
            val sim = simulate(false); val simWide = simulate(true)
            val real = Score()
            File(dir, "truth.txt").readLines().filter { it.isNotBlank() }.forEach { l ->
                val f = l.trim().split(Regex("\\s+"))
                if (f[4].toFloat() > cols || f[5].toFloat() > rows) return@forEach   // label typed with another puzzle's grid
                val photo = Bmp.load(File(dir, "all/" + f[0])).centerSquare(PieceRecognizer.CROP)
                val found = matcher.locate(photo, leads = 8) as? Analysis.Found
                found?.let { TestReranker.calib("own-real", it.match, f[4].toFloat(), f[5].toFloat()) }
                val m = found?.match
                real.add(m, f[4].toFloat(), f[5].toFloat(), f[3].toInt())
                println("OWN   ${dir.name} ${f[0]} truth=(${f[4]},${f[5]}) -> " + (m?.let { "(${it.best.col},${it.best.row}) conf=${it.confidence} alts=" + it.alternatives.joinToString { a -> "(${a.col},${a.row})" } } ?: "none"))
            }
            println("OWN ${dir.name.padEnd(10)} simulated | $sim")
            println("OWN ${dir.name.padEnd(10)} sim-wide  | $simWide")
            println("OWN ${dir.name.padEnd(10)} real      | $real")
        }
    }

    /** For tools/dataset/label_pieces.html: the 8 best places per capture (the BMPs in all/), written to <puzzle>/leads.json, so labelling is a check, not a search. */
    @Test
    fun dumpLeadsForLabelling() {
        val root = System.getenv("PUZZLE_OWN_DIR")?.let(::File)
        assumeTrue("PUZZLE_OWN_DIR not set", root != null && root.isDirectory)
        for (dir in root!!.listFiles()!!.filter { File(it, "box.bmp").exists() && File(it, "all").isDirectory }.sortedBy { it.name }) {
            val (cols, rows) = File(dir, "grid.txt").readText().trim().split(Regex("\\s+")).map { it.toInt() }
            val matcher = PieceMatcher(Bmp.load(File(dir, "box.bmp")), cols * rows, Grid(cols, rows), TestReranker.fromEnv(), true, TestReranker.segmenter)
            val out = File(dir, "all").listFiles()!!.filter { it.name.endsWith(".bmp") }.sortedBy { it.name }.map { f ->
                val found = matcher.locate(Bmp.load(f).centerSquare(PieceRecognizer.CROP), leads = 8) as? Analysis.Found
                // The cut-out piece and its mask (BMP has no alpha), for the labelling tool's drag-and-drop overlay.
                found?.let {
                    File(dir, "cuts").mkdirs()
                    Bmp.save(it.cutout, File(dir, "cuts/${f.nameWithoutExtension}.bmp"))
                    Bmp.save(Raster(it.cutout.w, it.cutout.h, IntArray(it.cutout.px.size) { i -> if (it.cutout.px[i] ushr 24 != 0) -1 else 0xFF000000.toInt() }), File(dir, "cuts/${f.nameWithoutExtension}_mask.bmp"))
                }
                val m = found?.match
                val leads = m?.let { listOf(it.best) + it.alternatives }.orEmpty()
                "\"${f.nameWithoutExtension}.jpg\":[" + leads.joinToString(",") { "[${it.col},${it.row},${it.rotationDeg}]" } + "]"
            }
            File(dir, "leads.json").writeText("{" + out.joinToString(",") + "}")
        }
    }
}
