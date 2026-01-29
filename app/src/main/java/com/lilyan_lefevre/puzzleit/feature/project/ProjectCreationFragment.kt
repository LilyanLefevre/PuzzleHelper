package com.lilyan_lefevre.puzzleit.feature.project

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentProjectCreationBinding
import com.lilyan_lefevre.puzzleit.feature.camera.CameraManager
import com.lilyan_lefevre.puzzleit.feature.camera.CameraRepository
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment for project creation confirmation
 */
@AndroidEntryPoint
class ProjectCreationFragment : Fragment() {

    private var _binding: FragmentProjectCreationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()

    @Inject
    lateinit var cameraManager: CameraManager
    
    @Inject
    lateinit var cameraRepository: CameraRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectCreationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize camera manager
        cameraManager.initialize(
            fragment = this,
            onPermissionDenied = { showPermissionDeniedDialog() },
            onPermissionRationale = { showPermissionRationaleDialog() }
        )
        
        // Setup edge-to-edge for fragment
        setupEdgeToEdge()
        
        setupCamera()
        setupUI()
        observeViewModel()
    }

    private fun setupEdgeToEdge() {
        // Make status bar transparent for better visual effect
        requireActivity().window.statusBarColor = Color.TRANSPARENT
        requireActivity().window.navigationBarColor = Color.TRANSPARENT

        // Handle window insets for status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            // Apply padding to capture button to avoid navigation bar overlap
            binding.captureButton.updatePadding(
                bottom = systemBars.bottom + 32
            )

            // Consume the insets
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupCamera() {
        Log.d(TAG, "Setting up camera")
        
        // Check camera permissions using camera manager
        cameraManager.checkAndRequestPermission(
            fragment = this,
            onPermissionRationale = { showPermissionRationaleDialog() }
        )
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.camera_permission_required))
            .setMessage(getString(R.string.camera_permission_rationale))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                // Request permission directly - this will show the system permission dialog
                cameraManager.requestPermission()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                // User cancelled rationale, show denied dialog
                showPermissionDeniedDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.camera_permission_denied))
            .setMessage(getString(R.string.camera_permission_denied))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                // Navigate back to project list
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupUI() {
        binding.captureButton.setOnClickListener {
            takePhoto()
        }
    }

    override fun onResume() {
        super.onResume()
        // Bind camera preview when fragment is resumed and view is ready
        binding.previewView.post {
            // Always try to bind preview - CameraManager will handle the timing
            cameraManager.bindPreview(this, binding.previewView)
        }
    }

    private fun takePhoto() {
        if (!cameraManager.isReady()) {
            Toast.makeText(requireContext(), getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        
        cameraManager.takePhoto(
            fragment = this,
            onPhotoCaptured = { imagePath ->
                binding.progressBar.visibility = View.GONE
                processCapturedImage(imagePath)
            },
            onError = { error ->
                Log.e(TAG, "Photo capture failed: $error")
                binding.progressBar.visibility = View.GONE
                showErrorDialog(error)
            }
        )
    }

    private fun processCapturedImage(imagePath: String?) {
        imagePath?.let {
            // Switch to main thread before showing dialog
            requireActivity().runOnUiThread {
                showMetadataDialog(imagePath)
            }
        }
    }

    private fun showMetadataDialog(imagePath: String) {
        // Stop camera preview to freeze the captured image
        cameraManager.stopCamera()
        
        val dialog = ProjectMetadataDialog(
            context = requireContext(),
            onProjectCreated = { name, pieces ->
                createProjectWithMetadata(imagePath, name, pieces)
            },
            onDismiss = {
                // Restart camera when dialog is dismissed
                cameraManager.checkAndRequestPermission(
                    fragment = this,
                    onPermissionRationale = { showPermissionRationaleDialog() }
                )
            }
        )
        dialog.show()
    }

    private fun createProjectWithMetadata(imagePath: String, name: String, pieces: Int) {
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // Load bitmap from file
                val bitmap = BitmapFactory.decodeFile(imagePath)
                bitmap?.let {
                    // Create project with metadata
                    val puzzleSize = pieces
                    val difficulty = when {
                        pieces.toInt() < 500 -> "easy"
                        pieces.toInt() < 1500 -> "medium"
                        else -> "hard"
                    }
                    
                    // Save image with CameraRepository
                    val result = cameraRepository.saveCapturedImage(bitmap, imagePath)
                    
                    result.fold(
                        onSuccess = { storageResult ->
                            // Create project with metadata
                            viewModel.createProject(
                                name = name,
                                puzzleSize = puzzleSize,
                                difficulty = difficulty,
                                imagePath = storageResult.mainImagePath,
                                thumbnailPath = storageResult.thumbnailPath
                            )
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to save image", error)
                            binding.progressBar.visibility = View.GONE
                            showErrorDialog(error.message ?: getString(R.string.unknown_error))
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process image", e)
                binding.progressBar.visibility = View.GONE
                showErrorDialog(getString(R.string.failed_to_process_image, e.message ?: ""))
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.projectCreated.collect { project ->
                        project?.let {
                            // Show success and return to list immediately
                            showSuccessAndNavigate(project)
                            viewModel.clearProjectCreated()
                        }
                    }
                }

                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            showErrorDialog(it)
                            viewModel.clearError()
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        if (!isLoading) {
                            binding.progressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun showSuccessAndNavigate(project: Project) {
        Toast.makeText(requireContext(), "Project \"${project.name}\" created successfully!", Toast.LENGTH_SHORT).show()
        
        // Navigate back to project list immediately
        findNavController().navigateUp()
    }

    private fun resetUI() {
        // Reset progress bar
        binding.progressBar.visibility = View.GONE
    }

    private fun showErrorDialog(error: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.error))
            .setMessage(error)
            .setPositiveButton(getString(R.string.ok), null)
            .setCancelable(false)
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Stop camera when fragment is paused to save resources
        cameraManager.stopCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraManager.cleanup()
        _binding = null
    }

    companion object {
        private const val TAG = "ProjectCreationFragment"
    }
}
