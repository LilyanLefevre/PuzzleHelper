package com.lilyan_lefevre.puzzleit.feature.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Runs the re-ranker network (assets/reranker.onnx) on a batch of [PieceReranker.SIZE] square CHW tensors. */
class OnnxEmbedder(model: ByteArray) {
    private val env = OrtEnvironment.getEnvironment()
    // No memory pattern and no arena: with batches of many different sizes (4 to 128 images) onnxruntime kept one plan and a growing
    // reserve per shape, +30 MB of native memory per scan until the system killed the app on a 2 GB device.
    private val options = OrtSession.SessionOptions().apply { setMemoryPatternOptimization(false); setCPUArenaAllocator(false) }
    private val session: OrtSession = env.createSession(model, options)

    fun embed(inputs: List<FloatArray>): List<FloatArray> {
        val s = PieceReranker.SIZE
        val flat = FloatBuffer.allocate(inputs.size * 3 * s * s).apply { inputs.forEach(::put); rewind() }
        OnnxTensor.createTensor(env, flat, longArrayOf(inputs.size.toLong(), 3, s.toLong(), s.toLong())).use { t ->
            session.run(mapOf("image" to t)).use { out ->
                @Suppress("UNCHECKED_CAST")
                return (out[0].value as Array<FloatArray>).toList()
            }
        }
    }
}
