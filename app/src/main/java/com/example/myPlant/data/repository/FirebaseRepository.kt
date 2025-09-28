package com.example.myPlant.data.repository

import android.net.Uri
import android.content.Context
import android.widget.Toast
import com.example.myPlant.data.model.PlantNetResponse
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class FirebaseRepository(private val context: Context) {

    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun uploadPlantResult(response: PlantNetResponse?, imageUris: List<Uri>) {
        if (response == null || imageUris.isEmpty()) {
            Toast.makeText(context, "No data to upload", Toast.LENGTH_SHORT).show()
            return
        }

        val plantId = UUID.randomUUID().toString()
        val storageRef = storage.reference.child("plants/$plantId")
        val imageUrls = mutableListOf<String>()
        var uploadedCount = 0

        imageUris.forEachIndexed { index, uri ->
            val fileRef = storageRef.child("image_$index.jpg")
            fileRef.putFile(uri)
                .addOnSuccessListener {
                    fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        imageUrls.add(downloadUrl.toString())
                        uploadedCount++

                        // Once all images are uploaded
                        if (uploadedCount == imageUris.size) {
                            saveResultToFirestore(response, imageUrls, plantId)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveResultToFirestore(response: PlantNetResponse, imageUrls: List<String>, plantId: String) {
        val results = response.results?.map { result ->
            mapOf(
                "species" to (result.species?.scientificNameWithoutAuthor ?: "Unknown"),
                "commonNames" to (result.species?.commonNames ?: emptyList()),
                "confidence" to (result.score ?: 0.0)
            )
        } ?: emptyList()

        val bestSpeciesName = response.results
            ?.firstOrNull()
            ?.species?.scientificNameWithoutAuthor ?: "Unknown"

        val data = mapOf(
            "plantId" to plantId,
            "timestamp" to System.currentTimeMillis(),
            "species" to bestSpeciesName,
            "imageUrls" to imageUrls,
            "results" to results,
            "isFlagged" to false,
            "flagReason" to null,
            "isVerified" to false,
            "verifiedBy" to null
        )

        db.collection("plants").document(plantId)
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(context, "Saved to Firebase", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Firestore error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun flagPlant(plantId: String, reason: String?) {
        db.collection("plants").document(plantId)
            .update(
                mapOf(
                    "isFlagged" to true,
                    "flagReason" to reason
                )
            )
            .addOnSuccessListener {
                Toast.makeText(context, "Plant flagged for review.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to flag: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun verifyPlant(plantId: String, verifiedBy: String) {
        db.collection("plants").document(plantId)
            .update(
                mapOf(
                    "isVerified" to true,
                    "verifiedBy" to verifiedBy
                )
            )
            .addOnSuccessListener {
                Toast.makeText(context, "Plant marked as verified.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to verify: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
