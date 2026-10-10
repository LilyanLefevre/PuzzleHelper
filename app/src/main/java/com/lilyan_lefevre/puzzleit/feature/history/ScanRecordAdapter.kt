package com.lilyan_lefevre.puzzleit.feature.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.ItemScanRecordBinding
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecord
import com.lilyan_lefevre.puzzleit.feature.history.data.Verdict
import com.lilyan_lefevre.puzzleit.feature.puzzle.zoneRes
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** The scanned pieces of a puzzle, newest first: photo, where it was sent, and whether the person confirmed it. */
class ScanRecordAdapter(
    private val onDelete: (ScanRecord) -> Unit,
) : ListAdapter<ScanRecord, ScanRecordAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemScanRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemScanRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: ScanRecord) {
            val res = itemView.resources
            val lead = record.shownLead
            Glide.with(itemView).load(File(record.piecePath)).centerCrop().into(binding.imagePiece)
            binding.textSpot.text = lead?.let { res.getString(zoneRes(it.x, it.y)) }.orEmpty()
            binding.textDetail.text = lead?.let {
                res.getString(R.string.spot_percent, (100 * it.x).roundToInt().coerceIn(0, 100), (100 * it.y).roundToInt().coerceIn(0, 100)) +
                    " · " + res.getString(R.string.confidence_percent, it.confidence)
            }.orEmpty()
            binding.textDate.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.createdAt))
            val (label, colour) = when (record.verdict) {
                Verdict.CORRECT -> R.string.verdict_correct to R.color.puzzle_primary
                Verdict.WRONG -> R.string.verdict_wrong to R.color.puzzle_error
                else -> R.string.verdict_unknown to R.color.puzzle_on_surface_variant
            }
            binding.textVerdict.setText(label)
            binding.textVerdict.setTextColor(itemView.context.getColor(colour))
            binding.buttonDelete.setOnClickListener { onDelete(record) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ScanRecord>() {
        override fun areItemsTheSame(a: ScanRecord, b: ScanRecord) = a.id == b.id
        override fun areContentsTheSame(a: ScanRecord, b: ScanRecord) = a == b
    }
}
