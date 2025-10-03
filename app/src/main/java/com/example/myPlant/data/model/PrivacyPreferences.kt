package com.example.myPlant.data.model

data class PrivacyPreferences(
    val showEmail: Boolean = false,
    val showLocation: String = "approximate", // "exact", "approximate", "hidden"
    val showContributions: Boolean = true
)

