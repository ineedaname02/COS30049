package com.example.myPlant.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserProfileRepository {
    private val db = FirebaseFirestore.getInstance()
    private val userProfilesCollection = db.collection("userProfiles")

    suspend fun incrementUserStat(userId: String, field: String, amount: Long = 1) {
        try {
            userProfilesCollection.document(userId)
                .update("contributionStats.$field", FieldValue.increment(amount))
                .await()
        } catch (e: Exception) {
            Log.e("UserProfileRepo", "Failed to update stats for user $userId", e)
        }
    }
}
