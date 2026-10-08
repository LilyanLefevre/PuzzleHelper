package com.lilyan_lefevre.puzzleit.feature.recognition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lilyan_lefevre.puzzleit.TestImages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/** Real JPEG decode, Bitmap <-> Raster and timing on the actual phone. */
@RunWith(AndroidJUnit4::class)
class PieceRecognizerDeviceTest {

    private val dir: File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val cols = 20
    private val rows = 15
    private val art = TestImages.boxArt()
    private val ref = TestImages.save(art, dir, "ref_test.jpg")
    private val recognizer = PieceRecognizer(null)

    private fun prepare() = runBlocking { recognizer.prepare(ref.absolutePath, cols * rows, Grid(cols, rows)) }!!

    @Test
    fun preparesReferenceWithinBudget() {
        var grid: Grid? = null
        val ms = measureTimeMillis { grid = prepare().matcher.grid }
        assertEquals(Grid(cols, rows), grid)
        assertTrue("prepare took $ms ms", ms < 3000)
    }

    @Test
    fun locatesRotatedPiecesFromJpegPhotos() {
        val p = prepare()
        val cases = listOf(Triple(4, 3, 0f), Triple(12, 8, 90f), Triple(15, 11, 180f), Triple(7, 5, 270f), Triple(10, 2, 90f), Triple(3, 12, 180f))
        var hits = 0; var rot = 0; var worst = 0L
        for ((i, t) in cases.withIndex()) {
            val (col, row, deg) = t
            val photo = TestImages.save(TestImages.piecePhoto(art, cols, rows, col, row, deg), dir, "piece_$i.jpg")
            var a: Analysis? = null
            worst = maxOf(worst, measureTimeMillis { a = runBlocking { recognizer.locate(p.matcher, photo.absolutePath) } })
            val m = (a as? Analysis.Found)?.match ?: continue
            if (abs(m.best.col - (col + .5f)) <= 1f && abs(m.best.row - (row + .5f)) <= 1f) {
                hits++
                if (m.best.rotationDeg == ((360 - deg.toInt()) % 360)) rot++
            }
        }
        assertTrue("located $hits/${cases.size}", hits >= 4)
        assertTrue("rotation $rot/$hits", rot >= hits - 1)
        assertTrue("slowest scan $worst ms (PRD: < 3 s)", worst < 3000)
    }

    @Test
    fun foundResultCarriesCutoutAndAlternatives() {
        val p = prepare()
        val photo = TestImages.save(TestImages.piecePhoto(art, cols, rows, 9, 6, 90f), dir, "piece_c.jpg")
        val a = runBlocking { recognizer.locate(p.matcher, photo.absolutePath) } as Analysis.Found
        assertNotNull(a.cutout.toBitmap())
        assertTrue(a.match.alternatives.isNotEmpty())
        assertTrue(a.match.confidence in 0..99)
    }

    @Test
    fun rejectsBlurredPhoto() {
        val p = prepare()
        val f = TestImages.save(TestImages.blurred(TestImages.piecePhoto(art, cols, rows, 5, 5, 0f)), dir, "blur.jpg")
        assertTrue(runBlocking { recognizer.locate(p.matcher, f.absolutePath) } is Analysis.Blurry)
    }

    @Test
    fun rejectsEmptyTableAndMissingFile() {
        val p = prepare()
        val f = TestImages.save(TestImages.emptyTable(), dir, "empty.jpg")
        val a = runBlocking { recognizer.locate(p.matcher, f.absolutePath) }
        assertTrue(a is Analysis.NoPiece || a is Analysis.Blurry)
        assertEquals(Analysis.NoPiece, runBlocking { recognizer.locate(p.matcher, File(dir, "nope.jpg").absolutePath) })
    }

    @Test
    fun missingReferenceYieldsNull() {
        assertEquals(null, runBlocking { recognizer.prepare(File(dir, "nope.jpg").absolutePath, 100) })
    }
}
