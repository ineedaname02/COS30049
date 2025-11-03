package com.example.myPlant.ui.plantlocationmap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.myPlant.R
import com.example.myPlant.data.model.TrainingData
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import java.text.SimpleDateFormat
import java.util.*

class CustomInfoWindowAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

    private val window: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.custom_info_window, null)
    }

    // We return null here to use the default window frame (the white bubble).
    override fun getInfoWindow(marker: Marker): View? {
        return null
    }

    // This method populates the contents of the info window.
    override fun getInfoContents(marker: Marker): View? {
        // Get the full TrainingData object from the marker's tag.
        val trainingData = marker.tag as? TrainingData ?: return null

        // Find the views from our layout.
        val imageView = window.findViewById<ImageView>(R.id.info_window_image)
        val titleTextView = window.findViewById<TextView>(R.id.info_window_title)
        val snippetTextView = window.findViewById<TextView>(R.id.info_window_snippet)

        // --- Populate the Views ---
        titleTextView.text = marker.title
        snippetTextView.text = buildSnippet(trainingData) // Use a helper for cleanliness.

        // --- Image Loading ---
        if (trainingData.imageUrl.isNotEmpty()) {
            imageView.visibility = View.VISIBLE
            Glide.with(context).load(trainingData.imageUrl).into(imageView)
        } else {
            imageView.visibility = View.GONE // Hide if no image.
        }

        return window
    }

    // Helper function to build the multi-line snippet string.
    private fun buildSnippet(data: TrainingData): String {
        val parts = mutableListOf<String>()

        // 1. Add Verification Date
        data.verificationDate?.toDate()?.let {        parts.add("Verified: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)}")
        }

        // 2. Add Conservation Status (IUCN Category) if it exists
        if (!data.iucnCategory.isNullOrBlank()) {
            parts.add("Status: ${data.iucnCategory}")
        }
        // Fallback for older data that might only have the 'isEndangered' boolean
        else if (data.isEndangered) {
            parts.add("Status: Endangered")
        }

        // 3. Add Confidence Score if it exists
        data.confidenceScore?.let {
            // Format as a percentage with one decimal place
            parts.add(String.format(Locale.US, "Confidence: %.1f%%", it * 100))
        }

        // This joins all the parts together with a new line between each one.
        return parts.joinToString("\n")
    }
}