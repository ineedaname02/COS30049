package com.example.myPlant.data.model

import com.google.firebase.Timestamp

data class EndangeredData(
    val id: String = "",
    val observationId: String = "",
    val plantId: String = "",
    val scientificName: String = "",
    val commonName: String = "",
    val imageUrl: String = "",
    val geolocation: GeoLocation? = null,
    val iucnCategory: String = "",
    val addedBy: String = "",
    val addedAt: Timestamp = Timestamp.now(),
    val notes: String = ""
)
