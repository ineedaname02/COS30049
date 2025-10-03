package com.example.myPlant.data.model

data class AISuggestion(
    val suggestionId: String = "",
    val source: String = "", // "plantnet", "smartplant_ai"
    val plantId: String = "", // Our internal plant ID or external ID
    val scientificName: String = "",
    val commonNames: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val externalIds: ExternalIds = ExternalIds() // Store IDs from different sources
)

data class ExternalIds(
    val gbifId: String? = null,
    val plantNetId: String? = null,
    val powoId: String? = null
)