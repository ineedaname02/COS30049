package com.example.myPlant.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myPlant.R
import com.example.myPlant.data.model.Observation //old - PlantObservation
import com.example.myPlant.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

// ✅ Update the adapter to use ListAdapter with Observation
class HistoryAdapter : ListAdapter<Observation, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val observation = getItem(position)
        holder.bind(observation)
    }

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        // ✅ Update the bind function to work with the Observation object
        fun bind(observation: Observation) {
            // Scientific Name
            binding.historyScientificName.text = observation.currentIdentification.scientificName.ifEmpty { "Unknown Species" }

            // Date
            binding.historyDate.text = observation.timestamp?.toDate()?.let {
                SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(it)
            } ?: "No date"

            // IUCN Status (if available)
            binding.iucnStatus.text = observation.iucnCategory ?: "IUCN Status: N/A"

            // Image
            if (observation.plantImageUrls.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .load(observation.plantImageUrls.first())
                    .placeholder(R.drawable.ic_menu_gallery) // A default placeholder
                    .into(binding.historyImage)
            } else {
                binding.historyImage.setImageResource(R.drawable.ic_menu_gallery)
            }
        }
    }

    // ✅ Update DiffUtil to compare Observation objects
    class HistoryDiffCallback : DiffUtil.ItemCallback<Observation>() {
        override fun areItemsTheSame(oldItem: Observation, newItem: Observation): Boolean {
            return oldItem.observationId == newItem.observationId
        }

        override fun areContentsTheSame(oldItem: Observation, newItem: Observation): Boolean {
            return oldItem == newItem
        }
    }
}
