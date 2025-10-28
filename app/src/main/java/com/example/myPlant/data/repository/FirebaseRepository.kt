package com.example.myPlant.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.myPlant.data.local.ObservationDao // ✅ Import DAO
import com.example.myPlant.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class FirebaseRepository(private val observationDao: ObservationDao) {

    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val observationsCollection = db.collection("observations")
    private val trainingDataCollection = db.collection("trainingData")
    private val flagQueueCollection = db.collection("flagQueue")
    private val plantsCollection = db.collection("plants")

    /**
     * Gets all observations.
     * Returns a LiveData stream directly from the local Room database.
     * In the background, it fetches fresh data from Firebase and updates the cache.
     */
    fun getAllObservations(): LiveData<List<Observation>> {
        // Immediately return the cached data as LiveData
        return observationDao.getAllObservations()
    }

    /**
     * Fetches fresh data for ALL observations from Firebase and updates the local cache.
     * This is intended to be called from the ViewModel.
     */
    suspend fun refreshAllObservations() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("Cache", "Refreshing all observations from Firebase...")
                val snapshot = observationsCollection
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(500) // Limit to a reasonable number for the global map
                    .get()
                    .await()
                val observations = snapshot.toObjects(Observation::class.java)

                // Clear the old cache and insert the new data
                observationDao.clearAll()
                observationDao.insertAll(observations)
                Log.d("Cache", "✅ Successfully cached ${observations.size} global observations.")
            } catch (e: Exception) {
                Log.e("Cache", "Error refreshing all observations: ${e.message}", e)
                // In case of error, the old cached data will remain, which is good for offline mode.
            }
        }
    }

    /**
     * Gets observations for a specific user.
     * Returns a LiveData stream directly from the user's data in the local Room database.
     */
    fun getUserObservations(userId: String): LiveData<List<Observation>> {
        return observationDao.getUserObservations(userId)
    }

    /**
     * Fetches fresh data for a SPECIFIC USER from Firebase and updates the local cache.
     */
    suspend fun refreshUserObservations(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("Cache", "Refreshing user observations for $userId from Firebase...")
                val snapshot = observationsCollection
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(200)
                    .get()
                    .await()
                val userObservations = snapshot.toObjects(Observation::class.java)

                // Note: We only clear and update the specific user's data
                observationDao.clearUserObservations(userId)
                observationDao.insertAll(userObservations)
                Log.d("Cache", "✅ Successfully cached ${userObservations.size} observations for user $userId.")
            } catch (e: Exception) {
                Log.e("Cache", "Error refreshing user observations: ${e.message}", e)
            }
        }
    }

    suspend fun fetchPendingObservations(limit: Int): List<Observation> {
        // This is a placeholder. In a real app, you would query your "flagQueue"
        // or observations where status is "needs_review".
        Log.d("Admin", "Fetching pending observations (placeholder)...")
        val snapshot = observationsCollection
            .whereIn("currentIdentification.status", listOf("needs_review", "ai_suggested"))
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get()
            .await()
        return snapshot.toObjects(Observation::class.java)
    }

    suspend fun processAdminValidation(
        observationId: String,
        adminId: String,
        isCorrect: Boolean,
        correctedScientificName: String?
    ): Boolean {
        // This is a placeholder for the real logic.
        Log.d("Admin", "Processing validation for $observationId (placeholder)...")
        // In a real implementation you would:
        // 1. Update the observation's 'currentIdentification' with the admin's input.
        // 2. Change the status to 'verified' or 'corrected'.
        // 3. Remove it from the flagQueue.
        return true // Assume success for now
    }

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
        // After successful upload, you could optionally trigger a refresh
        val userId = auth.currentUser?.uid
        if (userId != null) {
            refreshUserObservations(userId)
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

    private suspend fun addToTrainingDataIfConfident(
        observation: Observation,
        topSuggestion: AISuggestion
    ) {
        val scientificName = topSuggestion.scientificName
            .replace("[^A-Za-z0-9 ]".toRegex(), "_")
            .trim()
            .lowercase()

        val originalImageUrl = observation.plantImageUrls.firstOrNull()
        if (originalImageUrl.isNullOrEmpty()) return

        try {
            // 🔹 1️⃣ Copy image from original Storage location to trainingData/
            val sourceRef = FirebaseStorage.getInstance().getReferenceFromUrl(originalImageUrl)
            val newFileName = "${UUID.randomUUID()}.jpg"
            val destRef = storage.reference.child("trainingData/$scientificName/$newFileName")

            // Download → upload via stream
            val bytes = sourceRef.getBytes(5 * 1024 * 1024).await() // max 5MB per image
            destRef.putBytes(bytes).await()
            val newImageUrl = destRef.downloadUrl.await().toString()

            // 🔹 2️⃣ Save metadata to Firestore
            val trainingData = TrainingData(
                trainingId = UUID.randomUUID().toString(),
                plantId = topSuggestion.plantId,
                imageUrl = newImageUrl, // use the copied image URL
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

            Log.d("TrainingData", "✅ Saved training data for $scientificName")

        } catch (e: Exception) {
            Log.e("TrainingData", "⚠️ Failed to save training data for $scientificName: ${e.message}", e)
        }
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

//    suspend fun getAllObservations(): List<Observation> {
//        return try {
//            val snapshot = observationsCollection
//                .orderBy("timestamp", Query.Direction.DESCENDING) // Get the most recent first
//                .limit(500) // IMPORTANT: Limit docs to avoid high cost & slow loads
//                .get()
//                .await()
//
//            // Use Firestore's automatic object mapping to convert documents.
//            snapshot.toObjects(Observation::class.java)
//        } catch (e: Exception) {
//            Log.e("FirebaseRepository", "Error fetching all observations: ${e.message}", e)
//            emptyList() // Return an empty list on error to prevent crashes
//        }
//    }

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
//    suspend fun fetchPendingObservations(limit: Int = 30): List<PlantObservation> {
//        return try {
//            val snapshot = flagQueueCollection
//                .whereEqualTo("status", "pending")
//                .orderBy("flaggedAt", Query.Direction.ASCENDING)
//                .limit(limit.toLong())
//                .get()
//                .await()
//
//            val flaggedIds = snapshot.documents.mapNotNull { it.getString("observationId") }
//            if (flaggedIds.isEmpty()) return emptyList()
//
//            val observations = mutableListOf<PlantObservation>()
//            for (id in flaggedIds) {
//                val doc = observationsCollection.document(id).get().await()
//                val data = doc.toObject(Observation::class.java) ?: continue
//
//                val current = data.currentIdentification
//                observations.add(
//                    PlantObservation(
//                        id = id,
//                        scientificName = current?.scientificName ?: "Unknown",
//                        confidence = current?.confidence ?: 0.0,
//                        iucnCategory = data.iucnCategory ?: "-",
//                        imageUrls = data.plantImageUrls
//                    )
//                )
//            }
//            observations.sortedByDescending {
//                // Prioritize endangered species first
//                when (it.iucnCategory?.lowercase()) {
//                    "critically endangered" -> 3
//                    "endangered" -> 2
//                    "vulnerable" -> 1
//                    else -> 0
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("FirebaseRepository", "Error fetching pending observations: ${e.message}", e)
//            emptyList()
//        }
//    }

    // ✅ Process admin validation (correct / wrong)
    // ✅ Improved Admin Validation Process
    suspend fun processAdminValidation(
        observationId: String,
        adminId: String,
        isCorrect: Boolean,
        correctedScientificName: String? = null,
        correctedCommonName: String? = null
    ): Boolean {
        return try {
            val docRef = observationsCollection.document(observationId)
            val snapshot = docRef.get().await()
            val observation = snapshot.toObject(Observation::class.java)

            if (observation == null) {
                Log.w("AdminValidation", "⚠️ Observation $observationId not found")
                return false
            }

            val updateData = hashMapOf<String, Any>(
                "verifiedBy" to adminId,
                "isCorrect" to isCorrect,
                "verifiedAt" to Timestamp.now(),
                "currentIdentification.status" to if (isCorrect) "admin_verified" else "admin_corrected"
            )

            correctedScientificName?.let { updateData["currentIdentification.scientificName"] = it }
            correctedCommonName?.let { updateData["currentIdentification.commonName"] = it }

            docRef.update(updateData).await()

            // ✅ If correct, add to trainingData
            if (isCorrect) {
                addToTrainingDataFromAdmin(
                    observation = observation,
                    adminId = adminId,
                    correctedScientificName = correctedScientificName,
                    correctedCommonName = correctedCommonName
                )
            }

            // ✅ Remove from flagQueue (clean up)
            flagQueueCollection.document(observationId)
                .update("status", "resolved")
                .await()

            Log.d("AdminValidation", "✅ Admin verified observation $observationId")

            true
        } catch (e: Exception) {
            Log.e("AdminValidation", "❌ Failed admin validation: ${e.message}", e)
            false
        }
    }

    private suspend fun addToTrainingDataFromAdmin(
        observation: Observation,
        adminId: String,
        correctedScientificName: String? = null,
        correctedCommonName: String? = null
    ) {
        val scientificName = correctedScientificName
            ?.replace("[^A-Za-z0-9 ]".toRegex(), "_")
            ?.trim()
            ?.lowercase()
            ?: observation.currentIdentification?.scientificName?.lowercase()
            ?: "unknown"

        val originalImageUrl = observation.plantImageUrls.firstOrNull()
        if (originalImageUrl.isNullOrEmpty()) {
            Log.w("TrainingData", "⚠️ No image found for ${observation.observationId}")
            return
        }

        try {
            val sourceRef = FirebaseStorage.getInstance().getReferenceFromUrl(originalImageUrl)
            val newFileName = "${UUID.randomUUID()}.jpg"
            val destRef = storage.reference.child("trainingData/$scientificName/$newFileName")

            val bytes = sourceRef.getBytes(5 * 1024 * 1024).await()
            destRef.putBytes(bytes).await()
            val newImageUrl = destRef.downloadUrl.await().toString()

            val trainingData = TrainingData(
                trainingId = UUID.randomUUID().toString(),
                plantId = observation.observationId,
                imageUrl = newImageUrl,
                sourceType = "admin_verified",
                sourceObservationId = observation.observationId,
                verifiedBy = adminId,
                verificationDate = Timestamp.now(),
                verificationMethod = "manual",
                confidenceScore = observation.currentIdentification?.confidence ?: 1.0,
                geolocation = observation.geolocation,
                isActive = true
            )

            trainingDataCollection.document(trainingData.trainingId)
                .set(trainingData, SetOptions.merge())
                .await()

            Log.d("TrainingData", "✅ Added admin-verified training data for $scientificName")

        } catch (e: Exception) {
            Log.e("TrainingData", "❌ Failed to save admin training data: ${e.message}", e)
        }
    }



}
