package com.lilyan_lefevre.puzzleit.feature.recognition

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Replays a public dataset of real piece photos with known positions (Puzzle-Map, "120_avengers", CC-BY-4.0).
 * Skipped unless PUZZLE_DATASET_DIR holds box.bmp, grid.txt ("cols rows") and pieces.tsv:
 *   file  col  row  angle  tilt  x0 y0 x1 y1  top right bottom left      (col/row 0-based, angle = CCW quarter turns
 *   of the piece in the photo, bbox inside the crop, sides SMOOTH|OUTER|INNER as seen in the photo)
 * Pieces are hand-held in front of cluttered scenes, so the mask is the body inside the annotated box: this measures
 * the colour matching and the outline constraints, not the table segmentation.
 */
class DatasetReplayTest {

    private class Score { var n = 0; var near = 0; var exact = 0; var top4 = 0; var rot = 0
        override fun toString() = "near=$near/$n (${100 * near / n.coerceAtLeast(1)}%) exact=$exact top4=$top4 rotationOkAmongNear=$rot/$near" }

    @Test
    fun replay() {
        val dir = System.getenv("PUZZLE_DATASET_DIR")?.let(::File)
        assumeTrue("PUZZLE_DATASET_DIR not set", dir != null && dir.isDirectory)
        val (cols, rows) = File(dir!!, "grid.txt").readText().trim().split(Regex("\\s+")).map { it.toInt() }
        val matcher = PieceMatcher(Bmp.load(File(dir, "box.bmp")), cols * rows, Grid(cols, rows))
        val colourOnly = Score(); val withSides = Score()
        val t0 = System.nanoTime()
        for (line in File(dir, "pieces.tsv").readLines().filter { it.isNotBlank() }) {
            val p = line.split('\t')
            val img = Bmp.load(File(dir, p[0]))
            val col = p[1].toInt(); val row = p[2].toInt(); val angle = p[3].toInt()
            val (x0, y0, x1, y1) = p.subList(5, 9).map { it.toInt() }
            val sides = p.subList(9, 13).map { when (it) { "SMOOTH" -> Side.FLAT; "OUTER" -> Side.TAB; else -> Side.BLANK } }
            // Body of the piece: the annotated box minus the knobs.
            val ix = ((x1 - x0) * 0.16f).toInt(); val iy = ((y1 - y0) * 0.16f).toInt()
            val lab = LabImage.from(img)
            val mask = BooleanArray(img.w * img.h) { i -> val x = i % img.w; val y = i / img.w; x in x0 + ix until x1 - ix && y in y0 + iy until y1 - iy }
            // angle = CCW rotation of the piece in the photo, so the clockwise correction is the same angle.
            val want = ((angle % 360) + 360) % 360
            for ((score, shape) in listOf(colourOnly to null, withSides to Shape(0f, sides))) {
                val m = matcher.rank(lab, mask, shape) ?: continue
                score.n++
                fun ok(c: Candidate) = abs(c.col - (col + .5f)) <= 1.01f && abs(c.row - (row + .5f)) <= 1.01f
                if (ok(m.best)) {
                    score.near++
                    val d = abs(((m.best.rotationDeg - want) % 360 + 540) % 360 - 180)
                    if (d <= 20) score.rot++
                }
                if (hypot(m.best.col - (col + .5f), m.best.row - (row + .5f)) <= 0.51f) score.exact++
                if ((listOf(m.best) + m.alternatives).any(::ok)) score.top4++
            }
        }
        println("DATASET colour only : $colourOnly")
        println("DATASET + flat sides: $withSides")
        println("DATASET ${(System.nanoTime() - t0) / 1_000_000} ms")
    }
}
