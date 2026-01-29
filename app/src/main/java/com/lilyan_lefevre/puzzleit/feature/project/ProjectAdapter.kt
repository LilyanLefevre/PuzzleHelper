package com.lilyan_lefevre.puzzleit.feature.project

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.ItemProjectBinding
import com.lilyan_lefevre.puzzleit.shared.database.Project
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for displaying projects
 */
class ProjectAdapter(
    private val onProjectClick: (Project) -> Unit
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
                // Load thumbnail with Glide using placeholder and error
                Glide.with(root.context)
                    .load(project.thumbnailPath)
                    .placeholder(android.R.drawable.ic_menu_camera)
                    .error(android.R.drawable.ic_menu_camera)
                    .centerCrop()
                    .into(imageViewThumbnail)
                
                root.setOnClickListener {
                    onProjectClick(project)
                }
            }
        }

        private fun formatDate(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
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
