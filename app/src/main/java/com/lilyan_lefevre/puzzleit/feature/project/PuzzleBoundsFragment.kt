package com.lilyan_lefevre.puzzleit.feature.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleBoundsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

@AndroidEntryPoint
class PuzzleBoundsFragment : Fragment() {
    companion object {
        private const val TAG = "PuzzleBoundsFragment"
    }

    private var _binding: FragmentPuzzleBoundsBinding? = null
    private val binding get() = _binding!!

    private val args: PuzzleBoundsFragmentArgs by navArgs()

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
        
        // Load oriented dimensions first
        val (srcW, srcH) = readOrientedDimensions(imagePath)
        binding.quadSelectionView.setSourceSize(srcW, srcH)

        // Load the captured image with fitCenter
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
            inSampleSize = 4 // Scale down for faster processing
        }
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return@withContext null
        val correctedBitmap = correctBitmapRotation(bitmap, imagePath)
        
        val mat = Mat()
        Utils.bitmapToMat(correctedBitmap, mat)
        
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        
        // Blur to reduce noise
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        
        // Canny edge detection
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        
        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        var largestQuad: MatOfPoint2f? = null
        var maxArea = 0.0
        
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 5000) { // Minimum area threshold
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
            // Order points: TL, TR, BR, BL
            val orderedPoints = orderPoints(points)
            
            val fullDimensions = readOrientedDimensions(imagePath)
            
            orderedPoints.map { 
                PointF(
                    (it.x.toFloat() * options.inSampleSize) / fullDimensions.first,
                    (it.y.toFloat() * options.inSampleSize) / fullDimensions.second
                )
            }
        } else {
            null
        }
        
        // Cleanup
        mat.release()
        gray.release()
        edges.release()
        correctedBitmap.recycle()
        
        result
    }

    private fun orderPoints(points: Array<Point>): Array<Point> {
        val ordered = Array(4) { Point() }
        
        // 1. Sum and difference to find TL, TR, BR, BL
        val sums = points.map { it.x + it.y }
        val diffs = points.map { it.y - it.x }
        
        ordered[0] = points[sums.indexOf(sums.minOrNull())] // TL (min sum)
        ordered[2] = points[sums.indexOf(sums.maxOrNull())] // BR (max sum)
        ordered[1] = points[diffs.indexOf(diffs.minOrNull())] // TR (min diff)
        ordered[3] = points[diffs.indexOf(diffs.maxOrNull())] // BL (max diff)
        
        return ordered
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnConfirm.setOnClickListener {
            val quad = binding.quadSelectionView.getNormalizedQuad()
            val quadJson = quadToJsonString(quad)
            
            // Process the cropped image
            lifecycleScope.launch {
                try {
                    val croppedImagePath = processCroppedImage(quad)
                    val result = Bundle().apply {
                        putString("quadJson", quadJson)
                        putString("croppedImagePath", croppedImagePath)
                    }
                    setFragmentResult("puzzleBoundsResult", result)
                    // Navigate back to ProjectCreationFragment, not just up
                    findNavController().popBackStack(R.id.projectCreationFragment, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing cropped image", e)
                    // Fallback to original image if cropping fails
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

        // Correction de la rotation basée sur l'EXIF (déjà présent)
        val correctedBitmap = correctBitmapRotation(originalBitmap, args.imagePath)

        // Utilisation de Warp Perspective pour redresser l'image
        val warpedBitmap = warpPerspectiveWithQuad(correctedBitmap, quad)

        // Sauvegarde de l'image redressée
        val croppedFile = File(
            requireContext().filesDir,
            "warped_${System.currentTimeMillis()}.jpg"
        )

        FileOutputStream(croppedFile).use { out ->
            warpedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Nettoyage des bitmaps intermédiaires
        if (correctedBitmap != originalBitmap) correctedBitmap.recycle()
        warpedBitmap.recycle()
        originalBitmap.recycle()

        croppedFile.absolutePath
    }

    /**
     * Effectue une transformation de perspective pour redresser la zone sélectionnée
     */
    private fun warpPerspectiveWithQuad(bitmap: Bitmap, quad: List<PointF>): Bitmap {
        val srcWidth = bitmap.width.toDouble()
        val srcHeight = bitmap.height.toDouble()

        // 1. Points sources (ceux sélectionnés par l'utilisateur)
        // L'ordre attendu par mon implémentation est TL, TR, BR, BL
        val srcPoints = listOf(
            Point(quad[0].x * srcWidth, quad[0].y * srcHeight),
            Point(quad[1].x * srcWidth, quad[1].y * srcHeight),
            Point(quad[2].x * srcWidth, quad[2].y * srcHeight),
            Point(quad[3].x * srcWidth, quad[3].y * srcHeight)
        )
        val srcMat = Converters.vector_Point2f_to_Mat(srcPoints)

        // 2. Calcul des dimensions cibles du rectangle final
        // On prend le maximum des largeurs/hauteurs pour ne pas perdre en qualité
        val widthTop = hypot(srcPoints[1].x - srcPoints[0].x, srcPoints[1].y - srcPoints[0].y)
        val widthBottom = hypot(srcPoints[2].x - srcPoints[3].x, srcPoints[2].y - srcPoints[3].y)
        val targetWidth = widthTop.coerceAtLeast(widthBottom).toInt()

        val heightLeft = hypot(srcPoints[3].x - srcPoints[0].x, srcPoints[3].y - srcPoints[0].y)
        val heightRight = hypot(srcPoints[2].x - srcPoints[1].x, srcPoints[2].y - srcPoints[1].y)
        val targetHeight = heightLeft.coerceAtLeast(heightRight).toInt()

        // 3. Points destinations (le rectangle parfait de sortie)
        val dstPoints = listOf(
            Point(0.0, 0.0),
            Point(targetWidth.toDouble(), 0.0),
            Point(targetWidth.toDouble(), targetHeight.toDouble()),
            Point(0.0, targetHeight.toDouble())
        )
        val dstMat = Converters.vector_Point2f_to_Mat(dstPoints)

        // 4. Application de la transformation avec OpenCV
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val srcMatImage = Mat()
        Utils.bitmapToMat(bitmap, srcMatImage)

        val dstMatImage = Mat()
        Imgproc.warpPerspective(
            srcMatImage,
            dstMatImage,
            perspectiveTransform,
            Size(targetWidth.toDouble(), targetHeight.toDouble())
        )

        // 5. Conversion retour en Bitmap
        val resultBitmap = createBitmap(targetWidth, targetHeight)
        Utils.matToBitmap(dstMatImage, resultBitmap)

        // Libération de la mémoire native OpenCV
        srcMat.release()
        dstMat.release()
        perspectiveTransform.release()
        srcMatImage.release()
        dstMatImage.release()

        return resultBitmap
    }

    private fun correctBitmapRotation(bitmap: Bitmap, imagePath: String): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap // No rotation needed
            }
            
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle() // Recycle original if we created a new one
            }
            rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap // Return original if rotation fails
        }
    }

    private fun readOrientedDimensions(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var w = opts.outWidth
        var h = opts.outHeight
        if (w <= 0 || h <= 0) return Pair(1, 1)

        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            val tmp = w
            w = h
            h = tmp
        }
        return Pair(w, h)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
