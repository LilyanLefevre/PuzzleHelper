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
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentProjectListBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment displaying list of all puzzle projects
 */
@AndroidEntryPoint
class ProjectListFragment : Fragment() {

    private var _binding: FragmentProjectListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private lateinit var projectAdapter: ProjectAdapter

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
        projectAdapter = ProjectAdapter { project ->
            // Navigate to project details - will be implemented in story 1-4
            // For now, show a toast with project info
            android.widget.Toast.makeText(
                requireContext(),
                "Selected: ${project.name}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        
        binding.recyclerViewProjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = projectAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.projects.collect { projects ->
                        projectAdapter.submitList(projects)
                        binding.textViewEmpty.visibility =
                            if (projects.isEmpty()) View.VISIBLE else View.GONE
                        
                        // Hide loading when data is loaded
                        binding.progressBar.visibility = View.GONE
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            // Show error
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAddProject.setOnClickListener {
            // Navigate to project creation fragment using Navigation Component
            findNavController().navigate(R.id.action_projectListFragment_to_projectCreationFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
