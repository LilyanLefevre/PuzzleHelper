package com.lilyan_lefevre.puzzleit.feature.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Runs the segmentation network (assets/segmenter.onnx, U2-Net-small) on one [PieceSegmenter.SIZE] square CHW tensor. */
class OnnxSegmenter(model: ByteArray) {
    private val env = OrtEnvironment.getEnvironment()
    // No memory pattern and no arena: with batches of many different sizes (4 to 128 images) onnxruntime kept one plan and a growing
    // reserve per shape, +30 MB of native memory per scan until the system killed the app on a 2 GB device.
    private val options = OrtSession.SessionOptions().apply { setMemoryPatternOptimization(false); setCPUArenaAllocator(false) }
    private val session: OrtSession = env.createSession(model, options)

    fun run(input: FloatArray): FloatArray {
        val s = PieceSegmenter.SIZE.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, s, s)).use { t ->
            session.run(mapOf(session.inputNames.first() to t)).use { out ->
                @Suppress("UNCHECKED_CAST")
                return (out[0].value as Array<Array<Array<FloatArray>>>)[0][0].flatMap { it.asList() }.toFloatArray()
            }
        }
    }
}
