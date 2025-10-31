package com.example.myPlant.ui.plantlocationmap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.example.myPlant.R
import com.example.myPlant.data.model.TrainingData // ✅ ADD THIS
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import java.text.SimpleDateFormat // ✅ ADD THIS
import java.util.Locale // ✅ ADD THIS

class CustomInfoWindowAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

    private val window: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.custom_info_window, null)
    }

    override fun getInfoContents(marker: Marker): View? {
        // Retrieve the TrainingData object from the marker's tag
        val trainingData = marker.tag as? TrainingData ?: return null

        // --- Use ONLY the TextViews that exist in your layout ---
        val titleTextView = window.findViewById<TextView>(R.id.info_window_title)
        val snippetTextView = window.findViewById<TextView>(R.id.info_window_snippet)

        // --- Populate the views with dynamic data ---

        // Set the scientific name as the main title
        titleTextView.text = marker.title

        // Build the snippet string piece by piece safely
        val snippetParts = mutableListOf<String>()

        // 1. Add the verification date
        trainingData.verificationDate?.toDate()?.let { date ->
            val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
            snippetParts.add("Verified: $formattedDate")
        }

        // 2. Add the source and use the 'isEndangered' boolean
        val sourceType = trainingData.sourceType.ifEmpty { "N/A" }
        // ✅ Use the isEndangered boolean that exists in your TrainingData class
        val endangeredText = if (trainingData.isEndangered) " (Endangered)" else ""
        snippetParts.add("Source: $sourceType$endangeredText")

        // 3. Add the confidence score
        trainingData.confidenceScore?.let { score ->
            val formattedConfidence = String.format(Locale.US, "%.1f%%", score * 100)
            snippetParts.add("Confidence: $formattedConfidence")
        }

        // Join all the parts together with a new line for a clean look
        snippetTextView.text = snippetParts.joinToString("\n")

        return window
    }

    override fun getInfoWindow(marker: Marker): View? {
        // Return null here to use the default info window frame.
        return null
    }
}