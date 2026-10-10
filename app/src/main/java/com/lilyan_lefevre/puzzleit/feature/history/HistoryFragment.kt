package com.lilyan_lefevre.puzzleit.feature.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentHistoryBinding
import com.lilyan_lefevre.puzzleit.feature.history.data.Verdict
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** The pieces scanned on a puzzle, with the verdict the person gave on each. */
@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonBack.setOnClickListener { findNavController().navigateUp() }
        val adapter = ScanRecordAdapter { record ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_scan_title)
                .setMessage(R.string.delete_scan_message)
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(record) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.recyclerScans.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerScans.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.scans.collect { scans ->
                        adapter.submitList(scans)
                        binding.textEmpty.isVisible = scans.isEmpty()
                        binding.textSummary.text = resources.getQuantityString(R.plurals.history_summary, scans.size, scans.size, scans.count { it.verdict == Verdict.CORRECT })
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
