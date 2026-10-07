package com.lilyan_lefevre.puzzleit.feature.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.ItemProjectBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for displaying projects
 */
class ProjectAdapter(
    private val onProjectClick: (Project) -> Unit,
    private val onProjectLongClick: (Project) -> Unit = {}
) : ListAdapter<Project, ProjectAdapter.ProjectViewHolder>(ProjectDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProjectViewHolder(
        private val binding: ItemProjectBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(project: Project) {
            val piecesStr = itemView.resources.getString(R.string.pieces_name, project.puzzleSize)
            binding.apply {
                textViewProjectName.text = project.name
                textNumberPieces.text = "$piecesStr"
                textViewCreationDate.text = formatDate(project.creationDate)
                // Load thumbnail with rotation correction
                val imagePathToLoad = if (project.thumbnailPath.isNullOrEmpty()) {
                    project.imagePath
                } else {
                    project.thumbnailPath
                }
                
                // Load with rotation correction
                try {
                    val correctedBitmap = loadAndCorrectRotation(imagePathToLoad)
                    imageViewThumbnail.setImageBitmap(correctedBitmap)
                } catch (e: Exception) {
                    // Fallback to Glide
                    Glide.with(root.context)
                        .load(imagePathToLoad)
                        .placeholder(android.R.drawable.ic_menu_camera)
                        .error(android.R.drawable.ic_menu_camera)
                        .centerCrop()
                        .into(imageViewThumbnail)
                }
                
                root.setOnClickListener {
                    onProjectClick(project)
                }
                
                root.setOnLongClickListener {
                    onProjectLongClick(project)
                    true // Consume the long click event
                }
            }
        }

        private fun formatDate(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
        
        private fun loadAndCorrectRotation(imagePath: String): Bitmap {
            val originalBitmap = BitmapFactory.decodeFile(imagePath) ?: throw Exception("Failed to load image")
            
            try {
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
                    else -> return originalBitmap // No rotation needed
                }
                
                val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                if (rotatedBitmap != originalBitmap) {
                    originalBitmap.recycle()
                }
                return rotatedBitmap
            } catch (e: Exception) {
                return originalBitmap
            }
        }
    }
}

class ProjectDiffCallback : DiffUtil.ItemCallback<Project>() {
    override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: Project, newItem: Project): Any? {
        return when {
            oldItem.name != newItem.name -> "name"
            oldItem.creationDate != newItem.creationDate -> "date"
            oldItem.thumbnailPath != newItem.thumbnailPath -> "thumbnail"
            else -> null
        }
    }
}
