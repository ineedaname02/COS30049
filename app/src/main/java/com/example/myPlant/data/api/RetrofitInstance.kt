package com.example.myPlant.data.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL = "https://my-api.plantnet.org/"

    // Custom interceptor to log the full request URL
    private val urlLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        Log.d("PlantNetRequest", "➡️ URL: ${request.url}")
        Log.d("PlantNetRequest", "➡️ Headers: ${request.headers}")
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        // Logs full body (can be verbose)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        // Logs URL and headers separately (cleaner)
        .addInterceptor(urlLoggingInterceptor)
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val api: PlantNetApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlantNetApi::class.java)
    }
}
