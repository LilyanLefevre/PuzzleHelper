package com.lilyan_lefevre.puzzleit.feature.project.create

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.core.image.PhotoHelper
import com.lilyan_lefevre.puzzleit.databinding.FragmentProjectCreationBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.launch

/** Create or edit a project: box photo (system camera), framing, name, piece count and grid. */
@AndroidEntryPoint
class ProjectCreationFragment : Fragment() {

    private var _binding: FragmentProjectCreationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectCreationViewModel by viewModels()
    private val args: ProjectCreationFragmentArgs by navArgs()

    /** File the system camera writes into; only meaningful while the camera app is open. */
    private var pendingPhoto: File? = null

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else Toast.makeText(requireContext(), R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = pendingPhoto ?: return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onPhotoTaken(file.absolutePath)
            openBounds(file.absolutePath)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProjectCreationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        args.projectId?.let(viewModel::loadForEdit)

        binding.photoPlaceholder.setOnClickListener { checkPermissionAndTakePhoto() }
        binding.retakePhotoButton.setOnClickListener { checkPermissionAndTakePhoto() }
        binding.editPhotoButton.setOnClickListener { viewModel.draft.value.originalPath?.let(::openBounds) }
        binding.btnCancel.setOnClickListener { viewModel.discardDraft(); findNavController().navigateUp() }
        binding.btnCreate.setOnClickListener {
            viewModel.save(binding.editTextName.text.toString(), number(binding.editTextPieces), number(binding.editTextRows), number(binding.editTextCols))
        }
        binding.editTextPieces.doAfterTextChanged { suggestGrid() }

        setFragmentResultListener(PuzzleBoundsFragment.RESULT_KEY) { _, b ->
            val quad = b.getString(PuzzleBoundsFragment.QUAD_JSON) ?: return@setFragmentResultListener
            val path = b.getString(PuzzleBoundsFragment.RECTIFIED_PATH) ?: return@setFragmentResultListener
            viewModel.onBoundsConfirmed(quad, path)
            suggestGrid()
        }
        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.draft.collect { renderPhoto(it.preview) } }
                launch {
                    viewModel.existing.collect { p ->
                        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(viewModel.titleRes())
                        p ?: return@collect
                        if (binding.editTextName.text.isNullOrEmpty()) {
                            binding.editTextName.setText(p.name)
                            binding.editTextPieces.setText(p.puzzleSize.toString())
                            binding.editTextRows.setText(p.gridRows.toString())
                            binding.editTextCols.setText(p.gridCols.toString())
                        }
                        binding.btnCreate.setText(R.string.save_changes)
                    }
                }
                launch { viewModel.isProcessing.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
                launch {
                    viewModel.error.collect { e ->
                        e ?: return@collect
                        when (e) {
                            is Int -> Toast.makeText(requireContext(), e, Toast.LENGTH_SHORT).show()
                            else -> MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.error).setMessage(e.toString())
                                .setPositiveButton(R.string.ok, null).show()
                        }
                        viewModel.errorShown()
                    }
                }
                launch {
                    viewModel.saved.collect { s ->
                        s ?: return@collect
                        val msg = when (s) {
                            is ProjectCreationViewModel.Saved.Created -> getString(R.string.project_created_successfully, s.project.name)
                            ProjectCreationViewModel.Saved.Updated -> getString(R.string.project_updated)
                        }
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        viewModel.savedHandled()
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun suggestGrid() {
        val pieces = number(binding.editTextPieces) ?: return
        viewModel.suggestGrid(pieces)?.let {
            binding.editTextRows.setText(it.rows.toString())
            binding.editTextCols.setText(it.cols.toString())
        }
    }

    private fun checkPermissionAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
        else requestPermission.launch(Manifest.permission.CAMERA)
    }

    private fun takePhoto() {
        try {
            val file = PhotoHelper.createImageFile(requireContext()).also { pendingPhoto = it }
            PhotoHelper.createCameraIntent(requireContext(), file)?.let(takePicture::launch)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera", e)
            Toast.makeText(requireContext(), R.string.camera_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBounds(imagePath: String) {
        findNavController().navigate(ProjectCreationFragmentDirections.actionProjectCreationFragmentToPuzzleBoundsFragment(imagePath))
    }

    private fun renderPhoto(path: String?) {
        val has = !path.isNullOrBlank()
        binding.photoPlaceholder.visibility = if (has) View.GONE else View.VISIBLE
        binding.photoPreviewCard.visibility = if (has) View.VISIBLE else View.GONE
        binding.editPhotoButton.visibility = if (has) View.VISIBLE else View.GONE
        binding.retakePhotoButton.visibility = if (has) View.VISIBLE else View.GONE
        if (has) Glide.with(this).load(File(path!!)).placeholder(android.R.drawable.ic_menu_camera)
            .error(android.R.drawable.ic_menu_camera).fitCenter().into(binding.photoPreview)
        else Glide.with(this).clear(binding.photoPreview)
    }

    private fun number(field: android.widget.EditText): Int? = field.text.toString().toIntOrNull()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object { const val TAG = "ProjectCreation" }
}
