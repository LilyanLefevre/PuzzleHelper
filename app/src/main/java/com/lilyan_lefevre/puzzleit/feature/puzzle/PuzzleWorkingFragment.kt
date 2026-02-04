package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
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
import com.lilyan_lefevre.puzzleit.BuildConfig
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
import javax.inject.Inject
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
            
            // Load the cropped image directly (no need to rectify again)
            val imagePathToLoad = if (project.thumbnailPath.isNullOrEmpty()) {
                project.imagePath
            } else {
                project.thumbnailPath
            }
            
            Glide.with(requireContext())
                .load(imagePathToLoad)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .fitCenter()
                .into(imageViewReference)
        }
    }

    private fun showRectifiedImage(imagePath: String, quadJson: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rectified = withContext(Dispatchers.Default) {
                val src = BitmapFactory.decodeFile(imagePath) ?: return@withContext null
                val quad = parseNormalizedQuad(quadJson) ?: return@withContext null
                rectifyBitmapFromNormalizedQuad(src, quad)
            }

            rectified?.let {
                binding.imageViewReference.setImageBitmap(it)
            }
        }
    }

    private fun parseNormalizedQuad(quadJson: String): List<Pair<Float, Float>>? {
        return try {
            val arr = JSONArray(quadJson)
            if (arr.length() != 4) return null
            val pts = ArrayList<Pair<Float, Float>>(4)
            for (i in 0 until 4) {
                val p = arr.getJSONArray(i)
                val x = p.getDouble(0).toFloat()
                val y = p.getDouble(1).toFloat()
                pts.add(Pair(x, y))
            }
            pts
        } catch (_: Throwable) {
            null
        }
    }

    private fun rectifyBitmapFromNormalizedQuad(
        src: Bitmap,
        quadNormClockwise: List<Pair<Float, Float>>
    ): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()

        val srcPts = FloatArray(8)
        for (i in 0 until 4) {
            srcPts[i * 2] = quadNormClockwise[i].first.coerceIn(0f, 1f) * w
            srcPts[i * 2 + 1] = quadNormClockwise[i].second.coerceIn(0f, 1f) * h
        }

        val wTop = distance(srcPts[0], srcPts[1], srcPts[2], srcPts[3])
        val wBottom = distance(srcPts[6], srcPts[7], srcPts[4], srcPts[5])
        val outW = max(wTop, wBottom).toInt().coerceAtLeast(1)

        val hLeft = distance(srcPts[0], srcPts[1], srcPts[6], srcPts[7])
        val hRight = distance(srcPts[2], srcPts[3], srcPts[4], srcPts[5])
        val outH = max(hLeft, hRight).toInt().coerceAtLeast(1)

        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val m = Matrix()
        m.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        c.drawBitmap(src, m, paint)
        return out
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
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
