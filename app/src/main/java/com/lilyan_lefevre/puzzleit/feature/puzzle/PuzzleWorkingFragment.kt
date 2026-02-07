package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleWorkingBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.hypot
import kotlin.math.max

/**
 * Fragment for working on a puzzle project
 */
@AndroidEntryPoint
class PuzzleWorkingFragment : Fragment() {

    private var _binding: FragmentPuzzleWorkingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PuzzleWorkingViewModel by viewModels()
    private lateinit var projectId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get project ID from arguments
        projectId = arguments?.getString("projectId") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPuzzleWorkingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupMenu()
        observeViewModel()
        setupClickListeners()
        
        // Load project data
        viewModel.loadProject(projectId)
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_puzzle_working, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit -> {
                        navigateToEditProject()
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun navigateToEditProject() {
        // Pass projectId directly to the generated action method
        val action = PuzzleWorkingFragmentDirections.actionPuzzleWorkingFragmentToProjectCreationFragment(projectId)
        findNavController().navigate(action)
    }

    private fun showDeleteConfirmationDialog() {
        val projectName = viewModel.project.value?.name ?: "this project"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_project_title))
            .setMessage(getString(R.string.delete_project_message, projectName))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteProject()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteProject() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteProject()
            // Observe deletionSuccess in observeViewModel for navigation
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.project.collect { project ->
                        project?.let {
                            updateUI(it)
                            // Update ActionBar title with project name
                            (activity as? AppCompatActivity)?.supportActionBar?.title = it.name
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            // Show error message
                            android.widget.Toast.makeText(
                                requireContext(),
                                it,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            viewModel.clearError()
                        }
                    }
                }
                launch {
                    viewModel.deletionSuccess.collect { success ->
                        if (success) {
                            findNavController().popBackStack(R.id.projectListFragment, false)
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(project: Project) {
        binding.apply {
            // Update project info
            textNumberPieces.text = resources.getString(R.string.pieces_name, project.puzzleSize)
            textViewCreationDate.text = formatDate(project.creationDate)
            
            // Load the cropped image directly (no need to rectify again)
            val imagePathToLoad = project.thumbnailPath.ifEmpty { project.imagePath }
            
            Glide.with(requireContext())
                .load(imagePathToLoad)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .fitCenter()
                .into(photoPreview)
        }
    }

    private fun setupClickListeners() {
        binding.buttonCapturePiece.setOnClickListener {
            // TODO: Navigate to camera capture for piece analysis
            android.widget.Toast.makeText(
                requireContext(),
                "Camera capture coming in Epic 2",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
