package com.lilyan_lefevre.puzzleit.feature.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PieceSegmenterTest {

    @Test
    fun saliencyIsNormalisedAndResizedToThePhoto() {
        val s = PieceSegmenter.SIZE
        // A fake network that finds the piece in the left half of its 320 x 320 input.
        val segmenter = PieceSegmenter { input ->
            assertEquals(3 * s * s, input.size)
            FloatArray(s * s) { if (it % s < s / 2) 5f else -5f }
        }
        val sal = segmenter.saliency(Raster(200, 100, IntArray(200 * 100) { 0xFF808080.toInt() }))
        assertEquals(200 * 100, sal.size)
        assertTrue("piece side near 1", sal[50 * 200 + 20] > 0.95f)
        assertTrue("table side near 0", sal[50 * 200 + 180] < 0.05f)
        assertTrue("always within 0..1", sal.all { it in 0f..1f })
    }
}
