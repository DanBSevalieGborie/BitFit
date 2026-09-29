package com.codepath.bitfit.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codepath.bitfit.R
import com.codepath.bitfit.data.EntryEntity
import com.codepath.bitfit.databinding.ItemEntryBinding
import com.codepath.bitfit.util.Formatters
import java.io.File

class EntryAdapter(
    private val onClick: (EntryEntity) -> Unit,
) : ListAdapter<EntryEntity, EntryAdapter.EntryViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) = holder.bind(getItem(position))

    inner class EntryViewHolder(private val binding: ItemEntryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: EntryEntity) {
            val ctx = binding.root.context
            binding.foodName.text = entry.foodName
            binding.date.text = Formatters.friendlyDate(ctx, entry.epochDay)
            binding.calories.text = Formatters.number(entry.calories)

            val parts = buildList {
                add(ctx.getString(R.string.item_water, entry.waterCups))
                entry.sleepHours?.takeIf { it > 0f }?.let { add(ctx.getString(R.string.item_sleep, Formatters.oneDecimal(it))) }
                entry.mood?.let { add(ctx.getString(R.string.item_mood, Formatters.moodEmoji(it))) }
            }
            binding.metrics.text = parts.joinToString("   ")

            binding.notes.isVisible = !entry.notes.isNullOrBlank()
            binding.notes.text = entry.notes

            if (entry.photoPath != null) {
                binding.thumbnail.isVisible = true
                binding.placeholder.isVisible = false
                Glide.with(binding.thumbnail)
                    .load(File(entry.photoPath))
                    .centerCrop()
                    .into(binding.thumbnail)
            } else {
                Glide.with(binding.thumbnail).clear(binding.thumbnail)
                binding.thumbnail.isVisible = false
                binding.placeholder.isVisible = true
                binding.placeholder.text = entry.mood?.let { Formatters.moodEmoji(it) }
                    ?: entry.foodName.firstOrNull()?.uppercase() ?: "🍽"
            }
            binding.root.contentDescription = ctx.getString(
                R.string.item_content_description,
                entry.foodName, entry.calories, Formatters.friendlyDate(ctx, entry.epochDay)
            )
            binding.root.setOnClickListener { onClick(entry) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<EntryEntity>() {
        override fun areItemsTheSame(oldItem: EntryEntity, newItem: EntryEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: EntryEntity, newItem: EntryEntity) = oldItem == newItem
    }
}
