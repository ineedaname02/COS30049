package com.example.myPlant.data.repository

import android.content.Context
import android.net.Uri
import com.example.myPlant.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.*

class ObservationRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val observationsCollection = db.collection("observations")
    private val trainingDataCollection = db.collection("trainingData")
    private val flagQueueCollection = db.collection("flagQueue")
    private val plantsCollection = db.collection("plants")

    // --- Function to get all observations for the map ---
    suspend fun getAllObservations(): List<Map<String, Any>> {
        return try {
            val snapshot = observationsCollection
                .whereEqualTo("currentIdentification.status", "user_verified") // Example filter
                .get()
                .await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Function to upload a new observation ---
    suspend fun uploadPlantObservation(
        plantNetResponse: PlantNetResponse?,
        imageUris: List<Uri>,
        location: GeoLocation?
    ): kotlin.Result<String> {
        return try {
            val currentUser = auth.currentUser ?: return kotlin.Result.failure(Exception("User not authenticated"))
            if (imageUris.isEmpty()) return kotlin.Result.failure(Exception("No images to upload"))

            val observationId = UUID.randomUUID().toString()
            val imageUrls = uploadImagesToStorage(observationId, imageUris)
            val allSuggestions = createSuggestionsFrom(plantNetResponse)
                .sortedByDescending { it.confidence }
                .take(5)

            val topConfidence = allSuggestions.firstOrNull()?.confidence ?: 0.0
            val initialStatus = if (topConfidence > 0.7) "ai_suggested" else "needs_review"

            val observation = Observation(
                observationId = observationId,
                userId = currentUser.uid,
                plantImageUrls = imageUrls,
                geolocation = location,
                aiSuggestions = allSuggestions,
                currentIdentification = CurrentIdentification(
                    plantId = allSuggestions.firstOrNull()?.plantId ?: "",
                    scientificName = allSuggestions.firstOrNull()?.scientificName ?: "Unknown",
                    confidence = topConfidence,
                    status = initialStatus
                ),
                timestamp = Timestamp.now()
            )

            observationsCollection.document(observationId).set(observation).await()
            updatePlantsCatalog(allSuggestions) // Update master plant list

            kotlin.Result.success(observationId)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    // --- Function to handle user confirming an AI suggestion ---
    suspend fun confirmObservation(observationId: String, plantId: String, scientificName: String): kotlin.Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return kotlin.Result.failure(Exception("User not authenticated"))

            observationsCollection.document(observationId)
                .update(
                    mapOf(
                        "currentIdentification.plantId" to plantId,
                        "currentIdentification.scientificName" to scientificName,
                        "currentIdentification.identifiedBy" to "user_confirmed",
                        "currentIdentification.status" to "user_verified",
                        "flagInfo" to null
                    )
                ).await()

            // You can add logic here to create training data
            updateUserContributionStats(currentUser.uid, "verifiedIdentifications")
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    // --- Function to handle user flagging a result as wrong ---
    suspend fun flagObservation(observationId: String, reason: String): kotlin.Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return kotlin.Result.failure(Exception("User not authenticated"))
            val flagInfo = FlagInfo(
                isFlagged = true,
                flaggedBy = currentUser.uid,
                flaggedAt = Timestamp.now(),
                reason = reason
            )
            observationsCollection.document(observationId)
                .update(
                    mapOf(
                        "flagInfo" to flagInfo,
                        "currentIdentification.status" to "flagged"
                    )
                ).await()
            updateUserContributionStats(currentUser.uid, "flagsSubmitted")
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    // --- Helper function to convert PlantNet response ---
    fun createSuggestionsFrom(response: PlantNetResponse?): List<AISuggestion> {
        return response?.results?.map { result ->
            AISuggestion(
                suggestionId = "plantnet_${UUID.randomUUID()}",
                source = "plantnet",
                plantId = generatePlantId(result.species?.scientificNameWithoutAuthor),
                scientificName = result.species?.scientificNameWithoutAuthor ?: "Unknown",
                commonNames = result.species?.commonNames ?: emptyList(),
                confidence = result.score ?: 0.0
            )
        } ?: emptyList()
    }

    // --- Helper Functions (previously in FirebaseRepository) ---
    private fun generatePlantId(scientificName: String?): String {
        return scientificName?.replace("[^A-Za-z0-9]".toRegex(), "_")?.lowercase() ?: "unknown"
    }

    private suspend fun uploadImagesToStorage(observationId: String, imageUris: List<Uri>): List<String> {
        val imageUrls = mutableListOf<String>()
        val storageRef = storage.reference.child("observations/${auth.currentUser?.uid}/$observationId")
        imageUris.forEachIndexed { index, uri ->
            val fileRef = storageRef.child("image_$index.jpg")
            fileRef.putFile(uri).await()
            imageUrls.add(fileRef.downloadUrl.await().toString())
        }
        return imageUrls
    }

    private suspend fun updatePlantsCatalog(suggestions: List<AISuggestion>) {
        suggestions.take(3).forEach { suggestion ->
            val plantData = mapOf(
                "scientificName" to suggestion.scientificName,
                "commonNames" to suggestion.commonNames,
                "lastSeen" to Timestamp.now(),
                "suggestionCount" to FieldValue.increment(1)
            )
            plantsCollection.document(suggestion.plantId).set(plantData, SetOptions.merge()).await()
        }
    }

    private suspend fun updateUserContributionStats(userId: String, field: String) {
        db.collection("userProfiles").document(userId)
            .update("contributionStats.$field", FieldValue.increment(1)).await()
    }
}
