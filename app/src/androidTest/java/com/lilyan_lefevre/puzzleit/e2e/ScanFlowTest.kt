package com.lilyan_lefevre.puzzleit.e2e

import android.Manifest
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
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
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

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

    private fun waitFor(timeoutMs: Long = 60_000, check: () -> Unit) {
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

    /**
     * The photo goes through the channel the camera screen uses. The table ignores it until its matcher is prepared, so it is
     * sent again until the screen leaves the idle state (a person needs seconds to take a photo; the test needs milliseconds).
     */
    private fun inject(scenario: ActivityScenario<MainActivity>, photo: File) {
        fun idle(): Boolean { var v = true; scenario.onActivity { v = it.findViewById<View>(R.id.groupIdle).visibility == View.VISIBLE }; return v }
        waitFor(30_000) {
            if (idle()) {
                scenario.onActivity { a: FragmentActivity ->
                    val host = a.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
                    host.childFragmentManager.setFragmentResult(PieceCaptureFragment.RESULT_KEY, bundleOf(PieceCaptureFragment.PHOTO_PATH to photo.absolutePath))
                }
                Thread.sleep(300)
            }
            check(!idle()) { "the table did not start analysing" }
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
            waitFor { onView(withId(R.id.textViewProjectInfo)).check(matches(withText(containsString("${cols * rows}")))) }
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
            onView(withId(R.id.chipLeads)).check(matches(isDisplayed()))
            onView(allOf(withText(containsString(ctx.getString(R.string.alt_lead, 2))), isDescendantOfA(withId(R.id.chipLeads)))).perform(click())
            waitFor { onView(withId(R.id.textLead)).check(matches(withText(containsString(ctx.getString(R.string.alt_lead, 2))))) }
            Thread.sleep(1200); shot(scenario, "4_second_lead")
            onView(withId(R.id.buttonDismiss)).perform(scrollTo(), click())
            waitFor { onView(withId(R.id.groupIdle)).check(matches(isDisplayed())) }
        }
    }

    /** A real swipe from [fromY] to [toY] (screen pixels) through the input system, like a finger on the sheet. */
    private fun drag(scenario: ActivityScenario<MainActivity>, fromY: Float, toY: Float) {
        var x = 0
        scenario.onActivity { x = it.window.decorView.width / 2 }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input swipe $x ${fromY.toInt()} $x ${toY.toInt()} 300").close()
    }

    private fun screenHeight(scenario: ActivityScenario<MainActivity>): Float {
        var h = 0f
        scenario.onActivity { h = it.window.decorView.height.toFloat() }
        return h
    }

    private fun sheetTop(scenario: ActivityScenario<MainActivity>): Float {
        var y = 0f
        scenario.onActivity {
            val loc = IntArray(2); it.findViewById<View>(R.id.sheet).getLocationOnScreen(loc)
            y = loc[1] + 24 * it.resources.displayMetrics.density
        }
        return y
    }

    @Test
    fun resultSheetSlidesFromPeekToFullAndAwayAndBack() {
        openTable().use { scenario ->
            inject(scenario, TestImages.save(TestImages.piecePhoto(art, cols, rows, 9, 6, 90f), ctx.cacheDir, "e2e_piece2.jpg"))
            waitFor { onView(withId(R.id.groupResult)).check(matches(isDisplayed())) }
            Thread.sleep(1800)
            onView(withId(R.id.textSpot)).check(matches(isDisplayed()))               // half level: the comparison is visible
            // Up to the full level: the page that explains the leads.
            shot(scenario, "5_half")
            drag(scenario, sheetTop(scenario), 0.04f * screenHeight(scenario)); Thread.sleep(700)
            shot(scenario, "6_after_drag_up")
            // On a small screen the explanation page starts below the fold: scroll down to it.
            scenario.onActivity { act -> act.findViewById<androidx.core.widget.NestedScrollView>(R.id.sheetScroll).fullScroll(View.FOCUS_DOWN) }
            Thread.sleep(400)
            onView(withId(R.id.groupExplain)).check(matches(isDisplayed())); shot(scenario, "6_explain")
            // Freeze the first card's blinking and bring it into view: the piece must sit on the box where the lead says.
            scenario.onActivity { act ->
                act.findViewById<ViewGroup>(R.id.explainList).getChildAt(0).findViewById<View>(R.id.leadCompare).performClick()
                act.findViewById<androidx.core.widget.NestedScrollView>(R.id.sheetScroll).scrollTo(0, 1150)
            }
            Thread.sleep(500); shot(scenario, "6b_explain_overlay")
            // All the way down: the last card must be whole above the bottom edge of the screen.
            scenario.onActivity { act -> act.findViewById<androidx.core.widget.NestedScrollView>(R.id.sheetScroll).fullScroll(View.FOCUS_DOWN) }
            Thread.sleep(500); shot(scenario, "6c_explain_bottom")
            scenario.onActivity { act -> act.findViewById<androidx.core.widget.NestedScrollView>(R.id.sheetScroll).scrollTo(0, 0) }
            Thread.sleep(300)
            scenario.onActivity { act -> act.findViewById<androidx.core.widget.NestedScrollView>(R.id.sheetScroll).scrollTo(0, 0) }
            Thread.sleep(300)
            // Away: a long swipe down takes the sheet off whatever the screen's size, the map is free and the button brings the leads back.
            // (The peek level is checked by the card test; on a small screen the half and full levels nearly coincide.)
            // The swipe is a shell command: on a slow CI emulator it can start while the sheet still moves, so it is repeated.
            waitFor {
                if (runCatching { onView(withId(R.id.pillLeads)).check(matches(isDisplayed())) }.isFailure) {
                    drag(scenario, sheetTop(scenario), 0.98f * screenHeight(scenario)); Thread.sleep(1500)
                }
                onView(withId(R.id.pillLeads)).check(matches(isDisplayed()))
            }
            shot(scenario, "8_hidden")
            onView(withId(R.id.pillLeads)).perform(click()); Thread.sleep(700)
            onView(withId(R.id.textSpot)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun aLeadCardLeadsToThePuzzleWithThePieceOverIt() {
        openTable().use { scenario ->
            inject(scenario, TestImages.save(TestImages.piecePhoto(art, cols, rows, 9, 6, 90f), ctx.cacheDir, "e2e_piece3.jpg"))
            waitFor { onView(withId(R.id.groupResult)).check(matches(isDisplayed())) }
            Thread.sleep(1800)
            drag(scenario, sheetTop(scenario), 0.04f * screenHeight(scenario)); Thread.sleep(700)             // the page that explains the leads
            // Tapping the second lead's card selects it and drops the sheet to its peek level, over the map.
            scenario.onActivity { act -> act.findViewById<ViewGroup>(R.id.explainList).getChildAt(1).performClick() }
            Thread.sleep(1500)
            onView(allOf(withText(containsString(ctx.getString(R.string.alt_lead, 2))), isDescendantOfA(withId(R.id.chipLeads)))).check(matches(isChecked()))
            onView(withId(R.id.buttonOverlay)).check(matches(isDisplayed()))
            onView(withId(R.id.mapView)).check(matches(isDisplayed()))
            shot(scenario, "9_card_to_map"); Thread.sleep(900); shot(scenario, "9b_card_to_map")
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
    fun aScanIsRatedAndKeptInTheHistory() {
        openTable().use { scenario ->
            inject(scenario, TestImages.save(TestImages.piecePhoto(art, cols, rows, 9, 6, 90f), ctx.cacheDir, "e2e_piece4.jpg"))
            waitFor { onView(withId(R.id.groupResult)).check(matches(isDisplayed())) }
            Thread.sleep(1800)
            onView(withId(R.id.buttonRight)).perform(scrollTo(), click())
            onView(withId(R.id.textEvaluate)).check(matches(withText(R.string.evaluate_thanks)))
            Thread.sleep(500)                                                                  // the verdict is written in the background
            onView(withId(R.id.buttonMore)).perform(click())
            onView(withText(R.string.menu_history)).inRoot(isPlatformPopup()).perform(click())
            waitFor { onView(withText(R.string.verdict_correct)).check(matches(isDisplayed())) }
            shot(scenario, "10_history")
        }
    }

    @Test
    fun accountScreenOpensFromTheListAndRefusesABadAddress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor { onView(withId(R.id.buttonAccount)).check(matches(isDisplayed())) }
            onView(withId(R.id.buttonAccount)).perform(click())
            waitFor { onView(withId(R.id.editServer)).check(matches(isDisplayed())) }
            onView(withId(R.id.editServer)).perform(replaceText("not-an-address"), closeSoftKeyboard())
            onView(withId(R.id.buttonSignIn)).perform(scrollTo(), click())               // below the fold on a small screen
            onView(withId(R.id.textNotice)).check(matches(withText(R.string.server_invalid)))
            shot(scenario, "12_account")
        }
    }

    @Test
    fun progressScreenOpensFromTheMenu() {
        openTable().use { scenario ->
            onView(withId(R.id.buttonMore)).perform(click())
            onView(withText(R.string.menu_progress)).inRoot(isPlatformPopup()).perform(click())
            waitFor { onView(withId(R.id.buttonAddPhoto)).check(matches(isDisplayed())) }
            onView(withId(R.id.textEmpty)).check(matches(isDisplayed()))
            shot(scenario, "11_progress")
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

    @Test
    fun editFromTheTableOpensThePrefilledForm() {
        openTable().use {
            onView(withId(R.id.buttonEdit)).perform(click())
            waitFor { onView(withId(R.id.editTextName)).check(matches(withText(name))) }
            onView(withId(R.id.editTextPieces)).check(matches(withText("${cols * rows}")))
        }
    }

    @Test
    fun deleteFromTableAsksThenRemovesTheProject() {
        openTable().use {
            onView(withId(R.id.buttonDelete)).perform(click())
            onView(withText(R.string.delete)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            waitFor { onView(withId(R.id.fabAddProject)).check(matches(isDisplayed())) }
            waitFor { onView(withText(name)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist()) }
            assertNull(runBlocking { repository.getProjectById(id) })
        }
    }
}
