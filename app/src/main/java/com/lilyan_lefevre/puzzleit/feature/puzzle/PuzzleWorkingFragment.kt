package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.app.Activity
import android.content.Intent
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilyan_lefevre.puzzleit.BuildConfig
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentPuzzleWorkingBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import com.lilyan_lefevre.puzzleit.shared.utils.PhotoHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment for working on a puzzle project
 */
@AndroidEntryPoint
class PuzzleWorkingFragment : Fragment() {

    private var _binding: FragmentPuzzleWorkingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PuzzleWorkingViewModel by viewModels()
    private lateinit var projectId: String
    
    private var currentProject: Project? = null
    private var photoFile: File? = null

    private val takePiecePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoFile?.let { file ->
                val action = PuzzleWorkingFragmentDirections.actionPuzzleWorkingFragmentToPieceBoundsFragment(
                    imagePath = file.absolutePath,
                    projectId = projectId
                )
                findNavController().navigate(action)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        
        setupMenu()
        observeViewModel()
        setupClickListeners()
        
        binding.photoPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (_binding != null) {
                    updateGridAlignment()
                }
            }
        })
        
        viewModel.loadProject(projectId)
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_puzzle_working, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit -> {
                        navigateToEditProject()
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun navigateToEditProject() {
        val action = PuzzleWorkingFragmentDirections.actionPuzzleWorkingFragmentToProjectCreationFragment(projectId)
        findNavController().navigate(action)
    }

    private fun showDeleteConfirmationDialog() {
        val projectName = viewModel.project.value?.name ?: "this project"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_project_title))
            .setMessage(getString(R.string.delete_project_message, projectName))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteProject()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteProject() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteProject()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.project.collect { project ->
                        currentProject = project
                        project?.let {
                            updateUI(it)
                            (activity as? AppCompatActivity)?.supportActionBar?.title = it.name
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        if (_binding != null) {
                            binding.progressBar.visibility = 
                                if (isLoading) View.VISIBLE else View.GONE
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
                launch {
                    viewModel.deletionSuccess.collect { success ->
                        if (success) {
                            findNavController().popBackStack(R.id.projectListFragment, false)
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(project: Project) {
        if (_binding == null) return

        binding.apply {
            textNumberPieces.text = resources.getString(R.string.pieces_name, project.puzzleSize)
            textViewCreationDate.text = formatDate(project.creationDate)
            
            val imagePathToLoad = project.warpedPath.ifEmpty { 
                project.thumbnailPath.ifEmpty { project.imagePath } 
            }
            
            val glideRequest = Glide.with(requireContext())
                .load(imagePathToLoad)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .fitCenter()

            if (BuildConfig.DEBUG) {
                glideRequest.listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        if (_binding != null) {
                            gridOverlay.visibility = View.GONE
                        }
                        return false 
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        if (_binding != null) {
                            gridOverlay.setGrid(project.gridRows, project.gridCols)
                            photoPreview.post {
                                if (_binding != null) {
                                    updateGridAlignment()
                                }
                            }
                        }
                        return false
                    }
                }).into(photoPreview)
            } else {
                gridOverlay.visibility = View.GONE
                glideRequest.into(photoPreview)
            }
        }
    }

    private fun updateGridAlignment() {
        if (_binding == null) return

        val drawable = binding.photoPreview.drawable ?: return
        if (!BuildConfig.DEBUG) return

        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        if (imageWidth <= 0 || imageHeight <= 0) return

        val viewWidth = binding.photoPreview.width.toFloat()
        val viewHeight = binding.photoPreview.height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight)
        val finalWidth = imageWidth * scale
        val finalHeight = imageHeight * scale
        val left = (viewWidth - finalWidth) / 2f
        val top = (viewHeight - finalHeight) / 2f
        
        val actualImageRect = RectF(left, top, left + finalWidth, top + finalHeight)
        binding.gridOverlay.setTargetRect(actualImageRect)
        binding.gridOverlay.visibility = View.VISIBLE
    }

    private fun setupClickListeners() {
        binding.buttonCapturePiece.setOnClickListener {
            dispatchTakePiecePictureIntent()
        }
    }

    private fun dispatchTakePiecePictureIntent() {
        try {
            photoFile = PhotoHelper.createImageFile(requireContext(), "PIECE_")
            PhotoHelper.createCameraIntent(requireContext(), photoFile!!)?.also { intent ->
                takePiecePictureLauncher.launch(intent)
            }
        } catch (ex: Exception) {
            Log.e("PuzzleWorkingFragment", "Error starting camera", ex)
            Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
