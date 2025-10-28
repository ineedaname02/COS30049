package com.example.myPlant.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.myPlant.data.local.Converters

// ✅ ADD @Entity and @TypeConverters
@Entity(tableName = "observations")
@TypeConverters(Converters::class)
data class Observation(
    // ✅ ADD @PrimaryKey
    @PrimaryKey
    val observationId: String = "",

    val userId: String = "",
    val plantImageUrls: List<String> = emptyList(),

    // ✅ Use @Embedded for nested objects
    @Embedded
    val geolocation: GeoLocation? = null,

    val userNote: String = "",

    // Multiple AI Sources
    val aiSuggestions: List<AISuggestion> = emptyList(),
    val primarySource: String = "plantnet", // "plantnet", "smartplant_ai", "hybrid"

    // ✅ Use @Embedded for nested objects
    @Embedded(prefix = "current_id_")
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

data class GeoLocation(val lat: Double = 0.0, val lng: Double = 0.0)

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
