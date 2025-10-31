package com.example.myPlant.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myPlant.R
import com.example.myPlant.data.model.Observation
import com.example.myPlant.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter : ListAdapter<Observation, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(observation: Observation) {
            // ✅ Defensive null handling for currentIdentification
            val identification = observation.currentIdentification

            // Scientific name
            binding.historyScientificName.text =
                identification?.scientificName?.takeIf { it.isNotBlank() } ?: "Unknown species"

            // Date formatting
            val formattedDate = observation.timestamp?.toDate()?.let {
                SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(it)
            } ?: "Unknown date"
            binding.historyDate.text = formattedDate

            // IUCN category
            binding.iucnStatus.text = observation.iucnCategory ?: "IUCN Status: N/A"

            // Load first image (if available)
            if (!observation.plantImageUrls.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(observation.plantImageUrls.first())
                    .placeholder(R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(binding.historyImage)
            } else {
                binding.historyImage.setImageResource(R.drawable.ic_menu_gallery)
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<Observation>() {
        override fun areItemsTheSame(oldItem: Observation, newItem: Observation): Boolean {
            // ✅ Defensive check for missing IDs
            return oldItem.observationId == newItem.observationId
        }

        override fun areContentsTheSame(oldItem: Observation, newItem: Observation): Boolean {
            return oldItem == newItem
        }
    }
}
