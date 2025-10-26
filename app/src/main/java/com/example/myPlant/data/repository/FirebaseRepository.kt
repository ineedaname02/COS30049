package com.example.myPlant.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.myPlant.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.UUID
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirebaseRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val observationsCollection = db.collection("observations")
    private val trainingDataCollection = db.collection("trainingData")
    private val flagQueueCollection = db.collection("flagQueue")
    private val plantsCollection = db.collection("plants")

    /**
     * Upload a new plant observation with AI/PlantNet data and images.
     */
    suspend fun uploadPlantObservation(
        plantNetResponse: PlantNetResponse?,
        smartPlantAISuggestions: List<AISuggestion> = emptyList(),
        imageUris: List<Uri>,
        userNote: String = "",
        location: GeoLocation? = null,
        iucnCategory: String? = null
    ): String {
        val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
        if (imageUris.isEmpty()) throw Exception("No images to upload")

        // 1️⃣ Upload images
        val observationId = UUID.randomUUID().toString()
        val imageUrls = uploadImagesToStorage(observationId, imageUris)

        // 2️⃣ Convert PlantNet results → AISuggestion format
        val plantNetSuggestions = plantNetResponse?.results?.map { result ->
            AISuggestion(
                suggestionId = "plantnet_${UUID.randomUUID()}",
                source = "plantnet",
                plantId = generatePlantId(result),
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

        // 3️⃣ Merge all suggestions
        val allSuggestions = (plantNetSuggestions + smartPlantAISuggestions)
            .sortedByDescending { it.confidence }
            .take(5)

        val topSuggestion = allSuggestions.firstOrNull()
        val topConfidence = topSuggestion?.confidence ?: 0.0
        val primarySource = if (smartPlantAISuggestions.isNotEmpty()) "hybrid" else "plantnet"
        val initialStatus = if (topConfidence > 0.7) "ai_suggested" else "needs_review"

        // 4️⃣ Create Observation
        val observation = Observation(
            observationId = observationId,
            userId = currentUser.uid,
            plantImageUrls = imageUrls,
            geolocation = location,
            userNote = userNote,
            aiSuggestions = allSuggestions,
            primarySource = primarySource,
            currentIdentification = CurrentIdentification(
                plantId = topSuggestion?.plantId ?: "",
                scientificName = topSuggestion?.scientificName ?: "Unknown",
                confidence = topConfidence,
                identifiedBy = "ai",
                status = initialStatus
            ),
            iucnCategory = iucnCategory,
            timestamp = com.google.firebase.Timestamp.now()
        )

        // 5️⃣ Upload to Firestore
        observationsCollection.document(observationId)
            .set(observation)
            .await()

        // 6️⃣ Update plants catalog
        updatePlantsCatalog(allSuggestions)

        // 7️⃣ Add to training data if confident
        if (topConfidence > 0.8 && topSuggestion != null) {
            addToTrainingDataIfConfident(observation, topSuggestion)
        }

        // 8️⃣ Update user stats
        updateUserContributionStats(currentUser.uid, "observations")

        // 7️⃣ Add to training data if confident
        if (topConfidence > 0.8 && topSuggestion != null) {
            addToTrainingDataIfConfident(observation, topSuggestion)
        }

// 🆕 7.5️⃣ Add to flag queue if AI unsure (low confidence)
        if (topConfidence <= 0.7) {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val flagData = mapOf(
                    "observationId" to observationId,
                    "flaggedBy" to currentUser.uid,
                    "flaggedAt" to Timestamp.now(),
                    "reason" to "Low confidence AI prediction (${String.format("%.2f", topConfidence)})",
                    "status" to "pending",
                    "priority" to if (topConfidence < 0.4) "high" else "medium"
                )

                flagQueueCollection.document(observationId)
                    .set(flagData, SetOptions.merge())
                    .await()

                Log.d("FlagQueue", "⚠️ Added low-confidence observation $observationId to flagQueue")
            }
        }

        return observationId
    }

    private fun generatePlantId(result: Result): String {
        val scientificName = result.species?.scientificNameWithoutAuthor ?: "unknown"
        return scientificName.replace("[^A-Za-z0-9]".toRegex(), "_").lowercase()
    }

    private suspend fun uploadImagesToStorage(observationId: String, imageUris: List<Uri>): List<String> {
        val urls = mutableListOf<String>()
        val storageRef = storage.reference.child("observations/${auth.currentUser?.uid}/$observationId")

        for ((index, uri) in imageUris.withIndex()) {
            val fileRef = storageRef.child("image_$index.jpg")
            fileRef.putFile(uri).await()
            urls.add(fileRef.downloadUrl.await().toString())
        }
        return urls
    }

    private suspend fun updatePlantsCatalog(suggestions: List<AISuggestion>) {
        for (suggestion in suggestions.take(3)) {
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
            sourceApi = topSuggestion.source
        )
        trainingDataCollection.document(trainingData.trainingId)
            .set(trainingData, SetOptions.merge())
            .await()
    }

    suspend fun flagObservation(observationId: String, reason: String): String {
        val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

        val flagInfo = FlagInfo(
            isFlagged = true,
            flaggedBy = currentUser.uid,
            flaggedAt = Timestamp.now(),
            reason = reason
        )

        // Update the observation status
        observationsCollection.document(observationId)
            .update(
                mapOf(
                    "flagInfo" to flagInfo,
                    "currentIdentification.status" to "flagged"
                )
            )
            .await()

        // Add to flag queue for admin review
        flagQueueCollection.document(observationId)
            .set(
                mapOf(
                    "observationId" to observationId,
                    "flaggedBy" to currentUser.uid,
                    "flaggedAt" to Timestamp.now(),
                    "reason" to reason,
                    "status" to "pending",
                    "priority" to "medium"
                ),
                SetOptions.merge()
            )
            .await()

        // Update user stats
        updateUserContributionStats(currentUser.uid, "flagsSubmitted")

        return "Flag submitted for observation $observationId"
    }


    suspend fun confirmObservation(observationId: String, plantId: String, scientificName: String): String {
        val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

        // Update Firestore observation
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

        // Retrieve observation
        val observation = observationsCollection.document(observationId)
            .get()
            .await()
            .toObject(Observation::class.java)

        // Add to training data if available
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
                sourceApi = "user_verified"
            )

            trainingDataCollection.document(trainingData.trainingId)
                .set(trainingData, SetOptions.merge())
                .await()
        }

        // Update user contribution stats
        updateUserContributionStats(currentUser.uid, "verifiedIdentifications")

        return "Observation $observationId confirmed successfully"
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
// this block causes slowness
//    suspend fun getUserObservations(userId: String): List<PlantObservation> {
//        val collection = FirebaseFirestore.getInstance().collection("observations")
//        val query = collection.whereEqualTo("userId", userId)
//
//        val snapshot = try {
//            query.orderBy("timestamp", Query.Direction.DESCENDING).get().await()
//        } catch (e: Exception) {
//            // Fallback if index not built yet
//            query.get().await()
//        }
//
//        return snapshot.documents.mapNotNull { doc ->
//            val data = doc.data ?: return@mapNotNull null
//
//            val currentId = data["currentIdentification"] as? Map<*, *>
//            val name = currentId?.get("scientificName") as? String ?: "Unknown"
//            val confidence = (currentId?.get("confidence") as? Number)?.toDouble() ?: 0.0
//            val images = (data["plantImageUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
//            val category = data["iucnCategory"] as? String
//
//            PlantObservation(
//                id = doc.id,
//                scientificName = name,
//                confidence = confidence,
//                iucnCategory = category,
//                imageUrls = images
//            )
//        }
//    }

    suspend fun getAllObservations(): List<Observation> {
        return try {
            val snapshot = observationsCollection
                .orderBy("timestamp", Query.Direction.DESCENDING) // Get the most recent first
                .limit(500) // IMPORTANT: Limit docs to avoid high cost & slow loads
                .get()
                .await()

            // Use Firestore's automatic object mapping to convert documents.
            snapshot.toObjects(Observation::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching all observations: ${e.message}", e)
            emptyList() // Return an empty list on error to prevent crashes
        }
    }

    // ✅ ADD THIS FOR THE USER'S HISTORY MAP
    suspend fun getFullUserObservations(userId: String): List<Observation> {
        return try {
            val snapshot = observationsCollection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200) // Limit to user's 200 most recent for performance
            .get()
            .await()

            // This automatically converts documents to the full Observation data class
            snapshot.toObjects(Observation::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching full user observations: ${e.message}", e)
            emptyList() // Return an empty list on error to prevent crashes
        }
    }

    // ✅ Fetch pending observations for admin validation
    suspend fun fetchPendingObservations(limit: Int = 30): List<PlantObservation> {
        return try {
            val snapshot = flagQueueCollection
                .whereEqualTo("status", "pending")
                .orderBy("flaggedAt", Query.Direction.ASCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val flaggedIds = snapshot.documents.mapNotNull { it.getString("observationId") }
            if (flaggedIds.isEmpty()) return emptyList()

            val observations = mutableListOf<PlantObservation>()
            for (id in flaggedIds) {
                val doc = observationsCollection.document(id).get().await()
                val data = doc.toObject(Observation::class.java) ?: continue

                val current = data.currentIdentification
                observations.add(
                    PlantObservation(
                        id = id,
                        scientificName = current?.scientificName ?: "Unknown",
                        confidence = current?.confidence ?: 0.0,
                        iucnCategory = data.iucnCategory ?: "-",
                        imageUrls = data.plantImageUrls
                    )
                )
            }
            observations.sortedByDescending {
                // Prioritize endangered species first
                when (it.iucnCategory?.lowercase()) {
                    "critically endangered" -> 3
                    "endangered" -> 2
                    "vulnerable" -> 1
                    else -> 0
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching pending observations: ${e.message}", e)
            emptyList()
        }
    }

    // ✅ Process admin validation (correct / wrong)
    suspend fun processAdminValidation(
        observationId: String,
        adminId: String,
        isCorrect: Boolean,
        correctedScientificName: String? = null,
        correctedCommonName: String? = null
    ): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("observations").document(observationId)

            val updateData = hashMapOf<String, Any>(
                "verifiedBy" to adminId,
                "isCorrect" to isCorrect,
                "verifiedAt" to com.google.firebase.Timestamp.now()
            )

            if (!isCorrect) {
                correctedScientificName?.let { updateData["correctedScientificName"] = it }
                correctedCommonName?.let { updateData["correctedCommonName"] = it }
            }

            docRef.update(updateData).await()
            true  // ✅ success
        } catch (e: Exception) {
            e.printStackTrace()
            false // ❌ failed
        }
    }


}
