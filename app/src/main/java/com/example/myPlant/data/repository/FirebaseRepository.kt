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
import com.google.firebase.firestore.FieldPath
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

    private val usersCollection = db.collection("users")

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
        val userObservationsRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .collection("observations")

        userObservationsRef.document(observationId)
            .set(observation)
            .await()

        // 6️⃣ Update plants catalog
        updatePlantsCatalog(allSuggestions)

        // 7️⃣ Add to training data if confident
        // TODO Need adjust confidence level
        if (topConfidence > 0.8 && topSuggestion != null) {
            addToTrainingDataIfConfident(observation, topSuggestion)
        }

        // 8️⃣ Update user stats
        updateUserContributionStats(currentUser.uid, "observations")


// 🆕 7.5️⃣ Add to flag queue if AI unsure (low confidence)
        if (topConfidence <= 0.7) {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val flagData = mapOf(
                    "observationId" to observationId,
                    "userId" to currentUser.uid, // ✅ Added field
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

// 🆕 7.6️⃣ Flag and log endangered species for conservation tracking
        if (!iucnCategory.isNullOrEmpty()) {
            val endangeredCategories = listOf(
                "extinct",
                "extinct in the wild",
                "critically endangered",
                "endangered"
            )
            if (iucnCategory.lowercase() in endangeredCategories) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val endangeredData = mapOf(
                        "observationId" to observationId,
                        "userId" to currentUser.uid, // ✅ Added for path lookup later
                        "scientificName" to (topSuggestion?.scientificName ?: ""),
                        "iucnCategory" to iucnCategory,
                        "flaggedBy" to currentUser.uid,
                        "flaggedAt" to Timestamp.now(),
                        "reason" to "Detected endangered species ($iucnCategory)",
                        "status" to "pending",
                        "priority" to "high"
                    )

                    // 🧩 1️⃣ Add to flagQueue for admin awareness
                    flagQueueCollection.document("endangered_$observationId")
                        .set(endangeredData, SetOptions.merge())
                        .await()

                    Log.d("FlagQueue", "🚨 Added endangered species $observationId to flagQueue")
                }
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

    private suspend fun addToTrainingDataIfConfident(
        observation: Observation,
        topSuggestion: AISuggestion
    ) {
        val scientificName = topSuggestion.scientificName
            .replace("[^A-Za-z0-9 ]".toRegex(), "_")
            .trim()
            .lowercase()

        val originalImageUrl = observation.plantImageUrls.firstOrNull()

        // 🛑 Prevent endangered species from going into trainingData
        val endangeredCategories = listOf(
            "extinct",
            "extinct in the wild",
            "critically endangered",
            "endangered"
        )
        val category = observation.iucnCategory?.lowercase()
        if (category in endangeredCategories) {
            saveEndangeredDataToFirestore(observation, source = "ai_auto_confident")
            Log.d("TrainingData", "⚠️ Skipped trainingData: $scientificName is endangered")
            return
        }

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
        val userId = currentUser.uid

        val flagInfo = FlagInfo(
            isFlagged = true,
            flaggedBy = userId,
            flaggedAt = Timestamp.now(),
            reason = reason
        )

        val docRef = db.collection("users").document(userId)
            .collection("observations").document(observationId)

        // Update observation status
        docRef.update(
            mapOf(
                "flagInfo" to flagInfo,
                "currentIdentification.status" to "flagged"
            )
        ).await()

        // Add to flag queue for admin review
        flagQueueCollection.document(observationId)
            .set(
                mapOf(
                    "userId" to userId,
                    "observationId" to observationId,
                    "flaggedAt" to Timestamp.now(),
                    "status" to "pending",
                    "reason" to reason
                ),
                SetOptions.merge()
            )
            .await()


        updateUserContributionStats(userId, "flagsSubmitted")
        return "Flag submitted for observation $observationId"
    }



    suspend fun confirmObservation(
        observationId: String,
        plantId: String,
        scientificName: String
    ): String {
        val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
        val userId = currentUser.uid

        // Prefer the user’s own observation path
        val docRef = db.collection("users").document(userId)
            .collection("observations").document(observationId)

        val snapshot = docRef.get().await()
        val observation = snapshot.toObject(Observation::class.java)
            ?: throw Exception("Observation $observationId not found")

        // Update Firestore observation
        docRef.update(
            mapOf(
                "currentIdentification.plantId" to plantId,
                "currentIdentification.scientificName" to scientificName,
                "currentIdentification.identifiedBy" to "user_confirmed",
                "currentIdentification.status" to "user_verified",
                "flagInfo" to null
            )
        ).await()

        // Handle training or endangered data
        val endangeredCategories = listOf(
            "extinct",
            "extinct in the wild",
            "critically endangered",
            "endangered"
        )

        val category = observation.iucnCategory?.lowercase()
        if (category in endangeredCategories) {
            saveEndangeredDataToFirestore(observation, source = "user_verified")
            Log.d("TrainingData", "⚠️ Skipped trainingData: $scientificName is endangered")
        } else {
            val trainingData = TrainingData(
                trainingId = UUID.randomUUID().toString(),
                plantId = plantId,
                imageUrl = observation.plantImageUrls.firstOrNull() ?: "",
                sourceType = "user_verified",
                sourceObservationId = observationId,
                verifiedBy = userId,
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

        updateUserContributionStats(userId, "verifiedIdentifications")
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

    suspend fun getAllObservations(): List<Observation> {
        return try {
            val snapshot = observationsCollection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(500)
                .get()
                .await()

            snapshot.toObjects(Observation::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching all observations: ${e.message}", e)
            emptyList()
        }
    }

    // ✅ ADD THIS FOR THE USER'S HISTORY MAP
    suspend fun getUserObservations(userId: String): List<Observation> {
        return try {
            val snapshot = usersCollection
                .document(userId)
                .collection("observations")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.toObjects(Observation::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching user observations for $userId: ${e.message}", e)
            emptyList()
        }
    }

    // ✅ Fetch pending observations for admin validation
    suspend fun fetchPendingObservations(limit: Int = 30): List<PlantObservation> {
        return try {
            val snapshot = flagQueueCollection
                .orderBy("flaggedAt", Query.Direction.ASCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val observations = mutableListOf<PlantObservation>()

            for (flag in snapshot.documents) {
                val observationId = flag.getString("observationId") ?: continue
                val userId = flag.getString("userId") ?: continue

                val path = "users/$userId/observations/$observationId"
                val docRef = db.document(path).get().await()

                if (!docRef.exists()) continue
                val data = docRef.toObject(Observation::class.java) ?: continue
                val current = data.currentIdentification

                observations.add(
                    PlantObservation(
                        id = observationId,
                        scientificName = current?.scientificName ?: "Unknown",
                        confidence = current?.confidence ?: 0.0,
                        iucnCategory = data.iucnCategory ?: "-",
                        imageUrls = data.plantImageUrls
                    )
                )
            }

            observations.sortedByDescending {
                val priority = when (it.iucnCategory?.lowercase()) {
                    "extinct" -> 9
                    "extinct in the wild" -> 8
                    "critically endangered" -> 7
                    "endangered" -> 6
                    "vulnerable" -> 5
                    "near threatened" -> 4
                    "least concern" -> 3
                    "data deficient" -> 2
                    "not evaluated" -> 1
                    else -> 0
                }
                priority + if (it.confidence < 0.5) 1 else 0
            }

        } catch (e: Exception) {
            Log.e("FirebaseRepository", "❌ Error fetching pending observations: ${e.message}", e)
            emptyList()
        }
    }


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
            val db = FirebaseFirestore.getInstance()

            // 1) Find the pending flagQueue entry for this observation
            val flagSnap = db.collection("flagQueue")
                .whereEqualTo("observationId", observationId)
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .await()

            if (flagSnap.isEmpty) {
                Log.w("AdminValidation", "No pending flagQueue entry found for $observationId")
                return false
            }

            val flagDoc = flagSnap.documents.first()
            val flagDocId = flagDoc.id
            val userId = flagDoc.getString("userId")
            if (userId.isNullOrEmpty()) {
                Log.w("AdminValidation", "flagQueue entry missing userId for $observationId")
                return false
            }

            // 2) Try to load the observation document at /users/{userId}/observations/{observationId}
            val obsDocRef = db.collection("users")
                .document(userId)
                .collection("observations")
                .document(observationId)

            val obsSnap = obsDocRef.get().await()

            // Fallback: if not found by document id, try searching by a field inside that user's observations
            val observation: Observation? = if (obsSnap.exists()) {
                obsSnap.toObject(Observation::class.java)
            } else {
                // fallback query in user's observations (in case doc id != observationId but field exists)
                val fallback = db.collection("users")
                    .document(userId)
                    .collection("observations")
                    .whereEqualTo("observationId", observationId)
                    .limit(1)
                    .get()
                    .await()

                val fallbackDoc = fallback.documents.firstOrNull()
                if (fallbackDoc != null) {
                    // replace obsDocRef with fallback doc reference
                    // (we will update fallbackDoc.reference)
                    fallbackDoc.toObject(Observation::class.java)
                } else {
                    null
                }
            }

            if (observation == null) {
                Log.w("AdminValidation", "Observation $observationId not found under user $userId")
                return false
            }

            // Determine the document reference we will update (use existing obsDocRef if exists, else fallback reference)
            val docRefToUpdate = if (obsSnap.exists()) {
                obsDocRef
            } else {
                // find fallback doc reference (we already did a query above, fetch again to obtain reference)
                val fallbackQuery = db.collection("users")
                    .document(userId)
                    .collection("observations")
                    .whereEqualTo("observationId", observationId)
                    .limit(1)
                    .get()
                    .await()
                fallbackQuery.documents.first().reference
            }

            // 3) Build update payload for the observation
            val updateData = mutableMapOf<String, Any>(
                "verifiedBy" to adminId,
                "isCorrect" to isCorrect,
                "verifiedAt" to com.google.firebase.Timestamp.now(),
                "currentIdentification.status" to if (isCorrect) "admin_verified" else "admin_corrected"
            )

            correctedScientificName?.let { updateData["currentIdentification.scientificName"] = it }
            correctedCommonName?.let { updateData["currentIdentification.commonName"] = it }

            // 4) Update the observation document
            docRefToUpdate.update(updateData).await()

            // 5) Run your endangered / training-data logic AFTER we have the observation object
            if (isCorrect) {
                val endangeredCategories = listOf(
                    "extinct", "extinct in the wild", "critically endangered", "endangered"
                )
                val category = observation.iucnCategory?.lowercase()
                if (category in endangeredCategories) {
                    // ensure this function exists and accepts Observation
                    saveEndangeredDataToFirestore(observation, source = "admin_verified")
                    Log.d("AdminValidation", "Skipped trainingData: ${observation.currentIdentification?.scientificName} is endangered")
                } else {
                    addToTrainingDataFromAdmin(
                        observation = observation,
                        adminId = adminId,
                        correctedScientificName = correctedScientificName,
                        correctedCommonName = correctedCommonName
                    )
                    handleAdminTrainingData(observation, adminId, correctedScientificName, correctedCommonName)
                }
            } else {
                // If admin corrected the identification, you may also wish to add corrected data to training.
                addToTrainingDataFromAdmin(
                    observation = observation,
                    adminId = adminId,
                    correctedScientificName = correctedScientificName,
                    correctedCommonName = correctedCommonName
                )
            }

            // 6) Update the flagQueue entry to mark as resolved + attach validator info
            db.collection("flagQueue").document(flagDocId)
                .update(
                    mapOf(
                        "status" to "resolved",
                        "validatedBy" to adminId,
                        "validatedAt" to com.google.firebase.Timestamp.now(),
                        "validationResult" to if (isCorrect) "correct" else "corrected"
                    )
                )
                .await()

            Log.d("AdminValidation", "Admin validation completed for $observationId (user: $userId)")
            true
        } catch (e: Exception) {
            Log.e("AdminValidation", "Failed admin validation for $observationId: ${e.message}", e)
            false
        }
    }


    private suspend fun addToTrainingDataFromAdmin(

        observation: Observation,
        adminId: String,
        correctedScientificName: String? = null,
        correctedCommonName: String? = null
    ) {
        val endangeredCategories = listOf(
            "extinct",
            "extinct in the wild",
            "critically endangered",
            "endangered"
        )
        val category = observation.iucnCategory?.lowercase()
        if (category in endangeredCategories) {
            saveEndangeredDataToFirestore(observation, source = "admin_verified")
            Log.d("TrainingData", "⚠️ Skipped trainingData: ${observation.currentIdentification?.scientificName} is endangered")
            return
        }

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

    // ---------- NEW HELPER: copy image to training storage and return new URL ----------
    private suspend fun copyImageToTrainingStorage(originalImageUrl: String, scientificName: String): String? {
        return try {
            val sourceRef = FirebaseStorage.getInstance().getReferenceFromUrl(originalImageUrl)
            val newFileName = "${UUID.randomUUID()}.jpg"
            val folderName = scientificName.replace("[^A-Za-z0-9_]".toRegex(), "_").lowercase()
            val destRef = storage.reference.child("trainingData/$folderName/$newFileName")

            val bytes = sourceRef.getBytes(5 * 1024 * 1024).await() // up to 5MB
            destRef.putBytes(bytes).await()
            destRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("TrainingStorage", "❌ Failed to copy image to training storage: ${e.message}", e)
            null
        }
    }

    // ---------- NEW HELPER: save endangered metadata to EndangeredData collection ----------
    private suspend fun saveEndangeredDataToFirestore(endangeredData: Map<String, Any>) {
        try {
            val docId = (endangeredData["observationId"] ?: UUID.randomUUID().toString()).toString()
            db.collection("EndangeredData")
                .document(docId)
                .set(endangeredData, SetOptions.merge())
                .await()

            Log.d("EndangeredData", "✅ Saved endangered metadata for ${docId}")
        } catch (e: Exception) {
            Log.e("EndangeredData", "❌ Failed to save endangered data to Firestore: ${e.message}", e)
        }
    }

    // ---------- NEW HELPER: central handler for admin-created training/endangered data ----------
    private suspend fun handleAdminTrainingData(
        observation: Observation,
        adminId: String,
        correctedScientificName: String? = null,
        correctedCommonName: String? = null
    ) {
        try {
            val scientificName = correctedScientificName
                ?: observation.currentIdentification?.scientificName
                ?: "unknown"

            val sanitizedScientific = scientificName
                .replace("[^A-Za-z0-9 ]".toRegex(), "_")
                .trim()
                .lowercase()

            val originalImageUrl = observation.plantImageUrls.firstOrNull()
            if (originalImageUrl.isNullOrEmpty()) {
                Log.w("TrainingData", "⚠️ No image found for ${observation.observationId}; skipping training/endangered upload")
                return
            }

            // endangered categories list
            val endangeredCategories = listOf(
                "extinct",
                "extinct in the wild",
                "critically endangered",
                "endangered"
            )

            val obsIucn = observation.iucnCategory?.lowercase()

            // If endangered -> copy image to training storage and save metadata to EndangeredData
            if (obsIucn != null && obsIucn in endangeredCategories) {
                val newImageUrl = copyImageToTrainingStorage(originalImageUrl, sanitizedScientific)

                val endangeredMap = mutableMapOf<String, Any>(
                    "observationId" to observation.observationId,
                    "adminId" to adminId,
                    "plantId" to (observation.currentIdentification?.plantId ?: ""),
                    "scientificName" to scientificName,
                    "commonName" to (correctedCommonName ?: ""),
                    "iucnCategory" to (observation.iucnCategory ?: ""),
                    "addedAt" to Timestamp.now(),
                    "status" to "flagged_endangered",
                    "source" to "admin_verified"
                )

                if (!newImageUrl.isNullOrEmpty()) endangeredMap["imageUrl"] = newImageUrl
                if (observation.geolocation != null) {
                    endangeredMap["geolocation"] = mapOf(
                        "lat" to observation.geolocation.lat,
                        "lng" to observation.geolocation.lng
                    )
                }

                saveEndangeredDataToFirestore(endangeredMap)
                return
            }

            // Not endangered -> only add to trainingData if geolocation exists
            if (observation.geolocation == null) {
                Log.d("TrainingData", "ℹ️ Observation ${observation.observationId} has no geo; skipping trainingData upload.")
                return
            }

            // copy image into training storage and save metadata to trainingData (Firestore)
            val newImageUrl = copyImageToTrainingStorage(originalImageUrl, sanitizedScientific)
            if (newImageUrl.isNullOrEmpty()) {
                Log.w("TrainingData", "⚠️ Image copy failed for ${observation.observationId}; skipping Firestore training entry")
                return
            }

            val training = TrainingData(
                trainingId = UUID.randomUUID().toString(),
                plantId = observation.currentIdentification?.plantId ?: observation.observationId,
                imageUrl = newImageUrl,
                sourceType = "admin_verified",
                sourceObservationId = observation.observationId,
                verifiedBy = adminId,
                verificationDate = Timestamp.now(),
                verificationMethod = "manual",
                confidenceScore = observation.currentIdentification?.confidence ?: 1.0,
                geolocation = observation.geolocation,
                isActive = true,
                sourceApi = "admin_verified"
            )

            // Optionally set isEndangered flag if you added that field
            // val updatedTraining = training.copy(isEndangered = false)

            saveTrainingDataToFirestore(training)
        } catch (e: Exception) {
            Log.e("TrainingData", "❌ handleAdminTrainingData failed: ${e.message}", e)
        }
    }

    private suspend fun saveTrainingDataToFirestore(trainingData: TrainingData) {
        try {
            trainingDataCollection.document(trainingData.trainingId)
                .set(trainingData, SetOptions.merge())
                .await()
            Log.d("TrainingData", "✅ Saved training data (Firestore) for ${trainingData.plantId}")
        } catch (e: Exception) {
            Log.e("TrainingData", "❌ Failed saving training data to Firestore: ${e.message}", e)
        }
    }

    // ✅ Central helper to save endangered plant data
    private suspend fun saveEndangeredDataToFirestore(observation: Observation, source: String) {
        try {
            val endangeredCategories = listOf(
                "extinct",
                "extinct in the wild",
                "critically endangered",
                "endangered"
            )

            val category = observation.iucnCategory?.lowercase()
            if (category !in endangeredCategories) return // not endangered

            val docId = "endangered_${observation.observationId}"
            val data = mutableMapOf<String, Any>(
                "observationId" to observation.observationId,
                "plantId" to (observation.currentIdentification?.plantId ?: ""),
                "scientificName" to (observation.currentIdentification?.scientificName ?: ""),
                "iucnCategory" to (observation.iucnCategory ?: "unknown"),
                "addedAt" to Timestamp.now(),
                "status" to "flagged_endangered",
                "source" to source
            )

            observation.geolocation?.let {
                data["geolocation"] = mapOf("lat" to it.lat, "lng" to it.lng)
            }

            val imageUrl = observation.plantImageUrls.firstOrNull()
            if (imageUrl != null) data["imageUrl"] = imageUrl

            db.collection("EndangeredData").document(docId)
                .set(data, SetOptions.merge())
                .await()

            Log.d("EndangeredData", "✅ Saved endangered metadata for ${observation.observationId}")

        } catch (e: Exception) {
            Log.e("EndangeredData", "❌ Failed to save endangered metadata: ${e.message}", e)
        }

        Log.i("EndangeredRedirect", "🌱 Redirected ${observation.currentIdentification?.scientificName} → EndangeredData")

    }

}
