package com.lilyan_lefevre.puzzleit.feature.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.databinding.ItemProgressPhotoBinding
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhoto
import java.io.File
import java.text.DateFormat
import java.util.Date

/** The dated photos of a puzzle, newest first. */
class ProgressPhotoAdapter(
    private val onDelete: (ProgressPhoto) -> Unit,
) : ListAdapter<ProgressPhoto, ProgressPhotoAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemProgressPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemProgressPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: ProgressPhoto) {
            Glide.with(itemView).load(File(photo.path)).centerCrop().into(binding.imagePhoto)
            binding.textDate.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(photo.createdAt))
            binding.buttonDelete.setOnClickListener { onDelete(photo) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ProgressPhoto>() {
        override fun areItemsTheSame(a: ProgressPhoto, b: ProgressPhoto) = a.id == b.id
        override fun areContentsTheSame(a: ProgressPhoto, b: ProgressPhoto) = a == b
    }
}
