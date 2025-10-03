package com.example.myPlant.data.model

import com.google.firebase.Timestamp
import com.example.myPlant.data.model.NotificationPreferences
import com.example.myPlant.data.model.PrivacyPreferences

data class UserProfile(
    // Only store additional data NOT provided by Firebase Auth
    val uid: String = "",
    val bio: String? = null,
    val location: String? = null,
    val interests: List<String> = emptyList(),

    // Role & stats
    val role: String = "public", // "public", "admin"
    val contributionStats: ContributionStats = ContributionStats(),

    // Preferences
    val preferences: UserPreferences = UserPreferences(),

    // Timestamps
    val dateJoined: Timestamp = Timestamp.now(),
    val lastProfileUpdate: Timestamp = Timestamp.now()
)

data class ContributionStats(
    val observations: Int = 0,
    val verifiedIdentifications: Int = 0,
    val flagsSubmitted: Int = 0,
    val totalPoints: Int = 0
)

data class UserPreferences(
    val notifications: NotificationPreferences = NotificationPreferences(),
    val privacy: PrivacyPreferences = PrivacyPreferences()
)