package com.lilyan_lefevre.puzzleit.feature.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Runs the segmentation network (assets/segmenter.onnx, U2-Net-small) on one [PieceSegmenter.SIZE] square CHW tensor. */
class OnnxSegmenter(model: ByteArray) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(model, OrtSession.SessionOptions())

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
