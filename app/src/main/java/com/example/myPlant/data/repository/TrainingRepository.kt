package com.example.myPlant.data.repository

import com.example.myPlant.data.model.AISuggestion
import com.example.myPlant.data.model.Observation
import com.example.myPlant.data.model.TrainingData
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class TrainingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val trainingDataCollection = db.collection("trainingData")

    suspend fun addHighConfidenceTrainingData(observation: Observation, topSuggestion: AISuggestion) {
        val trainingData = TrainingData(
            trainingId = UUID.randomUUID().toString(),
            plantId = topSuggestion.plantId,
            imageUrl = observation.plantImageUrls.first(),
            sourceType = "ai_high_confidence",
            sourceObservationId = observation.observationId,
            verifiedBy = "ai_system"
            //... and other fields
        )
        trainingDataCollection.document(trainingData.trainingId).set(trainingData)
    }
}
