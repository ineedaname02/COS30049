package com.example.myPlant.data.model

data class NotificationPreferences(
    val emailDigest: Boolean = true,
    val flagResolutions: Boolean = true,
    val newSpeciesInArea: Boolean = false,
    val conservationAlerts: Boolean = true
)