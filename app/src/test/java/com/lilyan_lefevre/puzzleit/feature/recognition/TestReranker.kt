package com.lilyan_lefevre.puzzleit.feature.recognition

import java.io.File

/** The benchmarks re-rank only when PUZZLE_RERANKER points to the ONNX model (app/src/main/assets/reranker.onnx). */
object TestReranker {
    /** PUZZLE_NCC=0 turns the pixel search off, PUZZLE_NCC_WEIGHT tunes it (default: the app's). */
    val useNcc: Boolean = (System.getenv("PUZZLE_NCC") != "0").also { System.getenv("PUZZLE_NCC_WEIGHT")?.toFloatOrNull()?.let { w -> PieceMatcher.NCC_WEIGHT = w } }

    fun fromEnv(): PieceReranker? =
        System.getenv("PUZZLE_RERANKER")?.takeIf { it.isNotBlank() }?.let { OnnxEmbedder(File(it).readBytes()) }?.let { e -> PieceReranker(e::embed) }
}
