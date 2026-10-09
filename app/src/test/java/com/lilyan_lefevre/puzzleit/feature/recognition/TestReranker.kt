package com.lilyan_lefevre.puzzleit.feature.recognition

import java.io.File

/** The benchmarks re-rank only when PUZZLE_RERANKER points to the ONNX model (app/src/main/assets/reranker.onnx). */
object TestReranker {
    fun fromEnv(): PieceReranker? =
        System.getenv("PUZZLE_RERANKER")?.let { OnnxEmbedder(File(it).readBytes()) }?.let { e -> PieceReranker(e::embed) }
}
