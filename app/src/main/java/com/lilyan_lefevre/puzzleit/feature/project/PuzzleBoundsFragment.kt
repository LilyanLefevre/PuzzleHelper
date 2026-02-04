package com.lilyan_lefevre.puzzleit.feature.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Path
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
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
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class PuzzleBoundsFragment : Fragment() {

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

        setupToolbar()
        loadImage()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadImage() {
        val imagePath = args.imagePath
        // Load the captured image with fitCenter
        Glide.with(requireContext())
            .load(imagePath)
            .fitCenter()
            .into(binding.imageView)

        // Set source size for QuadSelectionView (read EXIF orientation to match Glide)
        val (srcW, srcH) = readOrientedDimensions(imagePath)
        binding.quadSelectionView.setSourceSize(srcW, srcH)
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

    private fun quadToJsonString(points: List<android.graphics.PointF>): String {
        val arr = JSONArray()
        for (pt in points) {
            val p = JSONArray()
            p.put(pt.x.toDouble())
            p.put(pt.y.toDouble())
            arr.put(p)
        }
        return arr.toString()
    }

    private suspend fun processCroppedImage(quad: List<android.graphics.PointF>): String = withContext(Dispatchers.IO) {
        val originalFile = File(args.imagePath)
        val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath) ?: throw Exception("Failed to load original image")
        
        // Correct rotation based on EXIF
        val correctedBitmap = correctBitmapRotation(originalBitmap, args.imagePath)
        
        // Create cropped bitmap using the quad points
        val croppedBitmap = cropBitmapWithQuad(correctedBitmap, quad)
        
        // Save cropped bitmap to new file
        val croppedFile = File(
            requireContext().filesDir,
            "cropped_${System.currentTimeMillis()}.jpg"
        )
        
        FileOutputStream(croppedFile).use { out ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        croppedFile.absolutePath
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

    private fun cropBitmapWithQuad(bitmap: Bitmap, quad: List<android.graphics.PointF>): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Convert normalized quad points to pixel coordinates
        val points = quad.map { point ->
            android.graphics.PointF(point.x * width, point.y * height)
        }
        
        // Create path from quad points
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        
        // Calculate bounding box
        val left = points.minOf { it.x }
        val top = points.minOf { it.y }
        val right = points.maxOf { it.x }
        val bottom = points.maxOf { it.y }
        val cropWidth = (right - left).toInt()
        val cropHeight = (bottom - top).toInt()
        
        // Crop the bitmap
        val cropped = Bitmap.createBitmap(
            bitmap,
            left.toInt(),
            top.toInt(),
            cropWidth,
            cropHeight
        )
        
        return cropped
    }

    private fun readOrientedDimensions(path: String): Pair<Int, Int> {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        var w = opts.outWidth
        var h = opts.outHeight
        if (w <= 0 || h <= 0) return Pair(1, 1)

        val exif = androidx.exifinterface.media.ExifInterface(path)
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
        if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270) {
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
