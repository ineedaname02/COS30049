package com.example.myPlant.data.model

import com.google.firebase.Timestamp

data class UserItem(
    var uid: String = "",
    var email: String? = null,
    var role: String = "public",
    var dateJoined: Timestamp? = null,
    var lastLogin: Timestamp? = null,
    var lastProfileUpdate: Timestamp? = null,
    var location: String? = null,
    var contributionStats: ContributionStats = ContributionStats(),
    var preferences: UserPreferences = UserPreferences()
)