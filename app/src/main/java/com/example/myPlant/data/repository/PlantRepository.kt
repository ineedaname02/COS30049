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
    suspend fun getIucnStatus(scientificName: String): String {
        val usageKey = gbifApiService.getUsageKey(scientificName)
        val category = if (usageKey != null) {
            gbifApiService.getIucnCategory(usageKey)?.category
        } else null

        return when (category) {
            "EXTINCT" -> "Extinct"
            "EXTINCT_IN_THE_WILD" -> "Extinct in the Wild"
            "CRITICALLY_ENDANGERED" -> "Critically Endangered"
            "ENDANGERED" -> "Endangered"
            "VULNERABLE" -> "Vulnerable"
            "NEAR_THREATENED" -> "Near Threatened"
            "LEAST_CONCERN" -> "Least Concern"
            "DATA_DEFICIENT" -> "Data Deficient"
            else -> "Not Evaluated"
        }
    }

}
