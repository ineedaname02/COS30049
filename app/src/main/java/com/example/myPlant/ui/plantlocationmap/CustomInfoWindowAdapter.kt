package com.example.myPlant.ui.plantlocationmap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.example.myPlant.R
import com.example.myPlant.data.model.Observation
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import java.text.SimpleDateFormat
import java.util.*

class CustomInfoWindowAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

    // This function inflates the layout but doesn't fill the data yet.
    // It's used to provide the window's frame (background, etc.).
    override fun getInfoWindow(marker: Marker): View? {
        val view = LayoutInflater.from(context).inflate(R.layout.custom_info_window, null)
        render(marker, view)
        return view
    }

    // This function only provides the content, not the frame. We return null
    // because getInfoWindow handles everything for us.
    override fun getInfoContents(marker: Marker): View? {
        return null
    }

    // This is where we bind the data to the views.
    private fun render(marker: Marker, view: View) {
        val titleView = view.findViewById<TextView>(R.id.info_title)
        val iucnView = view.findViewById<TextView>(R.id.info_iucn_status)
        val dateView = view.findViewById<TextView>(R.id.info_date)

        titleView.text = marker.title

        // Retrieve the full Observation object we stored in the marker's tag.
        val observation = marker.tag as? Observation
        if (observation == null) {
            iucnView.text = "Status: N/A"
            dateView.text = "No date available"
            return
        }

        // Set the IUCN status text
        iucnView.text = observation.iucnCategory?.let { "Status: $it" } ?: "Status: N/A"

        // Set the date text
        dateView.text = observation.timestamp?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(it)
        } ?: "No date available"
    }
}
