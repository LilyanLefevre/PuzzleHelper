package com.lilyan_lefevre.puzzleit.feature.recognition

import java.io.File

/**
 * Optional export for off-device experiments (e.g. re-ranking with a neural network on a desktop GPU).
 * Enabled by PUZZLE_DUMP_DIR. One line per piece in <dir>/leads.tsv:
 *   suite  box  cols  rows  piece.bmp  truthCol  truthRow  truthRot  kind  col:row:rot:dist|...   (top 30 leads)
 * truthRot = clockwise turn that puts the photographed piece back the way it is on the box.
 */
class LeadDump(suite: String) {
    private val dir = System.getenv("PUZZLE_DUMP_DIR")?.let { File(it, suite).also(File::mkdirs) }
    val enabled get() = dir != null
    private var n = 0

    fun add(box: File, grid: Grid, piece: Raster, truthCol: Int, truthRow: Int, truthRot: Int, match: Match) {
        val d = dir ?: return
        val name = "piece_%05d.bmp".format(n++)
        Bmp.save(piece, File(d, name))
        val leads = (listOf(match.best) + match.alternatives).joinToString("|") { "${it.col}:${it.row}:${it.rotationDeg}:${it.distance}" }
        File(d, "leads.tsv").appendText(listOf(d.name, box.absolutePath, grid.cols, grid.rows, name, truthCol, truthRow, truthRot, match.kind, leads).joinToString("\t") + "\n")
    }

    companion object { const val LEADS = 30 }
}
