package com.lilyan_lefevre.puzzleit.feature.puzzle.piece

import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.databinding.FragmentPieceBoundsBinding
import com.lilyan_lefevre.puzzleit.feature.puzzle.PuzzleWorkingViewModel
import com.lilyan_lefevre.puzzleit.feature.storage.ImageStorageManager
import com.lilyan_lefevre.puzzleit.shared.ui.QuadSelectionView
import com.lilyan_lefevre.puzzleit.shared.utils.ImageUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class PieceBoundsFragment : Fragment() {

    private var _binding: FragmentPieceBoundsBinding? = null
    private val binding get() = _binding!!

    private val args: PieceBoundsFragmentArgs by navArgs()
    private val workingViewModel: PuzzleWorkingViewModel by viewModels()

    @Inject
    lateinit var imageUtils: ImageUtils
    
    @Inject
    lateinit var imageStorageManager: ImageStorageManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPieceBoundsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load project to get its name
        workingViewModel.loadProject(args.projectId ?: "")
        
        loadImage()
        setupClickListeners()
    }

    private fun loadImage() {
        val imagePath = args.imagePath
        val (srcW, srcH) = imageUtils.readOrientedDimensions(imagePath)
        binding.quadSelectionView.setSourceSize(srcW, srcH)

        Glide.with(requireContext())
            .load(imagePath)
            .fitCenter()
            .into(binding.imageView)

        lifecycleScope.launch {
            try {
                val pieceData = detectPieceData(imagePath, null)
                if (pieceData != null) {
                    binding.quadSelectionView.setNormalizedQuad(pieceData.boundingQuad)
                    binding.quadSelectionView.setNormalizedContour(pieceData.contour)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initial detection", e)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener { 
            cleanupTempAndNavigateUp()
        }

        binding.btnConfirm.setOnClickListener {
            confirmSelection()
        }

        binding.quadSelectionView.setOnQuadChangedListener(object : QuadSelectionView.OnQuadChangedListener {
            override fun onQuadChanged(points: List<PointF>) {
                updateContour(points)
            }
        })
    }

    private fun cleanupTempAndNavigateUp() {
        try {
            val file = File(args.imagePath)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp file", e)
        }
        findNavController().navigateUp()
    }

    private fun updateContour(quad: List<PointF>) {
        lifecycleScope.launch {
            try {
                val pieceData = detectPieceData(args.imagePath, quad)
                if (_binding != null) {
                    binding.quadSelectionView.setNormalizedContour(pieceData?.contour)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating contour", e)
            }
        }
    }

    private fun confirmSelection() {
        lifecycleScope.launch {
            try {
                val quad = binding.quadSelectionView.getNormalizedQuad()
                val originalBitmap = BitmapFactory.decodeFile(args.imagePath) ?: return@launch
                val correctedBitmap = imageUtils.rotateBitmap(originalBitmap, args.imagePath)

                val warpedBitmap = imageUtils.warpPerspectiveWithQuad(correctedBitmap, quad)
                val finalPieceBitmap = imageUtils.extractPieceWithMask(warpedBitmap)
                
                val projectId = args.projectId ?: "unknown"
                val projectName = workingViewModel.project.value?.name ?: "puzzle"
                
                // Save original capture in organized dir
                imageStorageManager.saveOriginalPieceImage(correctedBitmap, projectId, projectName)
                
                // Save extracted piece (PNG with transparency) in organized dir
                imageStorageManager.saveExtractedPieceImage(finalPieceBitmap, projectId, projectName)

                // Cleanup bitmaps
                if (correctedBitmap != originalBitmap) correctedBitmap.recycle()
                originalBitmap.recycle()
                warpedBitmap.recycle()
                finalPieceBitmap.recycle()

                // IMPORTANT: Delete the temporary capture file from files/ root
                val tempFile = File(args.imagePath)
                if (tempFile.exists()) tempFile.delete()

                Toast.makeText(requireContext(), "Pièce sauvegardée proprement", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Log.e(TAG, "Error confirming selection", e)
                Toast.makeText(requireContext(), "Erreur lors du traitement", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class PieceDetectionResult(
        val boundingQuad: List<PointF>,
        val contour: List<PointF>
    )

    private suspend fun detectPieceData(imagePath: String, restrictToQuad: List<PointF>?): PieceDetectionResult? = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return@withContext null
        val correctedBitmap = imageUtils.rotateBitmap(bitmap, imagePath)

        val fullMat = Mat()
        Utils.bitmapToMat(correctedBitmap, fullMat)
        
        var workMat = fullMat
        var offsetX = 0
        var offsetY = 0

        if (restrictToQuad != null) {
            val minX = (restrictToQuad.minOf { it.x } * fullMat.cols()).toInt().coerceIn(0, fullMat.cols() - 1)
            val maxX = (restrictToQuad.maxOf { it.x } * fullMat.cols()).toInt().coerceIn(0, fullMat.cols() - 1)
            val minY = (restrictToQuad.minOf { it.y } * fullMat.rows()).toInt().coerceIn(0, fullMat.rows() - 1)
            val maxY = (restrictToQuad.maxOf { it.y } * fullMat.rows()).toInt().coerceIn(0, fullMat.rows() - 1)
            
            if (maxX - minX > 10 && maxY - minY > 10) {
                val roi = Rect(minX, minY, maxX - minX, maxY - minY)
                workMat = Mat(fullMat, roi)
                offsetX = minX
                offsetY = minY
            }
        }

        val gray = Mat()
        Imgproc.cvtColor(workMat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        
        val edges = Mat()
        Imgproc.Canny(gray, edges, 20.0, 60.0)
        
        val binary = Mat()
        Imgproc.adaptiveThreshold(gray, binary, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0)
        
        Core.bitwise_or(edges, binary, binary)
        
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val centerX = workMat.cols() / 2.0
        val centerY = workMat.rows() / 2.0
        
        var bestContour: MatOfPoint? = null
        var maxScore = -1.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < 150) continue
            
            val moments = Imgproc.moments(contour)
            if (moments.m00 == 0.0) continue
            val cX = moments.m10 / moments.m00
            val cY = moments.m01 / moments.m00
            
            val dist = sqrt((cX - centerX) * (cX - centerX) + (cY - centerY) * (cY - centerY))
            val maxPossibleDist = sqrt(centerX * centerX + centerY * centerY)
            val proximityScore = 1.0 - (dist / (maxPossibleDist + 1.0))
            
            val score = area * proximityScore * proximityScore
            if (score > maxScore) {
                maxScore = score
                bestContour = contour
            }
        }

        val result = if (bestContour != null) {
            val rect = Imgproc.boundingRect(bestContour)
            val invW = 1.0f / fullMat.cols()
            val invH = 1.0f / fullMat.rows()
            
            val contourPoints = bestContour.toArray().map {
                PointF((it.x.toFloat() + offsetX) * invW, (it.y.toFloat() + offsetY) * invH)
            }

            val boundingQuad = if (restrictToQuad != null) {
                restrictToQuad
            } else {
                listOf(
                    PointF((rect.x + offsetX) * invW, (rect.y + offsetY) * invH),
                    PointF((rect.x + rect.width + offsetX) * invW, (rect.y + offsetY) * invH),
                    PointF((rect.x + rect.width + offsetX) * invW, (rect.y + rect.height + offsetY) * invH),
                    PointF((rect.x + offsetX) * invW, (rect.y + rect.height + offsetY) * invH)
                )
            }
            PieceDetectionResult(boundingQuad, contourPoints)
        } else null
        
        if (workMat != fullMat) workMat.release()
        fullMat.release()
        gray.release()
        binary.release()
        edges.release()
        kernel.release()
        correctedBitmap.recycle()
        bitmap.recycle()

        result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "PieceBoundsFragment"
    }
}
