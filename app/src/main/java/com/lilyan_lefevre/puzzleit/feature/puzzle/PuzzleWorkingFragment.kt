package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleWorkingBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
        
        setupToolbar()
        observeViewModel()
        setupClickListeners()
        
        // Load project data
        viewModel.loadProject(projectId)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.project.collect { project ->
                        project?.let { updateUI(it) }
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
                            // Navigate back on error
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(project: Project) {
        binding.apply {
            // Update toolbar title with project name
            toolbar.title = project.name
            
            // Update project info
            val piecesStr = resources.getString(R.string.pieces_name, project.puzzleSize)
            textViewProjectInfo.text = "$piecesStr • Created ${formatDate(project.creationDate)}"
            
            // Load reference image
            Glide.with(requireContext())
                .load(project.imagePath)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .fitCenter()
                .into(imageViewReference)
        }
    }

    private fun setupClickListeners() {
        binding.buttonCapturePiece.setOnClickListener {
            // TODO: Navigate to camera capture for piece analysis
            // This will be implemented in Epic 2 stories
            android.widget.Toast.makeText(
                requireContext(),
                "Camera capture coming in Epic 2",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
