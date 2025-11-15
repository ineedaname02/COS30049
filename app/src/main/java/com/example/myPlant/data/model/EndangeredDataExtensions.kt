package com.example.myPlant.data.model


import com.example.myPlant.R
import java.text.SimpleDateFormat
import java.util.Locale

// Extension functions for EndangeredData
fun EndangeredData.getFormattedDate(): String {
    val date = addedAt.toDate()
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(date)
}

fun EndangeredData.getLocationString(): String {
    return if (geolocation != null) {
        "Lat: ${"%.4f".format(geolocation.lat)}, Lng: ${"%.4f".format(geolocation.lng)}"
    } else {
        "No location"
    }
}

fun EndangeredData.getIucnColorRes(): Int {
    return when (iucnCategory.lowercase()) {
        "critically endangered" -> R.color.red_500
        "endangered" -> R.color.orange_500
        "extinct", "extinct in the wild" -> R.color.border_dark
        else -> R.color.amber_500
    }
}

fun EndangeredData.getDisplayName(): String {
    return if (commonName.isNotEmpty()) {
        "$commonName ($scientificName)"
    } else {
        scientificName
    }
}