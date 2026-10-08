package com.lilyan_lefevre.puzzleit.feature.project.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.core.image.ImageUtils
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleBoundsBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Lets the user adjust the 4 corners of the box on the photo. */
@AndroidEntryPoint
class PuzzleBoundsFragment : Fragment() {

    private var _binding: FragmentPuzzleBoundsBinding? = null
    private val binding get() = _binding!!

    private val args: PuzzleBoundsFragmentArgs by navArgs()
    private val viewModel: PuzzleBoundsViewModel by viewModels()

    @Inject lateinit var imageUtils: ImageUtils

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPuzzleBoundsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val (w, h) = imageUtils.readOrientedDimensions(args.imagePath)
        binding.quadSelectionView.setSourceSize(w, h)
        Glide.with(this).load(args.imagePath).fitCenter().into(binding.imageView)   // Glide applies EXIF rotation

        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }
        binding.btnConfirm.setOnClickListener { viewModel.confirm(args.imagePath, binding.quadSelectionView.getNormalizedQuad()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.detected.collect { quad -> quad?.let(binding.quadSelectionView::setNormalizedQuad) } }
                launch {
                    viewModel.confirmed.collect { b ->
                        b ?: return@collect
                        setFragmentResult(RESULT_KEY, bundleOf(QUAD_JSON to b.quadJson, RECTIFIED_PATH to b.rectifiedPath))
                        findNavController().popBackStack(R.id.projectCreationFragment, false)
                    }
                }
            }
        }
        viewModel.detect(args.imagePath)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "puzzleBoundsResult"
        const val QUAD_JSON = "quadJson"
        const val RECTIFIED_PATH = "croppedImagePath"
    }
}
