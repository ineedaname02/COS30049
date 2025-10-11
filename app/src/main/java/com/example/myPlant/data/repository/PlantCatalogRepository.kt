package com.example.myPlant.data.repository

import com.example.myPlant.data.model.AISuggestion
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

/**
 * Manages the master 'plants' catalog in Firestore.
 */
class PlantCatalogRepository {

    private val db = FirebaseFirestore.getInstance()
    private val plantsCollection = db.collection("plants")

    suspend fun updateCatalogFromSuggestions(suggestions: List<AISuggestion>) {
        // Only process the top 3 suggestions to avoid spamming the catalog
        for (suggestion in suggestions.take(3)) {
            val plantData = mapOf(
                "scientificName" to suggestion.scientificName,
                "commonNames" to suggestion.commonNames,
                "lastSeen" to Timestamp.now(), // Use client-side timestamp for consistency here
                "suggestionCount" to FieldValue.increment(1)
            )
            plantsCollection.document(suggestion.plantId)
                .set(plantData, SetOptions.merge())
            // We don't need to await() if we don't need to block execution
        }
    }
}
