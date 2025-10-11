package com.example.myPlant

import kotlinx.serialization.Serializable

@Serializable
data class Plant(
    val id: String,
    val name: String,
    val description: String,
    // Add other fields that your Firestore 'plants' documents contain
    // For example:
    // val scientificName: String,
    // val imageUrl: String
)