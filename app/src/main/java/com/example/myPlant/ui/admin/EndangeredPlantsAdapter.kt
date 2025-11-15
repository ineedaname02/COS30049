package com.example.myPlant.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myPlant.R
import com.example.myPlant.data.model.EndangeredData
import com.example.myPlant.data.model.getIucnColorRes
import com.example.myPlant.data.model.getLocationString
import com.example.myPlant.data.model.getFormattedDate

class EndangeredPlantsAdapter(
    private var plants: List<EndangeredData>,
    private val onItemClick: (EndangeredData) -> Unit = {}
) : RecyclerView.Adapter<EndangeredPlantsAdapter.EndangeredPlantViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EndangeredPlantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_endangered_plant, parent, false)
        return EndangeredPlantViewHolder(view)
    }

    override fun onBindViewHolder(holder: EndangeredPlantViewHolder, position: Int) {
        val plant = plants[position]
        holder.bind(plant)
        holder.itemView.setOnClickListener { onItemClick(plant) }
    }

    override fun getItemCount(): Int = plants.size

    fun updateList(newPlants: List<EndangeredData>) {
        plants = newPlants
        notifyDataSetChanged()
    }

    class EndangeredPlantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPlantImage: ImageView = itemView.findViewById(R.id.ivPlantImage)
        private val tvScientificName: TextView = itemView.findViewById(R.id.tvScientificName)
        private val tvCommonName: TextView = itemView.findViewById(R.id.tvCommonName)
        private val tvPlantId: TextView = itemView.findViewById(R.id.tvPlantId)
        private val tvIucnCategory: TextView = itemView.findViewById(R.id.tvIucnCategory)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvAddedBy: TextView = itemView.findViewById(R.id.tvAddedBy)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvNotes: TextView = itemView.findViewById(R.id.tvNotes)

        fun bind(plant: EndangeredData) {
            // Load image with Glide
            Glide.with(itemView.context)
                .load(plant.imageUrl)
                .placeholder(R.drawable.ic_plant_placeholder)
                .error(R.drawable.ic_plant_placeholder)
                .into(ivPlantImage)

            // Set all text values
            tvScientificName.text = plant.scientificName
            tvCommonName.text = plant.commonName.ifEmpty { "No common name" }
            tvPlantId.text = "ID: ${plant.plantId}"
            tvIucnCategory.text = plant.iucnCategory
            tvLocation.text = plant.getLocationString()
            tvAddedBy.text = "By: ${plant.addedBy}"
            tvDate.text = plant.getFormattedDate()

            // Show notes if available
            if (plant.notes.isNotEmpty()) {
                tvNotes.text = plant.notes
                tvNotes.visibility = View.VISIBLE
            } else {
                tvNotes.visibility = View.GONE
            }

            // Set IUCN category background color
            val iucnColor = ContextCompat.getColor(itemView.context, plant.getIucnColorRes())
            tvIucnCategory.setBackgroundColor(iucnColor)
        }
    }
}