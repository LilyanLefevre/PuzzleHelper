package com.lilyan_lefevre.puzzleit.feature.project

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentProjectListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment displaying list of all puzzle projects
 */
@AndroidEntryPoint
class ProjectListFragment : Fragment() {

    private var _binding: FragmentProjectListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter(
            onProjectClick = { project ->
                // Navigate to puzzle working screen using bundle
                val bundle = Bundle().apply {
                    putString("projectId", project.id)
                }
                findNavController().navigate(R.id.action_projectListFragment_to_puzzleWorkingFragment, bundle)
            },
            onProjectLongClick = { _ ->
                // Long click action removed as we now have a delete menu in the detail screen
            }
        )
        
        binding.recyclerViewProjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = projectAdapter
            // Cards fall into place the first time the list shows.
            scheduleLayoutAnimation()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.projects.collect { projects ->
                        projectAdapter.submitList(projects)
                        binding.emptyState.visibility =
                            if (projects.isEmpty()) View.VISIBLE else View.GONE
                        
                        // Hide loading when data is loaded
                        binding.progressBar.visibility = View.GONE
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
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAddProject.setOnClickListener {
            // Navigate to original project creation fragment using Navigation Component
            findNavController().navigate(R.id.action_projectListFragment_to_projectCreationFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
