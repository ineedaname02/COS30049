package com.example.myPlant.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.myPlant.data.model.*
import com.example.myPlant.data.encryption.BiodiversityEncryptionService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.tasks.await
import java.util.UUID
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue

class FirebaseRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val endangeredDataCollection = db.collection("EndangeredData")

    private val observationsCollection = db.collection("observations")
    private val trainingDataCollection = db.collection("trainingData")
    private val flagQueueCollection = db.collection("flagQueue")
    private val plantsCollection = db.collection("plants")

    private val usersCollection = db.collection("users")

    /**
     * Upload a new plant observation with AI/PlantNet data and images.
     */

    private val encryptionService = BiodiversityEncryptionService(context)
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
        val initialStatus = if (topConfidence > 0.8) "ai_suggested" else "needs_review"

        // 4️⃣ Extract common name and family from the PlantNet response
        val topPlantNetResult = plantNetResponse?.results?.firstOrNull()
        val commonName = topPlantNetResult?.species?.commonNames?.firstOrNull() ?: ""
        val family = topPlantNetResult?.species?.family?.scientificNameWithoutAuthor ?: ""

        // 5️⃣ Create Observation with all the new fields
        val encryptedUserNote = encryptionService.encrypt(userNote)

        // Only encrypt IUCN if it's endangered
        val (publicIUCN, encryptedIUCN) = if (encryptionService.shouldEncryptIUCN(iucnCategory)) {
            Pair("Protected Species", encryptionService.encrypt(iucnCategory!!))
        } else {
            Pair(iucnCategory, null)
        }

        // Encrypt location (always encrypt exact coordinates)
        val encryptedLocation = location?.let {
            encryptionService.encrypt("${it.lat},${it.lng}")
        }

        // Create generalized location for public view
        val generalLocation = location?.let {
            "Area: ${"%.2f".format(it.lat)}, ${"%.2f".format(it.lng)}"
        }

        // 5️⃣ Create Observation with encrypted fields
        val observation = Observation(
            observationId = observationId,
            userId = currentUser.uid,
            plantImageUrls = imageUrls,
            geolocation = location, // Keep original for now (we'll modify structure)
            userNote = encryptedUserNote, // ✅ Encrypted
            locationName = generalLocation, // ✅ Generalized for public view

            aiSuggestions = allSuggestions,
            primarySource = primarySource,
            currentIdentification = CurrentIdentification(
                plantId = topSuggestion?.plantId ?: "",
                scientificName = topSuggestion?.scientificName ?: "Unknown",
                commonName = commonName,
                family = family,
                confidence = topConfidence,
                identifiedBy = "ai",
                status = initialStatus
            ),
            iucnCategory = publicIUCN, // ✅ Public version (generalized)
            encryptedIUCN = encryptedIUCN, // ✅ Add this new field for encrypted version
            timestamp = com.google.firebase.Timestamp.now()
        )

        // 6️⃣ Upload to Firestore
        val userObservationsRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .collection("observations")

        userObservationsRef.document(observationId)
            .set(observation)
            .await()

        // 7️⃣ Update plants catalog
        updatePlantsCatalog(allSuggestions)

        // 8️⃣ Add to training data if confident
        if (topConfidence > 0.8 && topSuggestion != null) {
            addToTrainingDataIfConfident(observation, topSuggestion)
        }

        // 9️⃣ Update user stats
        updateUserContributionStats(currentUser.uid, "observations")

        // 🔟 Add to flag queue if AI unsure (low confidence)
        // 🔟 Add to flag queue only for low-confidence, non-endangered AI predictions
        val endangeredCategories = listOf("extinct", "extinct in the wild", "critically endangered", "endangered")
        val isEndangered = !iucnCategory.isNullOrEmpty() && iucnCategory.lowercase() in endangeredCategories

        if (topConfidence <= 0.8 && !isEndangered) {
            val flagDocId = "${currentUser.uid}_$observationId"
            val flagData = mapOf(
                "observationId" to observationId,
                "userId" to currentUser.uid,
                "flaggedBy" to currentUser.uid,
                "flaggedAt" to Timestamp.now(),
                "reason" to "Low confidence AI prediction (${String.format("%.2f", topConfidence)})",
                "status" to "pending",
                "priority" to if (topConfidence < 0.5) "high" else "medium"
            )

            flagQueueCollection.document(flagDocId)
                .set(flagData, SetOptions.merge())
                .await()
        }

        if (!iucnCategory.isNullOrEmpty()) {
            // 🔟 Add to flag queue ONLY for low-confidence (< 0.5), non-endangered AI predictions
            val endangeredCategories = listOf("extinct", "extinct in the wild", "critically endangered", "endangered")
            val isEndangered = !iucnCategory.isNullOrEmpty() && iucnCategory.lowercase() in endangeredCategories

// 🚨 ONLY add to flagQueue for confidence < 0.5 (not ≤ 0.8)
            if (topConfidence < 0.5 && !isEndangered) {
                val flagDocId = "${currentUser.uid}_$observationId"
                val flagData = mapOf(
                    "observationId" to observationId,
                    "userId" to currentUser.uid,
                    "flaggedBy" to currentUser.uid,
                    "flaggedAt" to Timestamp.now(),
                    "reason" to "Low confidence AI prediction (${String.format("%.2f", topConfidence)})",
                    "status" to "pending",
                    "priority" to "high" // All low confidence gets high priority
                )

                flagQueueCollection.document(flagDocId)
                    .set(flagData, SetOptions.merge())
                    .await()

                Log.d("FlagQueue", "⚠️ Added low-confidence observation $observationId to flagQueue")
            }
        }


        return observationId
    }

    suspend fun getDecryptedUserObservations(userId: String): List<Observation> {
        val observations = getUserObservations(userId)
        return observations.map { observation ->
            decryptObservationForUser(observation, userId)
        }
    }

    suspend fun getDecryptedObservationsForAdmin(userId: String? = null): List<Observation> {
        val observations = if (userId != null) {
            getUserObservations(userId)
        } else {
            getAllObservations()
        }

        return observations.map { observation ->
            decryptObservationForAdmin(observation)
        }
    }

    private fun decryptObservationForUser(observation: Observation, currentUserId: String): Observation {
        return if (observation.userId == currentUserId) {
            // User owns this - decrypt their data
            observation.copy(
                userNote = encryptionService.decrypt(observation.userNote),
                iucnCategory = observation.encryptedIUCN?.let { encryptionService.decrypt(it) } ?: observation.iucnCategory
            )
        } else {
            // User viewing someone else's data - show public data only
            observation.copy(
                userNote = "", // Hide encrypted notes
                geolocation = null, // Hide exact location
                iucnCategory = observation.iucnCategory // Show public version only
            )
        }
    }

    private fun decryptObservationForAdmin(observation: Observation): Observation {
        // Admin can decrypt everything
        return observation.copy(
            userNote = encryptionService.decrypt(observation.userNote),
            iucnCategory = observation.encryptedIUCN?.let { encryptionService.decrypt(it) } ?: observation.iucnCategory
        )
    }

    /**
     * Check if current user is admin
     */
    private suspend fun isCurrentUserAdmin(): Boolean {
        val currentUser = auth.currentUser ?: return false
        return try {
            val userDoc = db.collection("users").document(currentUser.uid).get().await()
            userDoc.getBoolean("isAdmin") ?: false
        } catch (e: Exception) {
            false
        }
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
        val scientificName = sanitizePlantName(topSuggestion.scientificName)
        val originalImageUrl = observation.plantImageUrls.firstOrNull()

        if (originalImageUrl.isNullOrEmpty()) return

        val endangeredCategories = listOf(
            "extinct", "extinct in the wild", "critically endangered", "endangered"
        )
        val category = observation.iucnCategory?.lowercase()
        val isEndangered = category != null && endangeredCategories.contains(category)

        try {
            // ✅ ALWAYS copy image to storage first
            val newImageUrl = copyImageToTrainingStorage(originalImageUrl, scientificName)
            if (newImageUrl.isNullOrEmpty()) return

            if (isEndangered) {
                // ✅ Use the renamed function
                saveEndangeredDataFromObservation(observation, source = "ai_high_confidence")
                Log.d("TrainingData", "🌱 Saved endangered species to EndangeredData: $scientificName")
            } else {
                val trainingData = TrainingData(
                    trainingId = UUID.randomUUID().toString(),
                    plantId = topSuggestion.plantId,
                    imageUrl = newImageUrl, // Use the copied image URL
                    // 🚨 MISSING FIELDS - need to add the rest
                    sourceType = "ai_high_confidence",
                    sourceObservationId = observation.observationId,
                    verifiedBy = "ai_system",
                    verificationDate = Timestamp.now(),
                    verificationMethod = "auto_confidence",
                    confidenceScore = topSuggestion.confidence,
                    iucnCategory = observation.iucnCategory,
                    isActive = true,
                    sourceApi = topSuggestion.source,
                    geolocation = observation.geolocation
                )
                trainingDataCollection.document(trainingData.trainingId)
                    .set(trainingData, SetOptions.merge())
                    .await()
                Log.d("TrainingData", "✅ Saved training data for $scientificName")
            }

        } catch (e: Exception) {
            Log.e("TrainingData", "⚠️ Failed to process data for $scientificName: ${e.message}", e)
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
        val flagDocId = "${userId}_$observationId" // stable ID
        val flagData = mapOf(
            "observationId" to observationId,
            "userId" to userId,
            "flaggedAt" to Timestamp.now(),
            "status" to "pending",
            "flaggedBy" to FieldValue.arrayUnion(userId), // track multiple flaggers
            "reason" to FieldValue.arrayUnion(reason) // keep AI + user reasons together
        )

        flagQueueCollection.document(flagDocId)
            .set(flagData, SetOptions.merge())
            .await()



        updateUserContributionStats(userId, "flagsSubmitted")
        return "Flag submitted for observation $observationId"
    }

    suspend fun confirmObservation(
        observationId: String,
        plantId: String,
        scientificName: String,
        commonName: String = "",
        family: String = ""
    ): String {
        val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
        val userId = currentUser.uid

        val docRef = db.collection("users").document(userId)
            .collection("observations").document(observationId)

        val snapshot = docRef.get().await()
        val observation = snapshot.toObject(Observation::class.java)
            ?: throw Exception("Observation $observationId not found")

        // Update Firestore observation with all fields (KEEP THIS UNCHANGED)
        docRef.update(
            mapOf(
                "currentIdentification.plantId" to plantId,
                "currentIdentification.scientificName" to scientificName,
                "currentIdentification.commonName" to commonName,
                "currentIdentification.family" to family,
                "currentIdentification.identifiedBy" to "user_confirmed",
                "currentIdentification.status" to "user_verified",
                "flagInfo" to null
            )
        ).await()

        // Handle endangered species first (KEEP THIS UNCHANGED)
        val endangeredCategories = listOf(
            "extinct",
            "extinct in the wild",
            "critically endangered",
            "endangered"
        )
        val category = observation.iucnCategory?.lowercase()
        if (category in endangeredCategories) {
            saveEndangeredDataFromObservation(observation, source = "user_verified")
            Log.d("TrainingData", "⚠️ Skipped trainingData: $scientificName is endangered")
            return "Observation $observationId confirmed and redirected to EndangeredData"
        }

        // Determine AI confidence (KEEP THIS UNCHANGED)
        val confidence = observation.currentIdentification?.confidence ?: 1.0

        if (confidence < 0.5) {
            // 🔸 Low confidence → add to flagQueue for admin verification (KEEP THIS UNCHANGED)
            val flagData = mapOf(
                "observationId" to observationId,
                "userId" to userId,
                "scientificName" to scientificName,
                "confidence" to confidence,
                "status" to "pending",
                "flaggedAt" to Timestamp.now(),
                "reason" to "low_confidence_user_confirmed"
            )

            val flagDocId = "${userId}_${observationId}"
            db.collection("flagQueue").document(flagDocId)
                .set(flagData, SetOptions.merge())
                .await()

            Log.d("FlagQueue", "⚠️ Added $scientificName to flagQueue (confidence=$confidence)")

            // 🚨 DON'T copy image to storage yet - wait for admin verification
            Log.d("TrainingData", "⏳ Waiting for admin verification before copying image to storage: $scientificName")

        } else {
            // ✅ High confidence (≥ 0.5) user confirmed → add to trainingData AND copy to storage
            val originalImageUrl = observation.plantImageUrls.firstOrNull()

            if (!originalImageUrl.isNullOrEmpty()) {
                try {
                    val sanitizedName = sanitizePlantName(scientificName)
                    val newImageUrl = copyImageToTrainingStorage(originalImageUrl, sanitizedName)

                    if (!newImageUrl.isNullOrEmpty()) {
                        val trainingData = TrainingData(
                            trainingId = UUID.randomUUID().toString(),
                            plantId = plantId,
                            imageUrl = newImageUrl, // ✅ Use the COPIED image URL
                            sourceType = "user_verified",
                            sourceObservationId = observationId,
                            verifiedBy = userId,
                            verificationDate = Timestamp.now(),
                            verificationMethod = "user_confirmation",
                            confidenceScore = confidence,
                            geolocation = observation.geolocation,
                            isActive = true,
                            sourceApi = "user_verified"
                        )

                        trainingDataCollection.document(trainingData.trainingId)
                            .set(trainingData, SetOptions.merge())
                            .await()

                        Log.d("TrainingData", "✅ Saved high-confidence user-confirmed training data to Firestore AND Storage for $scientificName")
                    }
                } catch (e: Exception) {
                    Log.e("TrainingData", "❌ Failed to copy image for user-confirmed observation: ${e.message}", e)

                    // Fallback: Save without copied image
                    val trainingData = TrainingData(
                        trainingId = UUID.randomUUID().toString(),
                        plantId = plantId,
                        imageUrl = originalImageUrl,
                        sourceType = "user_verified",
                        sourceObservationId = observationId,
                        verifiedBy = userId,
                        verificationDate = Timestamp.now(),
                        verificationMethod = "user_confirmation",
                        confidenceScore = confidence,
                        geolocation = observation.geolocation,
                        isActive = true,
                        sourceApi = "user_verified"
                    )
                    trainingDataCollection.document(trainingData.trainingId)
                        .set(trainingData, SetOptions.merge())
                        .await()
                }
            }
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

    // ✅ Fetch pending observations for admin validation WITH PRIORITIZATION
    suspend fun fetchPendingObservations(limit: Int = 30): List<PlantObservation> {
        return try {
            val snapshot = flagQueueCollection
                .whereEqualTo("status", "pending")
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

                // 🚨 Get priority and type from flag document
                val priority = flag.getString("priority") ?: "medium"
                val isEndangered = flag.getString("type") == "endangered"

                observations.add(
                    PlantObservation(
                        id = observationId,
                        scientificName = current?.scientificName ?: "Unknown",
                        confidence = current?.confidence ?: 0.0,
                        iucnCategory = data.iucnCategory ?: "-",
                        imageUrls = data.plantImageUrls,
                        priority = priority, // 🚨 Include priority for sorting
                        isEndangered = isEndangered // 🚨 Mark if endangered
                    )
                )
            }

            // 🚨 MANUAL PRIORITIZATION: Critical first, then endangered, then by confidence
            observations.sortedWith(compareByDescending<PlantObservation> {
                // First: Priority level
                when (it.priority) {
                    "critical" -> 10
                    "high" -> 8
                    "medium" -> 5
                    "low" -> 2
                    else -> 1
                }
            }.thenByDescending {
                // Second: Endangered plants get extra priority
                if (it.isEndangered) 5 else 0
            }.thenByDescending {
                // Third: IUCN category severity
                when (it.iucnCategory?.lowercase()) {
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
            }.thenBy {
                // Fourth: Lower confidence = higher priority for review
                it.confidence
            })

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
        correctedCommonName: String? = null,
        correctedFamily: String? = null // ✅ Add family parameter
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
                "currentIdentification.status" to if (isCorrect) "admin_verified" else "admin_corrected",
                "currentIdentification.identifiedBy" to "admin" // ✅ Set identifiedBy
            )

            correctedScientificName?.let { updateData["currentIdentification.scientificName"] = it }
            correctedCommonName?.let { updateData["currentIdentification.commonName"] = it }
            correctedFamily?.let { updateData["currentIdentification.family"] = it }

            // 4) Update the observation document
            docRefToUpdate.update(updateData).await()

            // 5) Run your endangered / training-data logic AFTER we have the observation object
            if (isCorrect) {
                val endangeredCategories = listOf(
                    "extinct", "extinct in the wild", "critically endangered", "endangered"
                )
                val category = observation.iucnCategory?.lowercase()
                if (category in endangeredCategories) {
                    saveEndangeredDataFromObservation(observation, source = "admin_verified", adminId = adminId) // ✅ Updated name
                    Log.d("AdminValidation", "Skipped trainingData: ${observation.currentIdentification?.scientificName} is endangered")
                } else {
                    // ✅ KEEP ONLY THIS ONE - it handles both training data and image storage
                    handleAdminTrainingData(observation, adminId, correctedScientificName, correctedCommonName)
                }
            } else {
                // ✅ KEEP ONLY THIS ONE - for corrected identifications
                handleAdminTrainingData(observation, adminId, correctedScientificName, correctedCommonName)
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

            db.collection("flagQueue").document(flagDocId).delete().await()

            Log.d("AdminValidation", "Admin validation completed for $observationId (user: $userId)")
            true
        } catch (e: Exception) {
            Log.e("AdminValidation", "Failed admin validation for $observationId: ${e.message}", e)
            false
        }
    }

    // ---------- NEW HELPER: copy image to training storage and return new URL ----------
    private suspend fun copyImageToTrainingStorage(originalImageUrl: String, scientificName: String): String? {
        return try {
            val sourceRef = storage.getReferenceFromUrl(originalImageUrl)
            val newFileName = "${UUID.randomUUID()}.jpg"
            val folderName = sanitizePlantName(scientificName)
            val destRef = storage.reference.child("trainingData/$folderName/$newFileName")

            val bytes = sourceRef.getBytes(5 * 1024 * 1024).await()
            destRef.putBytes(bytes).await()
            destRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("TrainingStorage", "❌ Failed to copy image to training storage: ${e.message}", e)
            null
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
            val scientificName = correctedScientificName ?: observation.currentIdentification?.scientificName ?: "unknown"
            val sanitizedScientific = sanitizePlantName(scientificName)
            val originalImageUrl = observation.plantImageUrls.firstOrNull()

            if (originalImageUrl.isNullOrEmpty()) {
                Log.w("TrainingData", "⚠️ No image found; skipping training upload")
                return
            }

            val endangeredCategories = listOf("extinct", "extinct in the wild", "critically endangered", "endangered")
            val obsIucn = observation.iucnCategory?.lowercase()
            val isEndangered = obsIucn != null && endangeredCategories.contains(obsIucn)

            // 🚨 SKIP ENDANGERED PLANTS - they are handled by saveEndangeredDataFromObservation
            if (isEndangered) {
                Log.d("TrainingData", "🌱 Skipping training data - endangered plant handled separately: $scientificName")
                return
            }

            // ✅ ONLY handle non-endangered plants here
            val newImageUrl = copyImageToTrainingStorage(originalImageUrl, sanitizedScientific)
            if (newImageUrl.isNullOrEmpty()) return

            // ✅ Save to TrainingData collection for non-endangered plants only
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
                isActive = true,
                sourceApi = "admin_verified",
                iucnCategory = observation.iucnCategory ?: "",
                geolocation = observation.geolocation
            )
            saveTrainingDataToFirestore(training)
            Log.d("TrainingData", "✅ Saved training data for $scientificName")

        } catch (e: Exception) {
            Log.e("TrainingData", "❌ handleAdminTrainingData failed: ${e.message}", e)
        }
    }


    // Example helper function to save EndangeredData
    private suspend fun saveEndangeredDataFromObservation(
        observation: Observation,
        source: String,
        adminId: String? = null
    ) {
        try {
            val endangeredCategories = listOf(
                "extinct", "extinct in the wild", "critically endangered", "endangered"
            )

            val category = observation.iucnCategory?.lowercase()
            if (category !in endangeredCategories) return // not endangered

            val scientificName = observation.currentIdentification?.scientificName ?: "unknown"
            val sanitizedScientific = sanitizePlantName(scientificName)
            val originalImageUrl = observation.plantImageUrls.firstOrNull()

            if (originalImageUrl.isNullOrEmpty()) {
                Log.w("EndangeredData", "⚠️ No image found for endangered plant $scientificName")
                return
            }

            // ✅ COPY IMAGE TO TRAINING STORAGE FIRST
            val newImageUrl = copyImageToTrainingStorage(originalImageUrl, sanitizedScientific)
            if (newImageUrl.isNullOrEmpty()) {
                Log.e("EndangeredData", "❌ Failed to copy image for endangered plant $scientificName")
                return
            }

            // ✅ Create proper EndangeredData object with the COPIED image URL
            val endangeredData = EndangeredData(
                id = "endangered_${observation.observationId}",
                observationId = observation.observationId,
                plantId = observation.currentIdentification?.plantId ?: "",
                scientificName = scientificName,
                commonName = observation.currentIdentification?.commonName ?: "",
                imageUrl = newImageUrl, // ✅ Use the COPIED image URL
                geolocation = observation.geolocation,
                iucnCategory = observation.iucnCategory ?: "unknown",
                addedBy = adminId ?: "system",
                addedAt = Timestamp.now(),
                notes = "Added via $source"
            )

            // ✅ Save using the proper EndangeredData object
            saveEndangeredDataToFirestore(endangeredData)

            Log.d("EndangeredData", "✅ Saved endangered data with image for ${observation.observationId}")

        } catch (e: Exception) {
            Log.e("EndangeredData", "❌ Failed to save endangered metadata: ${e.message}", e)
        }
    }

    // Add this function back - it's needed for saving EndangeredData objects
    private suspend fun saveEndangeredDataToFirestore(endangeredData: EndangeredData) {
        try {
            endangeredDataCollection.document(endangeredData.id)
                .set(endangeredData, SetOptions.merge())
                .await()
            Log.d("EndangeredData", "✅ Saved endangered data for ${endangeredData.plantId}")
        } catch (e: Exception) {
            Log.e("EndangeredData", "❌ Failed saving endangered data to Firestore: ${e.message}", e)
        }
    }

    private suspend fun saveTrainingDataToFirestore(trainingData: TrainingData) {
        try {
            if (trainingData.geolocation == null) {
                Log.w("TrainingData", "Skipped saving trainingData ${trainingData.trainingId} - missing geolocation")
                return
            }

            trainingDataCollection.document(trainingData.trainingId)
                .set(trainingData, SetOptions.merge())
                .await()

            Log.d("TrainingData", "✅ Saved training data for ${trainingData.plantId}")

        } catch (e: Exception) {
            Log.e("TrainingData", "❌ Failed saving training data to Firestore: ${e.message}", e)
        }
    }

    suspend fun getTrainingDataForMap(): List<TrainingData> {
        return try {
            Log.d("FirebaseRepo-Map", "Fetching training data for map...")
            val snapshot = trainingDataCollection
                .whereNotEqualTo("geolocation", null) // Only get items with a location
                .get()
                .await()

            val data = snapshot.toObjects(TrainingData::class.java)
            Log.d("FirebaseRepo-Map", "Successfully fetched ${data.size} training data items with geolocation")

            // Debug: Check what fields are actually present
            if (data.isNotEmpty()) {
                val firstItem = data.first()
                Log.d("FirebaseRepo-Map", "First item fields - " +
                        "trainingId: ${firstItem.trainingId}, " +
                        "plantId: ${firstItem.plantId}, " +
                        "geolocation: ${firstItem.geolocation}, " +
                        "iucnCategory: ${firstItem.iucnCategory}, " +
                        "hasImage: ${firstItem.imageUrl.isNotEmpty()}")
            }

            data
        } catch (e: Exception) {
            Log.e("FirebaseRepo-Map", "Error fetching training data for map", e)
            emptyList()
        }
    }

    private fun sanitizePlantName(name: String): String {
        return name.trim().lowercase().replace("[^a-z0-9]".toRegex(), " ")
    }

}
