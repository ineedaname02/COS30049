package com.example.myPlant.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Observation(
    val observationId: String = "",
    val userId: String = "",
    val plantImageUrls: List<String> = emptyList(),
    val geolocation: GeoLocation? = null,
    val userNote: String = "",

    // Multiple AI Sources
    val aiSuggestions: List<AISuggestion> = emptyList(),
    val primarySource: String = "plantnet", // "plantnet", "smartplant_ai", "hybrid"

    // Current Identification State
    val currentIdentification: CurrentIdentification = CurrentIdentification(),

    // Flagging System
    val flagInfo: FlagInfo? = null,

    // 🆕 IUCN Category (e.g., "Endangered", "Vulnerable", etc.)
    val iucnCategory: String? = null,

    // Metadata
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val version: Int = 1
)

data class CurrentIdentification(
    val plantId: String = "",
    val scientificName: String = "",
    val confidence: Double = 0.0,
    val identifiedBy: String = "ai", // 'ai', 'admin', 'community'
    val status: String = "ai_suggested"
)

data class FlagInfo(
    val isFlagged: Boolean = false,
    val flaggedBy: String = "",
    val flaggedAt: Timestamp? = null,
    val reason: String = "unsure",
    val assignedAdmin: String? = null,
    val resolution: String? = null
)
//