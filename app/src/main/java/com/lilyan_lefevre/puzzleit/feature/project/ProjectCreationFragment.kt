package com.lilyan_lefevre.puzzleit.feature.project

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
import com.lilyan_lefevre.puzzleit.databinding.FragmentProjectCreationBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import com.lilyan_lefevre.puzzleit.shared.util.GridProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/**
 * Fragment for project creation and edition with form and system camera integration
 */
@AndroidEntryPoint
class ProjectCreationFragment : Fragment() {

    private var _binding: FragmentProjectCreationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private val args: ProjectCreationFragmentArgs by navArgs()

    private var originalImagePath: String? = null
    private var croppedImagePath: String? = null
    private var currentQuadJson: String? = null
    private var photoFile: File? = null
    
    private var isEditMode = false
    private var existingProject: Project? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoFile?.let { file ->
                originalImagePath = file.absolutePath
                currentQuadJson = createDefaultQuad()
                navigateToPuzzleBounds(originalImagePath!!)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectCreationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isEditMode = args.projectId != null
        updateTitle()

        setupUI()
        observeViewModel()
        listenForPuzzleBoundsResult()

        if (isEditMode) {
            loadExistingProject(args.projectId!!)
        } else {
            renderPhotoState()
        }
    }

    private fun updateTitle() {
        val title = if (isEditMode) getString(R.string.edit_project_title) else getString(R.string.create_puzzle_title)
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    private fun loadExistingProject(projectId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loadProjects()
                viewModel.projects.collect { projects ->
                    val project = projects.find { it.id == projectId }
                    project?.let { 
                        existingProject = it
                        populateFields(it) 
                    }
                }
            }
        }
    }

    private fun populateFields(project: Project) {
        binding.editTextName.setText(project.name)
        binding.editTextPieces.setText(project.puzzleSize.toString())
        binding.editTextRows.setText(project.gridRows.toString())
        binding.editTextCols.setText(project.gridCols.toString())
        
        if (originalImagePath == null) {
            originalImagePath = project.imagePath
            croppedImagePath = project.thumbnailPath
            currentQuadJson = project.puzzleQuad
        }
        
        renderPhotoState()
        binding.btnCreate.text = getString(R.string.save_changes)
    }

    private fun setupUI() {
        binding.photoPlaceholder.setOnClickListener {
            dispatchTakePictureIntent()
        }
        
        binding.editPhotoButton.setOnClickListener {
            originalImagePath?.let { imagePath ->
                navigateToPuzzleBounds(imagePath)
            }
        }
        
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnCreate.setOnClickListener {
            if (isEditMode) updateProject() else createProject()
        }

        // Auto-calculate grid when pieces count changes
        binding.editTextPieces.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotEmpty()) {
                    autoCalculateGrid()
                }
            }
        })
    }

    private fun autoCalculateGrid() {
        val pieces = getPiecesCount() ?: return
        val imagePath = croppedImagePath ?: originalImagePath ?: return
        
        // Load image dimensions
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, options)
        
        if (options.outWidth > 0 && options.outHeight > 0) {
            val gridConfig = GridProcessor.calculateBestGrid(pieces, options.outWidth, options.outHeight)
            binding.editTextRows.setText(gridConfig.rows.toString())
            binding.editTextCols.setText(gridConfig.cols.toString())
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            photoFile = try {
                createImageFile()
            } catch (ex: Exception) {
                Log.e(TAG, "Error creating photo file", ex)
                null
            }
            
            photoFile?.also { file ->
                val photoURI = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureLauncher.launch(takePictureIntent)
            }
        }
    }

    private fun createImageFile(): File {
        val storageDir = requireContext().filesDir
        return File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            storageDir
        )
    }

    private fun navigateToPuzzleBounds(imagePath: String) {
        val action = ProjectCreationFragmentDirections.actionProjectCreationFragmentToPuzzleBoundsFragment(imagePath)
        findNavController().navigate(action)
    }

    private fun createDefaultQuad(): String {
        val defaultQuad = listOf(
            PointF(0.1f, 0.1f),
            PointF(0.9f, 0.1f),
            PointF(0.9f, 0.9f),
            PointF(0.1f, 0.9f)
        )
        val arr = JSONArray()
        for (pt in defaultQuad) {
            val p = JSONArray()
            p.put(pt.x.toDouble())
            p.put(pt.y.toDouble())
            arr.put(p)
        }
        return arr.toString()
    }

    private fun createProject() {
        val name = binding.editTextName.text.toString().trim()
        val pieces = getPiecesCount()
        val rows = getRowsCount() ?: 1
        val cols = getColsCount() ?: 1

        if (validateInput(name, pieces)) {
            viewModel.createProject(
                imagePath = originalImagePath!!,
                thumbnailPath = croppedImagePath ?: originalImagePath!!,
                name = name,
                puzzleSize = pieces!!,
                gridRows = rows,
                gridCols = cols,
                difficulty = "medium",
                puzzleQuad = currentQuadJson
            )
        }
    }

    private fun updateProject() {
        val name = binding.editTextName.text.toString().trim()
        val pieces = getPiecesCount()
        val rows = getRowsCount() ?: 1
        val cols = getColsCount() ?: 1

        if (validateInput(name, pieces) && existingProject != null) {
            val updatedProject = existingProject!!.copy(
                name = name,
                puzzleSize = pieces!!,
                gridRows = rows,
                gridCols = cols,
                imagePath = originalImagePath!!,
                thumbnailPath = croppedImagePath ?: originalImagePath!!,
                puzzleQuad = currentQuadJson
            )
            viewModel.updateProject(updatedProject)
        }
    }

    private fun getPiecesCount(): Int? {
        return try {
            binding.editTextPieces.text.toString().toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun getRowsCount(): Int? {
        return try {
            binding.editTextRows.text.toString().toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun getColsCount(): Int? {
        return try {
            binding.editTextCols.text.toString().toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun validateInput(name: String, pieces: Int?): Boolean {
        return when {
            originalImagePath == null -> {
                Toast.makeText(requireContext(), "Please take a photo first", Toast.LENGTH_SHORT).show()
                false
            }
            name.isEmpty() -> {
                Toast.makeText(requireContext(), getString(R.string.please_enter_project_name), Toast.LENGTH_SHORT).show()
                false
            }
            pieces == null -> {
                Toast.makeText(requireContext(), getString(R.string.please_enter_number_of_pieces), Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun listenForPuzzleBoundsResult() {
        setFragmentResultListener("puzzleBoundsResult") { _, bundle ->
            val quadJson = bundle.getString("quadJson")
            val croppedPath = bundle.getString("croppedImagePath")
            val cancelled = bundle.getBoolean("cancelled", false)
            
            if (cancelled) {
                if (!isEditMode) {
                    originalImagePath = null
                    croppedImagePath = null
                    currentQuadJson = null
                }
                renderPhotoState()
            } else if (quadJson != null) {
                currentQuadJson = quadJson
                croppedImagePath = croppedPath
                renderPhotoState()
                autoCalculateGrid() // Recalculate if image changed
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.projectCreated.collect { project ->
                        project?.let {
                            showSuccessAndNavigate(project)
                            viewModel.clearProjectCreated()
                        }
                    }
                }

                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            showErrorDialog(it)
                            viewModel.clearError()
                        }
                    }
                }

                launch {
                    viewModel.isProcessing.collect { isProcessing ->
                        binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showSuccessAndNavigate(project: Project) {
        val message = if (isEditMode) "Project updated!" else getString(R.string.project_created_successfully, project.name)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

        originalImagePath = null
        croppedImagePath = null
        currentQuadJson = null
        
        findNavController().navigateUp()
    }

    private fun showErrorDialog(error: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.error))
            .setMessage(error)
            .setPositiveButton(getString(R.string.ok), null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderPhotoState() {
        val imagePathToShow = croppedImagePath ?: originalImagePath
        if (imagePathToShow.isNullOrBlank()) {
            showPlaceholder()
        } else {
            showPhotoPreview(imagePathToShow)
        }
    }

    private fun showPlaceholder() {
        Glide.with(this).clear(binding.photoPreview)
        binding.photoPlaceholder.visibility = View.VISIBLE
        binding.photoPreviewCard.visibility = View.GONE
        binding.editPhotoButton.visibility = View.GONE
    }

    private fun showPhotoPreview(imagePath: String) {
        binding.photoPlaceholder.visibility = View.GONE
        binding.photoPreviewCard.visibility = View.VISIBLE
        binding.editPhotoButton.visibility = View.VISIBLE

        Glide.with(this)
            .load(File(imagePath))
            .placeholder(android.R.drawable.ic_menu_camera)
            .error(android.R.drawable.ic_menu_camera)
            .fitCenter()
            .into(binding.photoPreview)
    }

    companion object {
        private const val TAG = "ProjectCreationFragment"
    }
}
