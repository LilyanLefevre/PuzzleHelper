package com.lilyan_lefevre.puzzleit.feature.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import androidx.core.graphics.createBitmap

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
    private fun warpPerspectiveWithQuad(bitmap: Bitmap, quad: List<android.graphics.PointF>): Bitmap {
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
