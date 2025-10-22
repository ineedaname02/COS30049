package com.example.myPlant.data.model

import com.google.firebase.Timestamp


data class PlantObservation(
    val id: String = "",
    val userId: String = "",
    val timestamp: Timestamp? = null,
    val scientificName: String = "",
    val commonName: String = "",
    val confidence: Double = 0.0,
    val iucnCategory: String? = null,
    val imageUrls: List<String> = emptyList(),
    val location: String? = null
)
