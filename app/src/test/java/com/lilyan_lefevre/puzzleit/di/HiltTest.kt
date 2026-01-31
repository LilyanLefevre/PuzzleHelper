package com.lilyan_lefevre.puzzleit.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Basic test to verify Android context is available
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class HiltTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `should provide android context successfully`() {
        // Verify that Android context is available for testing
        assert(context != null)
        assert(context is Context)
    }
}
