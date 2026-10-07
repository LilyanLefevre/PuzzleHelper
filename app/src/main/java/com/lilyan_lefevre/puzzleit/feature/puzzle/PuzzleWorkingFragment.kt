package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.chip.Chip
import com.google.android.material.transition.MaterialSharedAxis
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleWorkingBinding
import com.lilyan_lefevre.puzzleit.feature.recognition.Candidate
import com.lilyan_lefevre.puzzleit.feature.recognition.Grid
import com.lilyan_lefevre.puzzleit.feature.recognition.PieceMatcher
import com.lilyan_lefevre.puzzleit.feature.recognition.Precision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** The puzzle table: box image on top, scan / result sheet below. */
@AndroidEntryPoint
class PuzzleWorkingFragment : Fragment() {

    private var _binding: FragmentPuzzleWorkingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PuzzleWorkingViewModel by viewModels()
    private lateinit var projectId: String
    private var grid: Grid? = null
    private var shownState: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectId = arguments?.getString("projectId") ?: ""
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPuzzleWorkingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonBack.setOnClickListener { findNavController().navigateUp() }
        val capture = View.OnClickListener { findNavController().navigate(R.id.action_puzzleWorkingFragment_to_pieceCaptureFragment) }
        binding.buttonCapturePiece.setOnClickListener(capture)
        binding.buttonNewPiece.setOnClickListener(capture)
        binding.buttonRetake.setOnClickListener(capture)
        binding.buttonDismiss.setOnClickListener { viewModel.dismiss() }
        binding.buttonErrorDismiss.setOnClickListener { viewModel.dismiss() }

        setFragmentResultListener(PieceCaptureFragment.RESULT_KEY) { _, b ->
            b.getString(PieceCaptureFragment.PHOTO_PATH)?.let(viewModel::analyze)
        }

        // The map must stay clear of the floating top bar and of the sheet, whatever its height.
        val sync = Runnable {
            _binding?.let { it.mapView.setViewportInsets(it.topBar.bottom, it.root.height - it.sheet.top) }
        }
        binding.sheet.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.removeCallbacks(sync); v.postDelayed(sync, 90) }

        observe()
        viewModel.loadProject(projectId)
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.reference.collect { r -> r?.let { (bmp, g) -> grid = g; binding.mapView.setImage(bmp, g) } } }
                launch { viewModel.project.collect { p -> p?.let { bindProject(it.name, it.puzzleSize) } } }
                launch { viewModel.isLoading.collect { binding.progressBar.isVisible = it } }
                launch { viewModel.scan.collect(::render) }
                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun bindProject(name: String, pieces: Int) {
        binding.textViewProjectName.text = name
        val g = grid
        binding.textViewProjectInfo.text =
            if (g != null) getString(R.string.grid_info, pieces, g.cols, g.rows) else getString(R.string.pieces_name, pieces)
    }

    // ---------------------------------------------------------------- states

    private fun render(state: ScanState) {
        // Re-collecting after a configuration change must not replay the camera move.
        val prev = shownState
        val first = prev == null
        if (state == prev) return
        shownState = state
        if (!first) TransitionManager.beginDelayedTransition(binding.sheet, AutoTransition().setDuration(300))

        binding.groupIdle.isVisible = state is ScanState.Idle
        binding.groupAnalyzing.isVisible = state is ScanState.Analyzing
        binding.groupResult.isVisible = state is ScanState.Result
        binding.groupError.isVisible = state is ScanState.Blurry || state is ScanState.NoPiece
        binding.mapView.setScanning(state is ScanState.Analyzing)

        when (state) {
            ScanState.Idle -> binding.mapView.clearMatch()
            ScanState.Analyzing -> Unit
            is ScanState.Result -> showResult(state, (prev as? ScanState.Result)?.takeIf { it.match === state.match })
            ScanState.Blurry -> showError(R.string.blurry_title, R.string.blurry_body)
            ScanState.NoPiece -> showError(R.string.no_piece_title, R.string.no_piece_body)
        }
    }

    private fun showError(title: Int, body: Int) {
        binding.textErrorTitle.setText(title)
        binding.textErrorBody.setText(body)
        binding.mapView.clearMatch()
    }

    private fun showResult(r: ScanState.Result, samePiece: ScanState.Result?) {
        val m = r.match
        val leads = listOf(m.best) + m.alternatives
        val cand = r.shown

        binding.mapView.showMatch(cand, m.precision, leads.filter { it !== cand })
        binding.textLead.setText(if (r.selected == 0) R.string.best_lead else R.string.alt_lead)
        if (r.selected != 0) binding.textLead.text = getString(R.string.alt_lead, r.selected + 1)
        binding.textSpot.text = spotLabel(cand, m.precision)

        showConfidence(cand.confidence)
        if (samePiece != null) { turnPiece(cand.rotationDeg); return }   // only the lead changed

        // The piece turns to the orientation it has on the box.
        binding.imagePiece.setImageBitmap(r.cutout)
        binding.imagePiece.apply {
            rotation = 0f; scaleX = 0.4f; scaleY = 0.4f; alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450).setInterpolator(OvershootInterpolator(1.4f))
                .withEndAction { turnPiece(cand.rotationDeg) }.start()
        }

        // One chip per lead; tapping one moves the spotlight.
        binding.chipLeads.removeAllViews()
        leads.forEachIndexed { i, c ->
            val chip = Chip(requireContext(), null, 0).apply {
                id = View.generateViewId()
                text = getString(R.string.alt_lead, i + 1) + " · " + shortCell(c)
                isCheckable = true
                setChipBackgroundColorResource(R.color.chip_bg)
                setTextColor(resources.getColorStateList(R.color.chip_text, null))
                isCheckedIconVisible = false
                isChecked = i == r.selected
                setOnClickListener { viewModel.select(i) }
            }
            binding.chipLeads.addView(chip)
        }
    }

    /** Label, bar and count-up for the lead on screen (each lead has its own confidence). */
    private fun showConfidence(value: Int) {
        val label = when { value >= PieceMatcher.CELL_CONF -> R.string.conf_high; value >= PieceMatcher.ZONE_CONF -> R.string.conf_medium; else -> R.string.conf_low }
        binding.textConfLabel.text = getString(R.string.confidence_label) + " · " + getString(label)
        val from = binding.confBar.progress
        binding.confBar.setProgressCompat(value, true)
        ValueAnimator.ofInt(from, value).apply {
            duration = 700
            addUpdateListener { _binding?.textConfValue?.text = "${it.animatedValue} %" }
            start()
        }
    }

    private fun turnPiece(deg: Int) {
        _binding?.apply {
            textRotate.text = when {
                deg == 0 -> getString(R.string.rotate_none)
                deg <= 180 -> getString(R.string.rotate_hint, deg)
                else -> getString(R.string.rotate_hint_ccw, 360 - deg)
            }
            imagePiece.animate().rotation(if (deg <= 180) deg.toFloat() else deg - 360f).setStartDelay(150).setDuration(750)
                .setInterpolator(OvershootInterpolator(0.8f)).start()
        }
    }

    private fun shortCell(c: Candidate) = "L${c.cell.second + 1} C${c.cell.first + 1}"

    private fun spotLabel(c: Candidate, p: Precision): String = when (p) {
        Precision.CELL -> getString(R.string.spot_cell, c.cell.second + 1, c.cell.first + 1)
        Precision.ZONE -> getString(R.string.spot_zone, zoneName(c))
        Precision.UNSURE -> getString(R.string.spot_unsure, zoneName(c))
    }

    private fun zoneName(c: Candidate): String {
        val g = grid ?: return ""
        val zx = (c.col / g.cols * 3).toInt().coerceIn(0, 2)
        val zy = (c.row / g.rows * 3).toInt().coerceIn(0, 2)
        val names = intArrayOf(R.string.zone_tl, R.string.zone_t, R.string.zone_tr, R.string.zone_l, R.string.zone_c, R.string.zone_r, R.string.zone_bl, R.string.zone_b, R.string.zone_br)
        return getString(names[zy * 3 + zx])
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shownState = null
        _binding = null
    }
}
