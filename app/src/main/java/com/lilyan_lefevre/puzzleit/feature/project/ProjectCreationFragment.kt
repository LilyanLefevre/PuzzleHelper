package com.lilyan_lefevre.puzzleit.feature.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
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

/**
 * Fragment for project creation with inline camera and form
 */
@AndroidEntryPoint
class ProjectCreationFragment : Fragment() {

    private var _binding: FragmentProjectCreationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()

    private var currentImagePath: String? = null
    private var currentQuadJson: String? = null

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
        listenForCameraResult()
        listenForPuzzleBoundsResult()
    }

    private fun setupUI() {
        binding.photoPlaceholder.setOnClickListener {
            navigateToFullScreenCamera()
        }
        
        binding.editPhotoButton.setOnClickListener {
            currentImagePath?.let { imagePath ->
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

    private fun navigateToFullScreenCamera() {
        val action = ProjectCreationFragmentDirections.actionProjectCreationFragmentToFullScreenCameraFragment()
        findNavController().navigate(action)
    }

    private fun listenForCameraResult() {
        setFragmentResultListener("fullScreenCameraResult") { _, bundle ->
            val imagePath = bundle.getString("imagePath")
            if (imagePath != null) {
                currentImagePath = imagePath
                currentQuadJson = createDefaultQuad()
                // Don't show photo preview yet - wait for puzzle bounds result
            }
        }
    }

    private fun navigateToPuzzleBounds(imagePath: String) {
        // Navigate to PuzzleBoundsFragment for bounds selection
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
            currentImagePath == null -> {
                Toast.makeText(requireContext(), "Please take a photo first", Toast.LENGTH_SHORT).show()
            }
            name.isEmpty() -> {
                Toast.makeText(requireContext(), getString(R.string.please_enter_project_name), Toast.LENGTH_SHORT).show()
            }
            pieces == null -> {
                Toast.makeText(requireContext(), getString(R.string.please_enter_number_of_pieces), Toast.LENGTH_SHORT).show()
            }
            else -> {
                viewModel.createProject(
                    imagePath = currentImagePath!!, // This is now the cropped image
                    thumbnailPath = currentImagePath!!, // Use same cropped image for thumbnail
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
            val croppedImagePath = bundle.getString("croppedImagePath")
            
            if (quadJson != null) {
                currentQuadJson = quadJson
            }
            
            // Use cropped image if available, otherwise use original
            if (croppedImagePath != null) {
                currentImagePath = croppedImagePath
            }
            
            // Show the photo preview
            showPhotoPreview()
        }
    }

    private fun showPhotoPreview() {
        val imagePath = currentImagePath ?: return
        
        // Hide placeholder, show photo preview
        binding.photoPlaceholder.visibility = View.GONE
        binding.photoPreview.visibility = View.VISIBLE
        binding.editPhotoButton.visibility = View.VISIBLE
        
        // Load the cropped image directly with Glide (no need for manual rotation correction)
        // The image is already cropped and corrected in PuzzleBoundsFragment
        com.bumptech.glide.Glide.with(requireContext())
            .load(imagePath)
            .placeholder(android.R.drawable.ic_menu_camera)
            .error(android.R.drawable.ic_menu_camera)
            .fitCenter()
            .into(binding.photoPreview)
    }

    

    private fun quadToJsonString(points: List<PointF>): String {
        val arr = JSONArray()
        for (pt in points) {
            val p = JSONArray()
            p.put(pt.x.toDouble())
            p.put(pt.y.toDouble())
            arr.put(p)
        }
        return arr.toString()
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

        currentQuadJson = null
        currentImagePath = null
        
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ProjectCreationFragment"
    }
}
