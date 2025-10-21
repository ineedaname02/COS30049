package com.example.myPlant.data.repository

import com.example.myPlant.data.api.RetrofitInstance
import okhttp3.MultipartBody
import com.example.myPlant.data.api.GbifApiService


class PlantRepository(
    private val apiKey: String,
    private val gbifApiService: GbifApiService = GbifApiService() // optional default
) {

    // Existing PlantNet API function
    suspend fun identifyPlant(
        images: List<MultipartBody.Part>,
        organs: List<MultipartBody.Part>,
        project: String
    ) = RetrofitInstance.api.identifyPlant(
        project = project,
        images = images,
        organs = organs,
        apiKey = apiKey
    )

    // 🆕 GBIF IUCN fetch function
    suspend fun getIucnStatus(scientificName: String): String? {
        val usageKey = gbifApiService.getUsageKey(scientificName)
        if (usageKey != null) {
            return gbifApiService.getIucnCategory(usageKey)?.category
        }
        return null
    }
}
