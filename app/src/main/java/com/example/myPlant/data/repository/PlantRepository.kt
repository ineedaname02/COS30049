package com.example.myPlant.data.repository

import com.example.myPlant.data.api.RetrofitInstance
import okhttp3.MultipartBody

class PlantRepository(private val apiKey: String) {
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
}