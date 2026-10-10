package com.lilyan_lefevre.puzzleit.feature.recognition

import java.io.File

/** The benchmarks re-rank only when PUZZLE_RERANKER points to the ONNX model (app/src/main/assets/reranker.onnx). */
object TestReranker {
    /** PUZZLE_NCC=0 turns the pixel search off, PUZZLE_NCC_WEIGHT tunes it (default: the app's). */
    val useNcc: Boolean = (System.getenv("PUZZLE_NCC") != "0").also { System.getenv("PUZZLE_NCC_WEIGHT")?.toFloatOrNull()?.let { w -> PieceMatcher.NCC_WEIGHT = w } }

    /** PUZZLE_SEGMENTER = the U2-Net ONNX (app/src/main/assets/segmenter.onnx); without it the table-colour segmentation is used. */
    val segmenter: PieceSegmenter? by lazy {
        System.getenv("PUZZLE_SEGMENTER")?.takeIf { it.isNotBlank() }?.let { OnnxSegmenter(File(it).readBytes()) }?.let { n -> PieceSegmenter(n::run) }
    }

    /** PUZZLE_GLOBAL=0 keeps the network to the colour matcher's top leads; PUZZLE_BORDER_PENALTY=1.4 makes the border constraint soft. */
    /** PUZZLE_CALIB=1 prints, per scan, the fused score and the correctness (within one cell) of the best 8 leads: input of the confidence calibration. */
    fun calib(tag: String, m: Match, tx: Float, ty: Float) {
        if (System.getenv("PUZZLE_CALIB") != "1") return
        val leads = (listOf(m.best) + m.alternatives).take(8)
        println("CALIB $tag " + leads.joinToString(" ") { String.format(java.util.Locale.ROOT, "%.3f:%d", it.score, if (kotlin.math.hypot(it.col - tx, it.row - ty) <= 1.01f) 1 else 0) })
    }

    fun fromEnv(): PieceReranker? {
        System.getenv("PUZZLE_REFINE_RADIUS")?.toIntOrNull()?.let { PieceMatcher.REFINE_RADIUS = it }
        System.getenv("PUZZLE_REFINE_STEP")?.toFloatOrNull()?.let { PieceMatcher.REFINE_STEP = it }
        System.getenv("PUZZLE_RESID_WEIGHT")?.toDoubleOrNull()?.let { PieceMatcher.RESID_WEIGHT = it }
        System.getenv("PUZZLE_INTERIOR_COST")?.toDoubleOrNull()?.let { PieceMatcher.INTERIOR_BORDER_COST = it }
        System.getenv("PUZZLE_REFINE")?.toIntOrNull()?.let { PieceMatcher.REFINE_LEADS = it }
        System.getenv("PUZZLE_GLOBAL")?.let { PieceMatcher.GLOBAL_EMBED = it == "1" }
        System.getenv("PUZZLE_BORDER_PENALTY")?.toFloatOrNull()?.let { PieceMatcher.BORDER_PENALTY = it }
        System.getenv("PUZZLE_EMBED_COLOUR_W")?.toDoubleOrNull()?.let { PieceMatcher.EMBED_COLOUR_WEIGHT = it }
        return System.getenv("PUZZLE_RERANKER")?.takeIf { it.isNotBlank() }?.let { OnnxEmbedder(File(it).readBytes()) }?.let { e -> PieceReranker(e::embed) }
    }
}
