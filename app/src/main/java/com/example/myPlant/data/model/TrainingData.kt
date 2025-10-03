package com.example.myPlant.data.model

import com.google.firebase.Timestamp

data class TrainingData(
    val trainingId: String = "",
    val plantId: String = "",
    val imageUrl: String = "",
    val sourceType: String = "", // 'admin_upload', 'user_verified'
    val sourceObservationId: String = "",
    val verifiedBy: String = "",
    val verificationDate: Timestamp? = null,
    val verificationMethod: String = "", // 'admin_review'
    val confidenceScore: Double = 1.0,
    val geolocation: GeoLocation? = null,
    val isActive: Boolean = true,
    val sourceApi: String? = null
)