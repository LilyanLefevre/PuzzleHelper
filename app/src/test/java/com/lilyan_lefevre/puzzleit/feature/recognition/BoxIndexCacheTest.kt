package com.lilyan_lefevre.puzzleit.feature.recognition

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BoxIndexCacheTest {

    private fun box() = Raster(80, 60, IntArray(80 * 60) { 0xFF000000.toInt() or (it * 7 and 0xFFFFFF) })

    @Test
    fun indexIsWrittenOnceThenReadBackWithoutTheNetwork() {
        val file = File.createTempFile("box_index", ".bin").also { it.delete() }
        var calls = 0
        PieceMatcher(box(), 20, Grid(5, 4), PieceReranker { inputs -> calls++; inputs.map { FloatArray(8) { k -> (k + calls).toFloat() } } }, useNcc = false).buildIndex(file)
        assertTrue("index file written", file.exists() && calls > 0)
        // A second matcher for the same box must read the file: its network would throw.
        PieceMatcher(box(), 20, Grid(5, 4), PieceReranker { error("recomputed instead of read") }, useNcc = false).buildIndex(file)
        // Another grid has another number of squares: the file no longer matches and is rebuilt.
        var rebuilt = 0
        PieceMatcher(box(), 30, Grid(6, 5), PieceReranker { inputs -> rebuilt++; inputs.map { FloatArray(8) } }, useNcc = false).buildIndex(file)
        assertTrue("stale index rebuilt", rebuilt > 0)
        file.delete()
    }
}
