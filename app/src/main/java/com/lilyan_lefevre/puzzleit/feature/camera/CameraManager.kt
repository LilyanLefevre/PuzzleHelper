package com.lilyan_lefevre.puzzleit.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.lilyan_lefevre.puzzleit.R
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera manager for handling camera operations
 * Provides reusable camera functionality across the app
 */
@Singleton
class CameraManager @Inject constructor() {

    companion object {
        private const val TAG = "CameraManager"
    }

    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null

    private val _isCameraReady = MutableStateFlow(false)

    private val _lastCapturedImagePath = MutableStateFlow<String?>(null)

    private var pendingPreviewView: androidx.camera.view.PreviewView? = null
    private var pendingFragment: Fragment? = null

    /**
     * Initialize camera manager with fragment
     */
    fun initialize(
        fragment: Fragment,
        onPermissionDenied: () -> Unit,
        onPermissionRationale: () -> Unit
    ) {
        setupPermissionLauncher(fragment, onPermissionDenied, onPermissionRationale)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * Setup permission launcher
     */
    private fun setupPermissionLauncher(
        fragment: Fragment,
        onPermissionDenied: () -> Unit,
        onPermissionRationale: () -> Unit
    ) {
        permissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startCamera(fragment)
            } else {
                // Check if user selected "Don't ask again"
                if (!fragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // User permanently denied - go to denied callback
                    onPermissionDenied()
                } else {
                    // User denied but can ask again - show rationale
                    onPermissionRationale()
                }
            }
        }
    }

    /**
     * Check and request camera permission
     */
    fun checkAndRequestPermission(
        fragment: Fragment,
        onPermissionRationale: () -> Unit
    ) {
        when {
            ContextCompat.checkSelfPermission(
                fragment.requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera(fragment)
            }
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // User has denied before but hasn't selected "Don't ask again"
                // Show rationale dialog
                onPermissionRationale()
            }
            else -> {
                // First time asking for permission
                permissionLauncher?.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Start camera with highest quality
     */
    private fun startCamera(fragment: Fragment) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(fragment.requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                _isCameraReady.value = true
                Log.d(TAG, "Camera provider initialized successfully")
                
                // If there's a pending preview view, bind it now
                pendingPreviewView?.let { previewView ->
                    pendingFragment?.let { frag ->
                        bindPreview(frag, previewView)
                        pendingPreviewView = null
                        pendingFragment = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider failed", e)
                _isCameraReady.value = false
            }
        }, ContextCompat.getMainExecutor(fragment.requireContext()))
    }

    /**
     * Bind camera preview with highest quality settings
     */
    fun bindPreview(
        fragment: Fragment,
        previewView: androidx.camera.view.PreviewView
    ) {
        // Ensure we're on main thread
        if (!isMainThread()) {
            fragment.requireActivity().runOnUiThread {
                bindPreview(fragment, previewView)
            }
            return
        }

        cameraProvider?.let { provider ->
            val preview = Preview.Builder().build()
            
            // Configure ImageCapture with highest quality for Computer Vision
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
                        )
                        .build()
                )
                .setJpegQuality(100)  // Maximum quality for CV
                .build()
                
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    fragment as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                this.imageCapture = imageCapture
                
                _isCameraReady.value = true
                
                Log.d(TAG, "Camera preview bound successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                _isCameraReady.value = false
            }
        } ?: run {
            Log.w(TAG, "Camera provider not initialized, storing pending request")
            // Store the request for when camera provider is ready
            pendingPreviewView = previewView
            pendingFragment = fragment
        }
    }

    /**
     * Check if we're on main thread
     */
    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * Take photo with highest quality
     */
    fun takePhoto(
        fragment: Fragment,
        onPhotoCaptured: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            onError("Camera not initialized")
            return
        }

        val photoFile = File(
            fragment.requireContext().cacheDir,
            "puzzle_${System.currentTimeMillis()}_original.jpg"
        )

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            cameraExecutor!!,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    // Switch to main thread for error callback
                    fragment.requireActivity().runOnUiThread {
                        onError(fragment.requireContext().getString(R.string.photo_capture_failed, exception.message ?: ""))
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: return
                    Log.d(TAG, "Photo saved: $savedUri")
                    
                    _lastCapturedImagePath.value = savedUri.path
                    
                    // Switch to main thread for success callback
                    fragment.requireActivity().runOnUiThread {
                        onPhotoCaptured(savedUri.path ?: "")
                    }
                }
            }
        )
    }

    /**
     * Stop and cleanup camera
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        _isCameraReady.value = false
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopCamera()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        cameraProvider = null
        imageCapture = null
        permissionLauncher = null
        pendingPreviewView = null
        pendingFragment = null
    }

    /**
     * Request camera permission directly (for use after rationale)
     */
    fun requestPermission() {
        permissionLauncher?.launch(Manifest.permission.CAMERA)
    }

    /**
     * Get current camera ready state
     */
    fun isReady(): Boolean = _isCameraReady.value
}
