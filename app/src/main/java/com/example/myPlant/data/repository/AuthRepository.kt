package com.example.myPlant.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.example.myPlant.data.model.UserProfile
import com.example.myPlant.data.model.ContributionStats
import com.example.myPlant.data.model.UserPreferences
import com.example.myPlant.data.model.NotificationPreferences
import com.example.myPlant.data.model.PrivacyPreferences
import com.example.myPlant.data.model.AISuggestion
import kotlinx.coroutines.tasks.await
import kotlin.Result

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Current user state
    val currentUser: FirebaseUser? get() = auth.currentUser
    val isUserLoggedIn: Boolean get() = auth.currentUser != null

    // User profile collection reference
    private val userProfilesCollection = db.collection("userProfiles")

    suspend fun registerUser(
        email: String,
        password: String,
        displayName: String
    ): Result<FirebaseUser> {
        return try {
            // 1. Create Firebase Auth account
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("User creation failed")

            // 2. Update profile in Auth
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            user.updateProfile(profileUpdates).await()

            // 3. Create user profile in Firestore using your data models
            val userProfile = UserProfile(
                uid = user.uid,
                role = "public",
                dateJoined = Timestamp.now(),
                lastProfileUpdate = Timestamp.now(),
                contributionStats = ContributionStats(),
                preferences = UserPreferences(
                    notifications = NotificationPreferences(),
                    privacy = PrivacyPreferences()
                )
            )

            userProfilesCollection.document(user.uid)
                .set(userProfile, SetOptions.merge())
                .await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("Login failed")

            // Update last login timestamp
            updateLastLogin(user.uid)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun getUserProfile(uid: String): Result<UserProfile> {
        return try {
            val document = userProfilesCollection.document(uid).get().await()
            if (document.exists()) {
                val userProfile = document.toObject(UserProfile::class.java)
                    ?: throw Exception("Failed to parse user profile")
                Result.success(userProfile)
            } else {
                Result.failure(Exception("User profile not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateLastLogin(uid: String) {
        try {
            userProfilesCollection.document(uid)
                .update("lastLogin", Timestamp.now())
                .await()
        } catch (e: Exception) {
            // Log error but don't fail login
            println("Failed to update last login: ${e.message}")
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}