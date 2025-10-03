package com.example.myPlant.data.repository

import android.content.Context
import android.net.Uri
import com.example.myPlant.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val observationsCollection = db.collection("observations")
    private val trainingDataCollection = db.collection("trainingData")
    private val flagQueueCollection = db.collection("flagQueue")
    private val plantsCollection = db.collection("plants") // Master plant catalog

    suspend fun uploadPlantObservation(
        plantNetResponse: PlantNetResponse?,
        smartPlantAISuggestions: List<AISuggestion> = emptyList(), // For future AI model
        imageUris: List<Uri>,
        userNote: String = "",
        location: GeoLocation? = null
    ): kotlin.Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return kotlin.Result.failure(Exception("User not authenticated"))
            }

            if (imageUris.isEmpty()) {
                return kotlin.Result.failure(Exception("No images to upload"))
            }

            // 1. Upload images to Firebase Storage
            val observationId = UUID.randomUUID().toString()
            val imageUrls = uploadImagesToStorage(observationId, imageUris)

            // 2. Convert PlantNet response to our AISuggestion format
            val plantNetSuggestions = plantNetResponse?.results?.mapIndexed { index, result ->
                AISuggestion(
                    suggestionId = "plantnet_${UUID.randomUUID()}",
                    source = "plantnet",
                    plantId = generatePlantId(result), // Generate consistent plant ID
                    scientificName = result.species?.scientificNameWithoutAuthor ?: "Unknown",
                    commonNames = result.species?.commonNames ?: emptyList(),
                    confidence = result.score ?: 0.0,
                    externalIds = ExternalIds(
                        plantNetId = result.species?.scientificNameWithoutAuthor,
                        gbifId = result.gbif?.id?.toString(),
                        powoId = result.powo?.id
                    )
                )
            } ?: emptyList()

            // 3. Combine suggestions from both sources
            val allSuggestions = (plantNetSuggestions + smartPlantAISuggestions)
                .sortedByDescending { it.confidence }
                .take(5) // Take top 5 combined suggestions

            // 4. Determine primary source and initial status
            val primarySource = if (smartPlantAISuggestions.isNotEmpty()) "hybrid" else "plantnet"
            val topConfidence = allSuggestions.firstOrNull()?.confidence ?: 0.0
            val initialStatus = if (topConfidence > 0.7) "ai_suggested" else "needs_review"

            // 5. Create observation document
            val observation = Observation(
                observationId = observationId,
                userId = currentUser.uid,
                plantImageUrls = imageUrls,
                geolocation = location,
                userNote = userNote,
                aiSuggestions = allSuggestions,
                primarySource = primarySource,
                currentIdentification = CurrentIdentification(
                    plantId = allSuggestions.firstOrNull()?.plantId ?: "",
                    scientificName = allSuggestions.firstOrNull()?.scientificName ?: "Unknown",
                    confidence = topConfidence,
                    identifiedBy = "ai",
                    status = initialStatus
                ),
                timestamp = Timestamp.now()
            )

            // 6. Save to Firestore
            observationsCollection.document(observationId)
                .set(observation)
                .await()

            // 7. Update master plants catalog if new species
            updatePlantsCatalog(allSuggestions)

            // 8. If confidence is high, consider for training data
            if (topConfidence > 0.8) {
                addToTrainingDataIfConfident(observation, allSuggestions.first())
            }

            // 9. Update user contribution stats
            updateUserContributionStats(currentUser.uid, "observations")

            kotlin.Result.success(observationId)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    private fun generatePlantId(result: Result): String {
        // Generate a consistent plant ID from scientific name
        val scientificName = result.species?.scientificNameWithoutAuthor ?: "unknown"
        // Remove special characters and spaces, convert to lowercase
        return scientificName
            .replace("[^A-Za-z0-9]".toRegex(), "_")
            .lowercase()
    }

    private suspend fun updatePlantsCatalog(suggestions: List<AISuggestion>) {
        for (suggestion in suggestions.take(3)) { // Update top 3 suggestions
            val plantData = mapOf(
                "scientificName" to suggestion.scientificName,
                "commonNames" to suggestion.commonNames,
                "externalIds" to mapOf(
                    "gbifId" to suggestion.externalIds.gbifId,
                    "plantNetId" to suggestion.externalIds.plantNetId,
                    "powoId" to suggestion.externalIds.powoId
                ),
                "lastSeen" to Timestamp.now(),
                "suggestionCount" to com.google.firebase.firestore.FieldValue.increment(1)
            )

            plantsCollection.document(suggestion.plantId)
                .set(plantData, SetOptions.merge())
                .await()
        }
    }


    private suspend fun addToTrainingDataIfConfident(observation: Observation, topSuggestion: AISuggestion) {
        if (topSuggestion.confidence > 0.8) {
            val trainingData = TrainingData(
                trainingId = UUID.randomUUID().toString(),
                plantId = topSuggestion.plantId,
                imageUrl = observation.plantImageUrls.first(),
                sourceType = "ai_high_confidence",
                sourceObservationId = observation.observationId,
                verifiedBy = "ai_system",
                verificationDate = Timestamp.now(),
                verificationMethod = "auto_confidence",
                confidenceScore = topSuggestion.confidence,
                geolocation = observation.geolocation,
                isActive = true,
                sourceApi = topSuggestion.source // Track which API provided this suggestion
            )

            trainingDataCollection.document(trainingData.trainingId)
                .set(trainingData, SetOptions.merge())
                .await()
        }
    }

    // Other methods remain similar but updated for the new structure...
    private suspend fun uploadImagesToStorage(observationId: String, imageUris: List<Uri>): List<String> {
        val imageUrls = mutableListOf<String>()
        val storageRef = storage.reference.child("observations/${auth.currentUser?.uid}/$observationId")

        for ((index, uri) in imageUris.withIndex()) {
            val fileRef = storageRef.child("image_$index.jpg")
            fileRef.putFile(uri).await()
            val downloadUrl = fileRef.downloadUrl.await()
            imageUrls.add(downloadUrl.toString())
        }

        return imageUrls
    }

    suspend fun flagObservation(observationId: String, reason: String): kotlin.Result<Unit> {
        // Implementation remains similar...
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return kotlin.Result.failure(Exception("User not authenticated"))
            }

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
                )
                .await()

            val flagQueueItem = mapOf(
                "observationId" to observationId,
                "flaggedBy" to currentUser.uid,
                "flaggedAt" to Timestamp.now(),
                "reason" to reason,
                "status" to "pending",
                "priority" to "medium"
            )

            flagQueueCollection.document(observationId)
                .set(flagQueueItem, SetOptions.merge())
                .await()

            updateUserContributionStats(currentUser.uid, "flagsSubmitted")

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    suspend fun confirmObservation(observationId: String, plantId: String, scientificName: String): kotlin.Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return kotlin.Result.failure(Exception("User not authenticated"))
            }

            // Update observation as verified
            observationsCollection.document(observationId)
                .update(
                    mapOf(
                        "currentIdentification.plantId" to plantId,
                        "currentIdentification.scientificName" to scientificName,
                        "currentIdentification.identifiedBy" to "user_confirmed",
                        "currentIdentification.status" to "user_verified",
                        "flagInfo" to null
                    )
                )
                .await()

            // Add to training data for AI retraining
            val observation = observationsCollection.document(observationId).get().await()
                .toObject(Observation::class.java)

            if (observation != null) {
                val trainingData = TrainingData(
                    trainingId = UUID.randomUUID().toString(),
                    plantId = plantId,
                    imageUrl = observation.plantImageUrls.first(),
                    sourceType = "user_verified",
                    sourceObservationId = observationId,
                    verifiedBy = currentUser.uid,
                    verificationDate = Timestamp.now(),
                    verificationMethod = "user_confirmation",
                    confidenceScore = 1.0,
                    geolocation = observation.geolocation,
                    isActive = true,
                    sourceApi = "user_verified" // Mark as user-verified for highest quality
                )

                trainingDataCollection.document(trainingData.trainingId)
                    .set(trainingData, SetOptions.merge())
                    .await()
            }

            updateUserContributionStats(currentUser.uid, "verifiedIdentifications")

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    private suspend fun updateUserContributionStats(userId: String, field: String) {
        try {
            db.collection("userProfiles").document(userId)
                .update("contributionStats.$field", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
        } catch (e: Exception) {
            println("Failed to update user stats: ${e.message}")
        }
    }
}