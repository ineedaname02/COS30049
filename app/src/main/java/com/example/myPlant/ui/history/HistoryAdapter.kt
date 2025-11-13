package com.example.myPlant.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

    private var onItemClickListener: ((Observation) -> Unit)? = null

    fun setOnItemClickListener(listener: (Observation) -> Unit) {
        onItemClickListener = listener
    }

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
            binding.root.setOnClickListener {
                onItemClickListener?.invoke(observation)
            }

            bindIdentificationInfo(observation)
            bindImages(observation)
            bindLocationInfo(observation)
            bindConfidenceScore(observation)
            bindIdentificationSource(observation)
        }

        private fun bindIdentificationInfo(observation: Observation) {
            val identification = observation.currentIdentification

            // Scientific name
            binding.historyScientificName.text =
                identification?.scientificName?.takeIf { it.isNotBlank() } ?: "Unknown species"

            // Common name
            binding.historyCommonName.text =
                identification?.commonName?.takeIf { it.isNotBlank() } ?: "No common name"
            binding.historyCommonName.visibility =
                if (identification?.commonName.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        private fun bindIdentificationSource(observation: Observation) {
            val identification = observation.currentIdentification
            val identifiedBy = identification?.identifiedBy ?: "ai"

            // Set identification source text
            binding.identificationSource.text = when (identifiedBy) {
                "admin" -> "Verified by Expert"
                "user_confirmed" -> "Confirmed by User"
                "community" -> "Community ID"
                else -> "AI Identification"
            }
        }

        private fun bindImages(observation: Observation) {
            if (!observation.plantImageUrls.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(observation.plantImageUrls.first())
                    .placeholder(R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(binding.historyImage)

                // Show image count badge if multiple images
                if (observation.plantImageUrls.size > 1) {
                    binding.imageCountBadge.text = "+${observation.plantImageUrls.size - 1}"
                    binding.imageCountBadge.visibility = View.VISIBLE
                } else {
                    binding.imageCountBadge.visibility = View.GONE
                }
            } else {
                binding.historyImage.setImageResource(R.drawable.ic_menu_gallery)
                binding.imageCountBadge.visibility = View.GONE
            }
        }

        private fun bindLocationInfo(observation: Observation) {
            // Date formatting
            val formattedDate = observation.timestamp?.toDate()?.let {
                SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(it)
            } ?: "Unknown date"
            binding.historyDate.text = formattedDate

            // Location
            val location = observation.locationName
            if (!location.isNullOrBlank()) {
                binding.historyLocation.text = location
                binding.historyLocation.visibility = View.VISIBLE
            } else {
                binding.historyLocation.visibility = View.GONE
            }
        }

        private fun bindConfidenceScore(observation: Observation) {
            val identification = observation.currentIdentification
            val confidence = identification?.confidence ?: 0.0

            binding.confidenceScore.text = "%.1f%%".format(confidence * 100)

            // Color code based on confidence
            val colorRes = when {
                confidence > 0.8 -> R.color.high_confidence
                confidence > 0.5 -> R.color.medium_confidence
                else -> R.color.low_confidence
            }
            binding.confidenceScore.setTextColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )

            // IUCN status
            val iucnCategory = observation.iucnCategory
            binding.iucnStatus.text = iucnCategory ?: "Status: Unknown"

            // Color code IUCN status
            val iucnColorRes = when (iucnCategory?.lowercase()) {
                "extinct" -> R.color.iucn_extinct
                "critically endangered" -> R.color.iucn_critically_endangered
                "endangered" -> R.color.iucn_endangered
                "vulnerable" -> R.color.iucn_vulnerable
                "near threatened" -> R.color.iucn_near_threatened
                "least concern" -> R.color.iucn_least_concern
                "data deficient" -> R.color.iucn_data_deficient
                else -> R.color.iucn_data_deficient
            }
            binding.iucnStatus.setTextColor(
                ContextCompat.getColor(binding.root.context, iucnColorRes)
            )
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<Observation>() {
        override fun areItemsTheSame(oldItem: Observation, newItem: Observation): Boolean {
            return oldItem.observationId == newItem.observationId
        }

        override fun areContentsTheSame(oldItem: Observation, newItem: Observation): Boolean {
            return oldItem == newItem
        }
    }
}