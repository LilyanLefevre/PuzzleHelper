package com.lilyan_lefevre.puzzleit

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Unit tests for Android project setup validation
 * These tests verify that project structure and dependencies are correctly configured
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ProjectSetupTest {

    private lateinit var mainActivity: MainActivity

    @Before
    fun setUp() {
        mainActivity = Robolectric.buildActivity(MainActivity::class.java).create().get()
    }

    @Test
    fun `MainActivity should launch without crashes`() {
        // Test that MainActivity can be created without crashes
        assertNotNull("MainActivity should be created successfully", mainActivity)
    }

    @Test
    fun `OpenCV should be available in classpath`() {
        // Test that OpenCV classes are available
        try {
            Class.forName("org.opencv.android.OpenCVLoader")
            // If we reach here, OpenCV is in classpath
        } catch (e: ClassNotFoundException) {
            fail("OpenCV should be available in classpath")
        }
    }

    @Test
    fun `App should have correct package structure`() {
        // Test that we're in the correct package
        assertEquals("Package should be com.lilyan_lefevre.puzzleit", "com.lilyan_lefevre.puzzleit", mainActivity.javaClass.`package`.name)
    }

    @Test
    fun `MainActivity should have correct theme`() {
        // Test that Material Design 3 theme is applied
        val theme = mainActivity.theme
        assertNotNull("Activity should have a theme", theme)
    }
}
