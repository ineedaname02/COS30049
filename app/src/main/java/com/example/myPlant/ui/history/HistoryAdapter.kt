package com.example.myPlant.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myPlant.data.model.PlantObservation
import com.example.myPlant.databinding.ItemHistoryBinding

class HistoryAdapter : ListAdapter<PlantObservation, HistoryAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            plantName.text = item.scientificName ?: "Unknown"
            confidence.text = "Confidence: ${"%.1f".format(item.confidence * 100)}%"
            iucnCategory.text = "IUCN: ${item.iucnCategory ?: "N/A"}"
            Glide.with(root).load(item.imageUrls.firstOrNull()).into(plantImage)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PlantObservation>() {
        override fun areItemsTheSame(old: PlantObservation, new: PlantObservation) = old.id == new.id
        override fun areContentsTheSame(old: PlantObservation, new: PlantObservation) = old == new
    }
}
