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
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneMultiFactorGenerator
import java.util.concurrent.TimeUnit
import android.app.Activity
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.PhoneAuthCredential


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
            Result.success(authResult.user!!)
        } catch (e: Exception) {
            if (e is FirebaseAuthMultiFactorException) {
                // Requires SMS verification
                val resolver = e.resolver
                Result.failure(MultiFactorAuthRequired(resolver))
            } else {
                Result.failure(e)
            }
        }
    }

    // Custom wrapper for MFA flow
    class MultiFactorAuthRequired(val resolver: MultiFactorResolver) : Exception()


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

    fun enrollSecondFactor(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val user = auth.currentUser
        if (user == null) {
            throw IllegalStateException("No user is currently signed in to enroll MFA.")
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)

    }

    // Complete enrollment using the received SMS code
    suspend fun finalizeEnrollment(verificationId: String, smsCode: String): Result<Unit> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
            auth.currentUser!!.multiFactor.enroll(assertion, "Phone MFA").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}