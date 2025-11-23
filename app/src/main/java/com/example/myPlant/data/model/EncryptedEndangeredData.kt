package com.example.myPlant.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.example.myPlant.data.encryption.EncryptionUtils
import com.example.myPlant.data.model.GeoLocation

data class EncryptedEndangeredData(
    // Non-encrypted fields (for querying)
    val id: String = "",
    val observationId: String = "",

    // Encrypted fields - USE CAMELCASE WITHOUT @PropertyName
    val encryptedPlantId: String = "",
    val encryptedScientificName: String = "",
    val encryptedCommonName: String = "",
    val encryptedImageUrl: String = "",

    // GeoLocation encryption
    val encryptedLatitude: String = "",
    val encryptedLongitude: String = "",
    val encryptedIucnCategory: String = "",

    // Non-encrypted metadata
    val addedBy: String = "",
    val addedAt: Timestamp = Timestamp.now(),
    val source: String = ""
) {
    companion object {
        suspend fun fromEndangeredData(data: EndangeredData): EncryptedEndangeredData {
            val encryptionKey = EncryptionUtils.getEncryptionKey()

            return EncryptedEndangeredData(
                id = data.id,
                observationId = data.observationId,
                encryptedPlantId = EncryptionUtils.encrypt(data.plantId, encryptionKey),
                encryptedScientificName = EncryptionUtils.encrypt(data.scientificName, encryptionKey),
                encryptedCommonName = EncryptionUtils.encrypt(data.commonName, encryptionKey),
                encryptedImageUrl = EncryptionUtils.encrypt(data.imageUrl, encryptionKey),
                encryptedLatitude = data.geolocation?.let {
                    EncryptionUtils.encrypt(it.lat.toString(), encryptionKey)
                } ?: "",
                encryptedLongitude = data.geolocation?.let {
                    EncryptionUtils.encrypt(it.lng.toString(), encryptionKey)
                } ?: "",
                encryptedIucnCategory = EncryptionUtils.encrypt(data.iucnCategory, encryptionKey),
                addedBy = data.addedBy,
                addedAt = data.addedAt ?: Timestamp.now(),
                source = data.notes
            )
        }
    }

    suspend fun toEndangeredData(): EndangeredData {
        val encryptionKey = EncryptionUtils.getEncryptionKey()

        return EndangeredData(
            id = id,
            observationId = observationId,
            plantId = EncryptionUtils.decrypt(encryptedPlantId, encryptionKey),
            scientificName = EncryptionUtils.decrypt(encryptedScientificName, encryptionKey),
            commonName = EncryptionUtils.decrypt(encryptedCommonName, encryptionKey),
            imageUrl = EncryptionUtils.decrypt(encryptedImageUrl, encryptionKey),
            geolocation = if (encryptedLatitude.isNotEmpty() && encryptedLongitude.isNotEmpty()) {
                GeoLocation(
                    lat = EncryptionUtils.decrypt(encryptedLatitude, encryptionKey).toDouble(),
                    lng = EncryptionUtils.decrypt(encryptedLongitude, encryptionKey).toDouble()
                )
            } else {
                null
            },
            iucnCategory = EncryptionUtils.decrypt(encryptedIucnCategory, encryptionKey),
            addedBy = addedBy,
            addedAt = addedAt,
            notes = source
        )
    }
}