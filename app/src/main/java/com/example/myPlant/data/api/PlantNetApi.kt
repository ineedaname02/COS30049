package com.example.myPlant.data.api

import com.example.myPlant.data.model.PlantNetResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface PlantNetApi {
    @Multipart
    @POST("v2/identify/{project}")
    suspend fun identifyPlant(
        @Path("project") project: String = "all",
        @Part images: List<MultipartBody.Part>,
        @Part organs: List<MultipartBody.Part>,
        @Query("api-key") apiKey: String,
        @Query("include-related-images") includeRelated: Boolean = false,
        @Query("no-reject") noReject: Boolean = false,
        @Query("nb-results") nbResults: Int = 10,
        @Query("lang") lang: String = "en",
        @Query("type") type: String = "kt"
    ): Response<PlantNetResponse>
}
