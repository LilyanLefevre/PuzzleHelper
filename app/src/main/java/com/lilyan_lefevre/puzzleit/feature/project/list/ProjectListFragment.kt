package com.lilyan_lefevre.puzzleit.feature.project.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val viewModel: ProjectListViewModel by viewModels()
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
                findNavController().navigate(R.id.action_projectListFragment_to_puzzleWorkingFragment, bundleOf("projectId" to project.id))
            },
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
                viewModel.projects.collect { projects ->
                    binding.progressBar.visibility = if (projects == null) View.VISIBLE else View.GONE
                    projects ?: return@collect
                    projectAdapter.submitList(projects)
                    binding.emptyState.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonAccount.setOnClickListener { findNavController().navigate(R.id.action_projectListFragment_to_accountFragment) }
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
