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
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentProjectListBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
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
            onProjectLongClick = { project ->
                showDeleteConfirmationDialog(project)
            }
        )
        
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
                            // Show error message
                            Snackbar.make(
                                binding.root,
                                it,
                                Snackbar.LENGTH_LONG
                            ).show()
                            viewModel.clearError()
                        }
                    }
                }
                launch {
                    viewModel.projectDeleted.collect { projectName ->
                        projectName?.let {
                            // Show success message
                            Snackbar.make(
                                binding.root,
                                getString(R.string.project_deleted_successfully, it),
                                Snackbar.LENGTH_SHORT
                            ).show()
                            viewModel.clearProjectDeleted()
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

    private fun showDeleteConfirmationDialog(project: Project) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_project_title))
            .setMessage(getString(R.string.delete_project_message, project.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteProject(project.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
