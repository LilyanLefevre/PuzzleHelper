package com.lilyan_lefevre.puzzleit.e2e

import android.Manifest
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
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
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectRepository
import com.lilyan_lefevre.puzzleit.feature.puzzle.capture.PieceCaptureFragment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Plays the tour that the README video shows, with slow real swipes, on the first puzzle already on the device (read only).
 * The screen records itself into /sdcard/demo.mp4: `adb pull /sdcard/demo.mp4`. Skipped when the device has no puzzle.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DemoTourTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Inject lateinit var repository: ProjectRepository

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx = instrumentation.targetContext

    private fun shell(cmd: String): String =
        instrumentation.uiAutomation.executeShellCommand(cmd).use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().toString(Charsets.UTF_8) }

    private fun waitFor(timeoutMs: Long = 60_000, check: () -> Unit) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            try { check(); return } catch (e: Throwable) {
                if (System.currentTimeMillis() > end) throw e
                Thread.sleep(250)
            }
        }
    }

    /** A real swipe, as long as [ms] says: the sheet and the lists follow the finger frame by frame. */
    private fun swipe(x: Float, fromY: Float, toY: Float, ms: Int) {
        shell("input swipe ${x.toInt()} ${fromY.toInt()} ${x.toInt()} ${toY.toInt()} $ms")
    }

    /** The first view under [root] that satisfies [match], depth first. */
    private fun find(root: View, match: (View) -> Boolean): View? {
        if (root.visibility == View.VISIBLE && match(root)) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), match)?.let { return it }
        return null
    }

    /** A real tap at the centre of the view: Espresso's click refuses to run with animations on, and the video needs them. */
    private fun tap(scenario: ActivityScenario<MainActivity>, what: String, match: (View) -> Boolean) {
        var x = 0; var y = 0
        scenario.onActivity { act ->
            val v = checkNotNull(find(act.window.decorView, match)) { "view to tap not found: $what" }
            val loc = IntArray(2); v.getLocationOnScreen(loc)
            x = loc[0] + v.width / 2; y = loc[1] + v.height / 2
        }
        shell("input tap $x $y")
    }

    @Test
    fun tour() {
        hilt.inject()
        val project = runBlocking { repository.getAllProjects().first() }.firstOrNull { it.warpedPath.isNotEmpty() }
        assumeTrue("needs a puzzle on the device", project != null)
        project!!
        val art = BitmapFactory.decodeFile(project.warpedPath, BitmapFactory.Options().apply { inSampleSize = 2 })
        val cols = sqrt(project.puzzleSize * art.width.toFloat() / art.height).roundToInt()
        val rows = (project.puzzleSize / cols.toFloat()).roundToInt()
        val photo = TestImages.save(TestImages.piecePhoto(art, cols, rows, (cols * 0.4f).toInt(), (rows * 0.5f).toInt(), 90f), ctx.cacheDir, "demo_piece.jpg")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor { onView(withText(project.name)).check(matches(isDisplayed())) }
            val recorder = instrumentation.uiAutomation.executeShellCommand("screenrecord --size 720x1544 --bit-rate 14000000 /sdcard/demo.mp4")
            Thread.sleep(1500)
            tap(scenario, "project") { it is android.widget.TextView && it.text.toString() == project.name }
            waitFor { onView(withId(R.id.buttonCapturePiece)).check(matches(isDisplayed())) }
            Thread.sleep(2500)

            // The photo goes through the channel the camera screen uses; sent again until the matcher is ready.
            fun idle(): Boolean { var v = true; scenario.onActivity { v = it.findViewById<View>(R.id.groupIdle).visibility == View.VISIBLE }; return v }
            waitFor(60_000) {
                if (idle()) {
                    scenario.onActivity { a: FragmentActivity ->
                        val host = a.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
                        host.childFragmentManager.setFragmentResult(PieceCaptureFragment.RESULT_KEY, bundleOf(PieceCaptureFragment.PHOTO_PATH to photo.absolutePath))
                    }
                    Thread.sleep(300)
                }
                check(!idle()) { "the table did not start analysing" }
            }
            waitFor { onView(withId(R.id.groupResult)).check(matches(isDisplayed())) }
            Thread.sleep(3500)

            var width = 0f; var height = 0f
            scenario.onActivity { width = it.window.decorView.width.toFloat(); height = it.window.decorView.height.toFloat() }
            fun sheetTop(): Float {
                var y = 0f
                scenario.onActivity { val loc = IntArray(2); it.findViewById<View>(R.id.sheet).getLocationOnScreen(loc); y = loc[1] + 24 * it.resources.displayMetrics.density }
                return y
            }
            fun lead(n: Int) = tap(scenario, "lead $n") { it is android.widget.TextView && it.text.contains(ctx.getString(R.string.alt_lead, n)) && (it.parent as? View)?.id == R.id.chipLeads }

            lead(2); Thread.sleep(2500)
            lead(1); Thread.sleep(2000)
            swipe(width / 2, sheetTop(), 0.04f * height, 900); Thread.sleep(2000)    // full level: why these leads
            repeat(3) { swipe(width / 2, 0.8f * height, 0.35f * height, 900); Thread.sleep(1500) }
            repeat(3) { swipe(width / 2, 0.35f * height, 0.8f * height, 700); Thread.sleep(600) }
            Thread.sleep(800)
            scenario.onActivity { act -> act.findViewById<ViewGroup>(R.id.explainList).getChildAt(1).performClick() }   // a card leads to the map
            Thread.sleep(3000)
            swipe(width / 2, sheetTop(), 0.995f * height, 200); Thread.sleep(2500)    // a flick hides the sheet: the map is free
            tap(scenario, "pill") { it.id == R.id.pillLeads }; Thread.sleep(3000)

            shell("kill -2 ${shell("pidof screenrecord").trim()}")   // SIGINT: the recorder finishes the file
            Thread.sleep(2000)
            recorder.close()
        }
    }
}
