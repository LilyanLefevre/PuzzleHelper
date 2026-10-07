package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import com.lilyan_lefevre.puzzleit.databinding.FragmentPieceCaptureBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Takes the photo of one loose piece and hands the file back to the working screen. */
class PieceCaptureFragment : Fragment() {

    private var _binding: FragmentPieceCaptureBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var torch = false
    private lateinit var executor: ExecutorService

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(requireContext(), com.lilyan_lefevre.puzzleit.R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPieceCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        executor = Executors.newSingleThreadExecutor()
        binding.buttonClose.setOnClickListener { findNavController().navigateUp() }
        binding.buttonTorch.setOnClickListener {
            torch = !torch
            camera?.cameraControl?.enableTorch(torch)
            binding.buttonTorch.alpha = if (torch) 1f else 0.6f
        }
        binding.buttonTorch.alpha = 0.6f
        binding.shutter.setOnClickListener { takePhoto() }
        binding.shutter.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            // The provider can come back after the user already left this screen.
            val b = _binding ?: return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(b.previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        binding.shutter.isEnabled = false
        val file = File(requireContext().cacheDir, "piece_${System.currentTimeMillis()}.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(e: ImageCaptureException) {
                Log.e(TAG, "Capture failed", e)
                activity?.runOnUiThread { _binding?.shutter?.isEnabled = true }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                activity?.runOnUiThread {
                    val b = _binding ?: return@runOnUiThread
                    // Shutter flash, then back to the table where the analysis starts.
                    b.flash.alpha = 0.9f
                    b.flash.animate().alpha(0f).setDuration(260).withEndAction {
                        if (_binding == null) return@withEndAction
                        setFragmentResult(RESULT_KEY, bundleOf(PHOTO_PATH to file.absolutePath))
                        findNavController().navigateUp()
                    }.start()
                }
            }
        })
    }

    override fun onDestroyView() {
        camera?.cameraControl?.enableTorch(false)
        executor.shutdown()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "PieceCapture"
        const val RESULT_KEY = "pieceCaptureResult"
        const val PHOTO_PATH = "photoPath"
    }
}
