package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.Fade
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleWorkingBinding
import com.lilyan_lefevre.puzzleit.databinding.ItemLeadExplainBinding
import com.lilyan_lefevre.puzzleit.feature.puzzle.capture.PieceCaptureFragment
import com.lilyan_lefevre.puzzleit.feature.recognition.Candidate
import com.lilyan_lefevre.puzzleit.feature.recognition.Grid
import com.lilyan_lefevre.puzzleit.feature.recognition.Match
import com.lilyan_lefevre.puzzleit.feature.recognition.PieceKind
import com.lilyan_lefevre.puzzleit.feature.recognition.PieceMatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The puzzle table: box image on top, scan / result sheet below. With a result the sheet slides between four levels:
 * peek (the leads), half (the piece next to the box), full (why these leads) and hidden (the map is free to explore).
 */
@AndroidEntryPoint
class PuzzleWorkingFragment : Fragment() {

    private var _binding: FragmentPuzzleWorkingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PuzzleWorkingViewModel by viewModels()
    private lateinit var projectId: String
    private lateinit var sheet: BottomSheetBehavior<View>
    private var grid: Grid? = null
    private var shownState: Any? = null
    private var resultOn = false
    private var mapInsetBottom = 0

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
        binding.mapView.onLeadTapped = { viewModel.select(it) }
        binding.buttonEdit.setOnClickListener {
            findNavController().navigate(R.id.action_puzzleWorkingFragment_to_projectCreationFragment, bundleOf("projectId" to projectId))
        }
        binding.buttonDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_project_title)
                .setMessage(getString(R.string.delete_project_message, binding.textViewProjectName.text))
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        val capture = View.OnClickListener { findNavController().navigate(R.id.action_puzzleWorkingFragment_to_pieceCaptureFragment) }
        binding.buttonCapturePiece.setOnClickListener(capture)
        binding.buttonNewPiece.setOnClickListener(capture)
        binding.buttonRetake.setOnClickListener(capture)
        binding.buttonDismiss.setOnClickListener { viewModel.dismiss() }
        binding.buttonErrorDismiss.setOnClickListener { viewModel.dismiss() }

        setFragmentResultListener(PieceCaptureFragment.RESULT_KEY) { _, b ->
            b.getString(PieceCaptureFragment.PHOTO_PATH)?.let(viewModel::analyze)
        }

        sheet = BottomSheetBehavior.from(binding.sheet)
        sheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheetView: View, newState: Int) {
                _binding?.pillLeads?.isVisible = resultOn && newState == BottomSheetBehavior.STATE_HIDDEN
                if (newState != BottomSheetBehavior.STATE_DRAGGING && newState != BottomSheetBehavior.STATE_SETTLING) syncMap()
            }
            override fun onSlide(sheetView: View, slideOffset: Float) = Unit
        })
        binding.pillLeads.setOnClickListener { sheet.state = BottomSheetBehavior.STATE_HALF_EXPANDED }

        observe()
        viewModel.loadProject(projectId)
    }

    /** The map must stay clear of the floating top bar and of the sheet, whatever its level; the full level covers it on purpose. */
    private val syncMapRunnable = Runnable {
        _binding?.let { b ->
            if (sheet.state != BottomSheetBehavior.STATE_EXPANDED) mapInsetBottom = (b.root.height - b.sheet.topIn(b.root)).coerceAtLeast(0)
            b.mapView.setViewportInsets(b.topBar.bottom, mapInsetBottom)
        }
    }

    private fun syncMap() {
        binding.sheet.removeCallbacks(syncMapRunnable)
        binding.sheet.postDelayed(syncMapRunnable, 90)
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.reference.collect { r ->
                        val (bmp, g) = r ?: return@collect
                        grid = g
                        binding.mapView.setImage(bmp, g)
                    }
                }
                launch { viewModel.project.collect { p -> p?.let { bindProject(it.name, it.puzzleSize) } } }
                launch { viewModel.isLoading.collect { binding.progressBar.isVisible = it } }
                launch { viewModel.scan.collect(::render) }
                launch {
                    viewModel.error.collect { error ->
                        error ?: return@collect
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        viewModel.errorShown()
                        findNavController().navigateUp()
                    }
                }
                launch {
                    viewModel.deleted.collect { gone ->
                        if (!gone) return@collect
                        Toast.makeText(requireContext(), getString(R.string.project_deleted_successfully, binding.textViewProjectName.text), Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    /** Name and piece count: the rows x columns of the puzzle are not shown, nobody knows them before the border is complete. */
    private fun bindProject(name: String, pieces: Int) {
        binding.textViewProjectName.text = name
        binding.textViewProjectInfo.text = getString(R.string.pieces_name, pieces)
    }

    // ---------------------------------------------------------------- states

    private fun render(state: ScanState) {
        // Re-collecting after a configuration change must not replay the camera move.
        val prev = shownState
        val first = prev == null
        if (state == prev) return
        shownState = state
        if (!first) TransitionManager.beginDelayedTransition(binding.sheetContent, Fade().setDuration(180))

        binding.groupIdle.isVisible = state is ScanState.Idle
        binding.groupAnalyzing.isVisible = state is ScanState.Analyzing
        binding.groupResult.isVisible = state is ScanState.Result
        binding.groupError.isVisible = state is ScanState.Blurry || state is ScanState.NoPiece
        binding.mapView.setScanning(state is ScanState.Analyzing)
        resultOn = state is ScanState.Result
        binding.pillLeads.isVisible = resultOn && sheet.state == BottomSheetBehavior.STATE_HIDDEN

        when (state) {
            ScanState.Idle -> binding.mapView.clearMatch()
            ScanState.Analyzing -> Unit
            is ScanState.Result -> showResult(state, (prev as? ScanState.Result)?.takeIf { it.match === state.match })
            ScanState.Blurry -> showError(R.string.blurry_title, R.string.blurry_body)
            ScanState.NoPiece -> showError(R.string.no_piece_title, R.string.no_piece_body)
        }
        levelOnceLaidOut(state, newResult = state is ScanState.Result && prev !is ScanState.Result)
    }

    /**
     * The sheet's levels depend on the height of what it shows: wait for the layout of the new state, then set them.
     * A result opens at the half level; the other states are fixed and show their whole content.
     */
    private fun levelOnceLaidOut(state: ScanState, newResult: Boolean) {
        val v = binding.sheet
        v.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                v.viewTreeObserver.removeOnPreDrawListener(this)
                _binding?.let { applyLevels(it, state, newResult) }
                return true
            }
        })
        v.invalidate()
    }

    private fun applyLevels(b: FragmentPuzzleWorkingBinding, state: ScanState, newResult: Boolean) {
        val dp = resources.displayMetrics.density
        val top = b.sheetScroll.top
        if (state is ScanState.Result) {
            sheet.isDraggable = true
            sheet.isHideable = true
            sheet.peekHeight = (top + b.chipScroll.bottomIn(b.sheetContent) + 14 * dp).toInt()
            sheet.halfExpandedRatio = ((top + b.buttonDismiss.bottomIn(b.sheetContent) + 24 * dp) / b.root.height).coerceIn(0.25f, 0.9f)
            if (newResult) sheet.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        } else {
            sheet.isHideable = false
            sheet.isDraggable = false
            sheet.peekHeight = top + b.sheetContent.height
            sheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        syncMap()
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

        binding.mapView.showLeads(leads, r.selected)
        binding.imageBoxPatch.setImageBitmap(boxPatch(cand))
        binding.textLead.setText(if (r.selected == 0) R.string.best_lead else R.string.alt_lead)
        if (r.selected != 0) binding.textLead.text = getString(R.string.alt_lead, r.selected + 1)
        when (m.kind) {
            PieceKind.CORNER -> binding.textLead.append(" · " + getString(R.string.piece_corner))
            PieceKind.EDGE -> binding.textLead.append(" · " + getString(R.string.piece_edge))
            else -> Unit
        }
        binding.textSpot.text = zoneText(cand)
        binding.textSpotDetail.text = spotPercent(cand)

        showConfidence(cand.confidence)
        if (samePiece != null) { turnPiece(cand.rotationDeg); return }   // only the lead changed

        // The piece turns to the orientation it has on the box.
        binding.imagePiece.setImageBitmap(r.cutout)
        binding.imagePiece.apply {
            rotation = 0f; scaleX = 0.4f; scaleY = 0.4f; alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450).setInterpolator(OvershootInterpolator(1.4f))
                .withEndAction { turnPiece(cand.rotationDeg) }.start()
        }

        // One chip per lead, best first with its confidence; tapping one moves the spotlight.
        binding.chipLeads.removeAllViews()
        leads.forEachIndexed { i, c ->
            val chip = Chip(requireContext(), null, 0).apply {
                id = View.generateViewId()
                text = getString(R.string.alt_lead, i + 1) + " · " + c.confidence + " %"
                isCheckable = true
                setChipBackgroundColorResource(R.color.chip_bg)
                setTextColor(resources.getColorStateList(R.color.chip_text, null))
                isCheckedIconVisible = false
                isChecked = i == r.selected
                setOnClickListener { viewModel.select(i) }
            }
            binding.chipLeads.addView(chip)
        }
        explainCards(r, leads)
    }

    /** The full level: one card per lead with its confidence, the piece blinking over the box, and the reasons. */
    private fun explainCards(r: ScanState.Result, leads: List<Candidate>) {
        binding.explainList.removeAllViews()
        val (box, g) = viewModel.reference.value ?: return
        leads.forEachIndexed { i, c ->
            val card = ItemLeadExplainBinding.inflate(layoutInflater, binding.explainList, false)
            card.leadTitle.text = getString(R.string.alt_lead, i + 1)
            card.leadValue.text = "${c.confidence} %"
            card.leadBar.progress = c.confidence
            card.leadCompare.bind(box, g, c, r.cutout)
            card.leadReasons.text = reasons(r.match, c)
            binding.explainList.addView(card.root)
        }
    }

    private fun reasons(m: Match, c: Candidate): String = buildList {
        if (c.pool > 0) {
            add(getString(R.string.reason_similarity, c.simRank, c.pool))
            add(getString(R.string.reason_colour, c.colourRank))
        }
        add(rotationText(c.rotationDeg))
        when (m.kind) {
            PieceKind.CORNER -> R.string.piece_corner
            PieceKind.EDGE -> R.string.piece_edge
            PieceKind.INTERIOR -> R.string.piece_inner
            PieceKind.UNKNOWN -> null
        }?.let { add(getString(R.string.reason_shape, getString(it))) }
    }.joinToString("\n")

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

    private fun rotationText(deg: Int) = when {
        deg == 0 -> getString(R.string.rotate_none)
        deg <= 180 -> getString(R.string.rotate_hint, deg)
        else -> getString(R.string.rotate_hint_ccw, 360 - deg)
    }

    private fun turnPiece(deg: Int) {
        _binding?.apply {
            textRotate.text = rotationText(deg)
            imagePiece.animate().rotation(if (deg <= 180) deg.toFloat() else deg - 360f).setStartDelay(150).setDuration(750)
                .setInterpolator(OvershootInterpolator(0.8f)).start()
        }
    }

    /** Where on the box, in words: a ninth of the puzzle. Rows and columns are not used, they are not known before the border is complete. */
    private fun zoneText(c: Candidate): String {
        val g = grid ?: return ""
        val x = (c.col / g.cols).coerceIn(0f, 0.999f); val y = (c.row / g.rows).coerceIn(0f, 0.999f)
        val zones = arrayOf(
            intArrayOf(R.string.zone_top_left, R.string.zone_top, R.string.zone_top_right),
            intArrayOf(R.string.zone_left, R.string.zone_centre, R.string.zone_right),
            intArrayOf(R.string.zone_bottom_left, R.string.zone_bottom, R.string.zone_bottom_right),
        )
        return getString(zones[(y * 3).toInt()][(x * 3).toInt()])
    }

    private fun spotPercent(c: Candidate): String {
        val g = grid ?: return ""
        return getString(R.string.spot_percent, (100 * c.col / g.cols).roundToInt().coerceIn(0, 100), (100 * c.row / g.rows).roundToInt().coerceIn(0, 100))
    }

    /** The box at this lead, one cell plus a little margin, in the box's orientation. */
    private fun boxPatch(c: Candidate): android.graphics.Bitmap? {
        val (bmp, g) = viewModel.reference.value ?: return null
        val cw = bmp.width / g.cols.toFloat(); val ch = bmp.height / g.rows.toFloat()
        val w = (cw * 1.3f).toInt().coerceIn(1, bmp.width); val h = (ch * 1.3f).toInt().coerceIn(1, bmp.height)
        val x = (c.col * cw - w / 2f).toInt().coerceIn(0, bmp.width - w); val y = (c.row * ch - h / 2f).toInt().coerceIn(0, bmp.height - h)
        return android.graphics.Bitmap.createBitmap(bmp, x, y, w, h)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shownState = null
        _binding = null
    }
}

/** Top edge of this view in the coordinates of [root], wherever the behaviour moved it (its layout `top` does not follow). */
private fun View.topIn(root: View): Int {
    val me = IntArray(2); val base = IntArray(2)
    getLocationOnScreen(me); root.getLocationOnScreen(base)
    return me[1] - base[1]
}

/** Bottom edge of this view in the coordinates of [ancestor]. */
private fun View.bottomIn(ancestor: View): Int {
    var y = bottom
    var p = parent as? View
    while (p != null && p !== ancestor) { y += p.top; p = p.parent as? View }
    return y
}
