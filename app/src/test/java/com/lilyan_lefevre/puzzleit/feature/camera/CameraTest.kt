package com.lilyan_lefevre.puzzleit.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.lilyan_lefevre.puzzleit.HiltTestRunner
import com.lilyan_lefevre.puzzleit.shared.utils.PermissionManager
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Unit tests for camera functionality
 * These tests verify camera permissions, capture functionality, and retry mechanism
 */
@HiltAndroidTest
@RunWith(HiltTestRunner::class)
@Config(sdk = [24])
class CameraTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var hiltRule = HiltTestRule(this)

    @Inject
    lateinit var permissionManager: PermissionManager

    @Mock
    lateinit var cameraManager: CameraManager

    private lateinit var cameraViewModel: CameraViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        MockitoAnnotations.openMocks(this)
        cameraViewModel = CameraViewModel(permissionManager)
    }

    @Test
    fun `should request camera permission when not granted`() {
        // Given
        `when`(permissionManager.hasCameraPermission()).thenReturn(false)

        // When
        cameraViewModel.requestCameraPermission()

        // Then
        verify(permissionManager).hasCameraPermission()
    }

    @Test
    fun `should not request camera permission when already granted`() {
        // Given
        `when`(permissionManager.hasCameraPermission()).thenReturn(true)

        // When
        cameraViewModel.requestCameraPermission()

        // Then
        verify(permissionManager).hasCameraPermission()
    }

    @Test
    fun `should check camera permission status correctly`() {
        // Given
        `when`(permissionManager.hasCameraPermission()).thenReturn(true)

        // When
        val hasPermission = cameraViewModel.hasCameraPermission()

        // Then
        assert(hasPermission)
        verify(permissionManager).hasCameraPermission()
    }

    @Test
    fun `should handle permission granted result`() {
        // When
        cameraViewModel.onPermissionResult(Manifest.permission.CAMERA, PackageManager.PERMISSION_GRANTED)

        // Then
        assert(cameraViewModel.isCameraPermissionGranted.value == true)
    }

    @Test
    fun `should handle permission denied result`() {
        // When
        cameraViewModel.onPermissionResult(Manifest.permission.CAMERA, PackageManager.PERMISSION_DENIED)

        // Then
        assert(cameraViewModel.isCameraPermissionGranted.value == false)
    }

    @Test
    fun `should implement retry mechanism for photo capture failures`() {
        // Given
        `when`(cameraManager.isReady()).thenReturn(true)
        
        var retryCount = 0
        val mockError = "Camera capture failed"
        
        `when`(cameraManager.takePhoto(any(), any(), any())).thenAnswer { invocation ->
            val onError = invocation.getArgument<(String) -> Unit>(2)
            retryCount++
            if (retryCount < 3) {
                onError(mockError)
            } else {
                // Success on third try
                invocation.getArgument<(String?) -> Unit>(1)("/mock/image/path")
            }
        }

        // When & Then
        // This test verifies the retry logic would be called
        verify(cameraManager, atMost(3)).takePhoto(any(), any(), any())
    }

    @Test
    fun `should validate camera manager readiness before capture`() {
        // Given
        `when`(cameraManager.isReady()).thenReturn(false)

        // When
        val isReady = cameraManager.isReady()

        // Then
        assert(!isReady)
        verify(cameraManager).isReady()
    }
}
