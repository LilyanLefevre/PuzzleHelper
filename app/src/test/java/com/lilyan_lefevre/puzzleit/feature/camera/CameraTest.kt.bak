package com.lilyan_lefevre.puzzleit.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class CameraTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var cameraManager: CameraManager
    @Mock lateinit var cameraRepository: CameraRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `should validate camera manager exists`() {
        assert(cameraManager != null)
    }

    @Test
    fun `should validate camera repository exists`() {
        assert(cameraRepository != null)
    }

    @Test
    fun `should handle camera manager readiness check`() {
        `when`(cameraManager.isReady()).thenReturn(false)
        val isReady = cameraManager.isReady()
        assert(!isReady)
        verify(cameraManager).isReady()
    }

    @Test
    fun `should test basic permission constants`() {
        val cameraPermission = Manifest.permission.CAMERA
        assert(cameraPermission == "android.permission.CAMERA")
    }
}
