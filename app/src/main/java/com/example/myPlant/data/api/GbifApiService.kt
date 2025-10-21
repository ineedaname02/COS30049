package com.example.myPlant.data.api

import android.util.Log
import com.example.myPlant.data.model.IucnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class GbifApiService {

    suspend fun getUsageKey(scientificName: String): Int? = withContext(Dispatchers.IO) {
        try {
            val encodedName = URLEncoder.encode(scientificName, "UTF-8")
            val url = "https://api.gbif.org/v1/species/match?name=$encodedName"
            val response = URL(url).readText()
            val json = JSONObject(response)
            return@withContext if (json.has("usageKey")) json.getInt("usageKey") else null
        } catch (e: Exception) {
            Log.e("GbifApiService", "Error fetching usageKey: ${e.message}")
            null
        }
    }

    suspend fun getIucnCategory(usageKey: Int): IucnStatus? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.gbif.org/v1/species/$usageKey/iucnRedListCategory"
            val response = URL(url).readText()
            if (response.isBlank()) return@withContext null

            val json = JSONObject(response)
            Log.d("GbifApiService", "IUCN response: $json")

            return@withContext IucnStatus(
                category = json.optString("category", null),
            )
        } catch (e: Exception) {
            Log.e("GbifApiService", "Error fetching IUCN category: ${e.message}")
            null
        }
    }
}