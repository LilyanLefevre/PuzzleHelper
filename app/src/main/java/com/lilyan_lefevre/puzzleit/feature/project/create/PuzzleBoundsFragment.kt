package com.lilyan_lefevre.puzzleit.feature.project.create

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.core.image.ImageUtils
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleBoundsBinding
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONArray
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PuzzleBoundsFragment : Fragment() {
    companion object {
        private const val TAG = "PuzzleBoundsFragment"
    }

    private var _binding: FragmentPuzzleBoundsBinding? = null
    private val binding get() = _binding!!

    private val args: PuzzleBoundsFragmentArgs by navArgs()

    @Inject
    lateinit var imageUtils: ImageUtils

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPuzzleBoundsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadImage()
        setupClickListeners()
    }

    private fun loadImage() {
        val imagePath = args.imagePath
        
        // Use mutualized oriented dimensions
        val (srcW, srcH) = imageUtils.readOrientedDimensions(imagePath)
        binding.quadSelectionView.setSourceSize(srcW, srcH)

        // Load with Glide - Glide usually handles EXIF rotation automatically
        Glide.with(requireContext())
            .load(imagePath)
            .fitCenter()
            .into(binding.imageView)

        // Try to auto-detect puzzle bounds
        lifecycleScope.launch {
            try {
                val detectedQuad = detectPuzzleBounds(imagePath)
                if (detectedQuad != null) {
                    binding.quadSelectionView.setNormalizedQuad(detectedQuad)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting puzzle bounds", e)
            }
        }
    }

    private suspend fun detectPuzzleBounds(imagePath: String): List<PointF>? = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4 
        }
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return@withContext null
        
        // Correct rotation using mutualized tool
        val correctedBitmap = imageUtils.rotateBitmap(bitmap, imagePath)
        
        val mat = Mat()
        Utils.bitmapToMat(correctedBitmap, mat)
        
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        var largestQuad: MatOfPoint2f? = null
        var maxArea = 0.0
        
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 5000) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val perimeter = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)
                
                if (approx.total() == 4L && area > maxArea) {
                    largestQuad = approx
                    maxArea = area
                }
            }
        }
        
        val result = if (largestQuad != null) {
            val points = largestQuad.toArray()
            val orderedPoints = orderPoints(points)
            
            // Normalize using full oriented dimensions
            val fullDimensions = imageUtils.readOrientedDimensions(imagePath)
            
            orderedPoints.map { 
                PointF(
                    (it.x.toFloat() * options.inSampleSize) / fullDimensions.first,
                    (it.y.toFloat() * options.inSampleSize) / fullDimensions.second
                )
            }
        } else null
        
        mat.release()
        gray.release()
        edges.release()
        if (correctedBitmap != bitmap) correctedBitmap.recycle()
        bitmap.recycle()
        
        result
    }

    private fun orderPoints(points: Array<Point>): Array<Point> {
        val ordered = Array(4) { Point() }
        val sums = points.map { it.x + it.y }
        val diffs = points.map { it.y - it.x }
        
        ordered[0] = points[sums.indexOf(sums.minOrNull())] // TL
        ordered[2] = points[sums.indexOf(sums.maxOrNull())] // BR
        ordered[1] = points[diffs.indexOf(diffs.minOrNull())] // TR
        ordered[3] = points[diffs.indexOf(diffs.maxOrNull())] // BL
        
        return ordered
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnConfirm.setOnClickListener {
            val quad = binding.quadSelectionView.getNormalizedQuad()
            val quadJson = quadToJsonString(quad)
            
            lifecycleScope.launch {
                try {
                    val croppedImagePath = processCroppedImage(quad)
                    val result = Bundle().apply {
                        putString("quadJson", quadJson)
                        putString("croppedImagePath", croppedImagePath)
                    }
                    setFragmentResult("puzzleBoundsResult", result)
                    findNavController().popBackStack(R.id.projectCreationFragment, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing cropped image", e)
                    val result = Bundle().apply {
                        putString("quadJson", quadJson)
                        putString("croppedImagePath", args.imagePath)
                    }
                    setFragmentResult("puzzleBoundsResult", result)
                    findNavController().popBackStack(R.id.projectCreationFragment, false)
                }
            }
        }
    }

    private fun quadToJsonString(points: List<PointF>): String {
        val arr = JSONArray()
        for (pt in points) {
            val p = JSONArray()
            p.put(pt.x.toDouble())
            p.put(pt.y.toDouble())
            arr.put(p)
        }
        return arr.toString()
    }

    private suspend fun processCroppedImage(quad: List<PointF>): String = withContext(Dispatchers.IO) {
        val originalFile = File(args.imagePath)
        val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath) ?: throw Exception("Failed to load original image")

        // Use mutualized rotation tool
        val correctedBitmap = imageUtils.rotateBitmap(originalBitmap, args.imagePath)

        // Use mutualized warp tool
        val warpedBitmap = imageUtils.warpPerspectiveWithQuad(correctedBitmap, quad)

        // Save temporary result
        val croppedFile = File(requireContext().filesDir, "temp_warped_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(croppedFile).use { out ->
            warpedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        if (correctedBitmap != originalBitmap) correctedBitmap.recycle()
        warpedBitmap.recycle()
        originalBitmap.recycle()

        croppedFile.absolutePath
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
