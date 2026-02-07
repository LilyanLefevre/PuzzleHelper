package com.lilyan_lefevre.puzzleit.feature.project

import android.app.Activity
import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentProjectCreationBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/**
 * Fragment for project creation with form and system camera integration
 */
@AndroidEntryPoint
class ProjectCreationFragment : Fragment() {

    private var _binding: FragmentProjectCreationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()

    private var originalImagePath: String? = null // Store original image path
    private var croppedImagePath: String? = null // Store cropped image path
    private var currentQuadJson: String? = null
    private var photoFile: File? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoFile?.let { file ->
                originalImagePath = file.absolutePath
                currentQuadJson = createDefaultQuad()
                // Navigate to puzzle bounds for cropping immediately after taking the photo
                navigateToPuzzleBounds(originalImagePath!!)
            }
        } else {
            // User cancelled photo taking, clear paths and navigate back if needed
            originalImagePath = null
            croppedImagePath = null
            currentQuadJson = null
            // No specific navigation needed here, user can decide to take photo again or navigate up
            // findNavController().navigateUp() // Optionally go back if photo is mandatory
        }
    }

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
        
        setupUI()
        observeViewModel()
        listenForPuzzleBoundsResult()
    }

    private fun setupUI() {
        binding.photoPlaceholder.setOnClickListener {
            dispatchTakePictureIntent()
        }
        
        binding.editPhotoButton.setOnClickListener {
            // Always use the original image for re-editing bounds
            originalImagePath?.let { imagePath ->
                navigateToPuzzleBounds(imagePath)
            }
        }
        
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnCreate.setOnClickListener {
            createProject()
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            photoFile = try {
                createImageFile()
            } catch (ex: Exception) {
                Log.e(TAG, "Error creating photo file", ex)
                null
            }
            
            photoFile?.also { file ->
                val photoURI = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureLauncher.launch(takePictureIntent)
            }
        }
    }

    private fun createImageFile(): File {
        val storageDir = requireContext().filesDir
        return File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            storageDir
        )
    }

    private fun navigateToPuzzleBounds(imagePath: String) {
        val action = ProjectCreationFragmentDirections.actionProjectCreationFragmentToPuzzleBoundsFragment(imagePath)
        findNavController().navigate(action)
    }

    private fun createDefaultQuad(): String {
        val defaultQuad = listOf(
            PointF(0.2f, 0.2f),
            PointF(0.8f, 0.2f),
            PointF(0.8f, 0.8f),
            PointF(0.2f, 0.8f)
        )
        val arr = JSONArray()
        for (pt in defaultQuad) {
            val p = JSONArray()
            p.put(pt.x.toDouble())
            p.put(pt.y.toDouble())
            arr.put(p)
        }
        return arr.toString()
    }

    private fun createProject() {
        val name = binding.editTextName.text.toString().trim()
        val pieces = try {
            binding.editTextPieces.text.toString().toInt()
        } catch (e: NumberFormatException) {
            null
        }

        when {
            originalImagePath == null -> {
                Toast.makeText(requireContext(), "Please take a photo first", Toast.LENGTH_SHORT).show()
            }
            name.isEmpty() -> {
                Toast.makeText(requireContext(), getString(R.string.please_enter_project_name), Toast.LENGTH_SHORT).show()
            }
            pieces == null -> {
                Toast.makeText(requireContext(), getString(R.string.please_enter_number_of_pieces), Toast.LENGTH_SHORT).show()
            }
            else -> {
                val finalImagePath = croppedImagePath ?: originalImagePath // Use cropped if available, else original
                viewModel.createProject(
                    imagePath = finalImagePath!!,
                    thumbnailPath = finalImagePath!!, // Thumbnail will also be the cropped/original image
                    name = name,
                    puzzleSize = pieces,
                    difficulty = "medium",
                    puzzleQuad = currentQuadJson
                )
            }
        }
    }

    private fun listenForPuzzleBoundsResult() {
        setFragmentResultListener("puzzleBoundsResult") { _, bundle ->
            val quadJson = bundle.getString("quadJson")
            val croppedPath = bundle.getString("croppedImagePath")
            val cancelled = bundle.getBoolean("cancelled", false)
            
            if (cancelled) {
                // User cancelled from PuzzleBoundsFragment, clear any pending image
                originalImagePath = null
                croppedImagePath = null
                currentQuadJson = null
                // Optionally, reset UI to show photo placeholder
                binding.photoPlaceholder.visibility = View.VISIBLE
                binding.photoPreview.visibility = View.GONE
                binding.editPhotoButton.visibility = View.GONE
            } else if (quadJson != null) {
                currentQuadJson = quadJson
                croppedImagePath = croppedPath // Store the new cropped path
                showPhotoPreview()
            }
        }
    }

    private fun showPhotoPreview() {
        val imagePathToShow = croppedImagePath ?: originalImagePath ?: return
        
        binding.photoPlaceholder.visibility = View.GONE
        binding.photoPreview.visibility = View.VISIBLE
        binding.editPhotoButton.visibility = View.VISIBLE
        
        com.bumptech.glide.Glide.with(requireContext())
            .load(imagePathToShow)
            .placeholder(android.R.drawable.ic_menu_camera)
            .error(android.R.drawable.ic_menu_camera)
            .fitCenter()
            .into(binding.photoPreview)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.projectCreated.collect { project ->
                        project?.let {
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

                launch {
                    viewModel.isProcessing.collect { isProcessing ->
                        binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showSuccessAndNavigate(project: Project) {
        Toast.makeText(requireContext(), getString(R.string.project_created_successfully, project.name), Toast.LENGTH_SHORT).show()

        originalImagePath = null
        croppedImagePath = null
        currentQuadJson = null
        
        findNavController().navigateUp()
    }

    private fun showErrorDialog(error: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.error))
            .setMessage(error)
            .setPositiveButton(getString(R.string.ok), null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ProjectCreationFragment"
    }
}
