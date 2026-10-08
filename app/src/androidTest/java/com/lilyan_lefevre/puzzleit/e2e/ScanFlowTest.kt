package com.lilyan_lefevre.puzzleit.e2e

import android.Manifest
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lilyan_lefevre.puzzleit.MainActivity
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.TestImages
import com.lilyan_lefevre.puzzleit.feature.project.ProjectRepository
import com.lilyan_lefevre.puzzleit.feature.puzzle.PieceCaptureFragment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Whole journey on the phone: list -> puzzle table -> (injected) piece photo -> result sheet -> leads -> dismiss.
 * The photo is injected through the same fragment-result channel the camera screen uses, so everything
 * downstream of the shutter is the real code. Run with animations off (see CI script / README).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ScanFlowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Inject lateinit var repository: ProjectRepository

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "e2e-scan-flow"
    private val name = "E2E Cascade"
    private val cols = 20
    private val rows = 15
    private val art = TestImages.boxArt()

    @Before
    fun seed() {
        hilt.inject()
        // Delete a leftover project first: deleting it also deletes its image file.
        runBlocking { repository.deleteProject(id) }
        val ref = TestImages.save(art, ctx.filesDir, "e2e_ref.jpg").absolutePath
        runBlocking {
            repository.createProject(id, ref, ref, ref, name, cols * rows, rows, cols, "medium", null)
        }
    }

    @After
    fun cleanUp() {
        runBlocking { repository.deleteProject(id) }
        File(ctx.filesDir, "e2e_ref.jpg").delete()
    }

    private fun waitFor(timeoutMs: Long = 20_000, check: () -> Unit) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            try { check(); return } catch (e: Throwable) {
                if (System.currentTimeMillis() > end) throw e
                Thread.sleep(250)
            }
        }
    }

    /** Screenshots land in the app's external files dir: adb pull /sdcard/Android/data/<pkg>/files/shots */
    private fun shot(scenario: ActivityScenario<MainActivity>, label: String) {
        // PixelCopy of the activity window: unlike UiAutomation it doesn't disturb Espresso's window focus.
        var window: android.view.Window? = null
        scenario.onActivity { window = it.window }
        val w = window ?: return
        val bmp = android.graphics.Bitmap.createBitmap(w.decorView.width, w.decorView.height, android.graphics.Bitmap.Config.ARGB_8888)
        val latch = java.util.concurrent.CountDownLatch(1)
        android.view.PixelCopy.request(w, bmp, { latch.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        val dir = File(ctx.getExternalFilesDir(null), "shots").also { it.mkdirs() }
        java.io.FileOutputStream(File(dir, "$label.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun inject(scenario: ActivityScenario<MainActivity>, photo: File) {
        scenario.onActivity { a: FragmentActivity ->
            val host = a.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
            host.childFragmentManager.setFragmentResult(PieceCaptureFragment.RESULT_KEY, bundleOf(PieceCaptureFragment.PHOTO_PATH to photo.absolutePath))
        }
    }

    private fun openTable() = ActivityScenario.launch(MainActivity::class.java).also {
        waitFor { onView(withText(name)).check(matches(isDisplayed())) }
        onView(withText(name)).perform(click())
        waitFor { onView(withId(R.id.buttonCapturePiece)).check(matches(isDisplayed())) }
    }

    @Test
    fun listShowsProjectAndTableShowsGrid() {
        openTable().use { scenario ->
            // The project loads asynchronously: wait for it instead of asserting on the first frame.
            waitFor { onView(withId(R.id.textViewProjectName)).check(matches(withText(name))) }
            waitFor { onView(withId(R.id.textViewProjectInfo)).check(matches(withText(containsString("20 × 15")))) }
            onView(withId(R.id.mapView)).check(matches(isDisplayed()))
            Thread.sleep(800); shot(scenario, "1_table")
        }
    }

    @Test
    fun scanShowsResultWithLeadsThenDismisses() {
        openTable().use { scenario ->
            val photo = TestImages.save(TestImages.piecePhoto(art, cols, rows, 9, 6, 90f), ctx.cacheDir, "e2e_piece.jpg")
            inject(scenario, photo)
            // The analysing state is brief by design: catch it if we can, never fail on it.
            runCatching { waitFor(3_000) { onView(withId(R.id.groupAnalyzing)).check(matches(isDisplayed())) }; shot(scenario, "2_analyzing") }
            waitFor { onView(withId(R.id.groupResult)).check(matches(isDisplayed())) }
            Thread.sleep(1800)   // let the camera move and the piece spin settle
            shot(scenario, "3_result")
            onView(withId(R.id.textSpot)).check(matches(isDisplayed()))
            onView(withId(R.id.confBar)).check(matches(isDisplayed()))
            onView(withId(R.id.chipLeads)).check(matches(isDisplayed()))
            onView(withId(R.id.chipLeads)).check(matches(isDisplayed()))
            onView(withText(containsString(ctx.getString(R.string.alt_lead, 2)))).perform(click())
            waitFor { onView(withId(R.id.textLead)).check(matches(withText(containsString(ctx.getString(R.string.alt_lead, 2))))) }
            Thread.sleep(1200); shot(scenario, "4_second_lead")
            onView(withId(R.id.buttonDismiss)).perform(click())
            waitFor { onView(withId(R.id.groupIdle)).check(matches(isDisplayed())) }
        }
    }

    @Test
    fun blurryPhotoAsksToRetake() {
        openTable().use { scenario ->
            val photo = TestImages.save(TestImages.blurred(TestImages.piecePhoto(art, cols, rows, 5, 5, 0f)), ctx.cacheDir, "e2e_blur.jpg")
            inject(scenario, photo)
            waitFor { onView(withId(R.id.groupError)).check(matches(isDisplayed())) }
            onView(withId(R.id.textErrorTitle)).check(matches(withText(R.string.blurry_title)))
            onView(withId(R.id.buttonRetake)).check(matches(isDisplayed()))
            shot(scenario, "5_blurry")
        }
    }

    @Test
    fun emptyTableReportsNoPiece() {
        openTable().use { scenario ->
            val photo = TestImages.save(TestImages.emptyTable(), ctx.cacheDir, "e2e_empty.jpg")
            inject(scenario, photo)
            waitFor { onView(withId(R.id.groupError)).check(matches(isDisplayed())) }
        }
    }

    @Test
    fun scanButtonOpensViewfinderAndBackReturns() {
        openTable().use {
            onView(withId(R.id.buttonCapturePiece)).perform(click())
            waitFor { onView(withId(R.id.shutter)).check(matches(isDisplayed())) }
            onView(withId(R.id.previewView)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonClose)).perform(click())
            waitFor { onView(withId(R.id.groupIdle)).check(matches(isDisplayed())) }
        }
    }
}
