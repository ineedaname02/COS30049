package com.example.myPlant.data.local

import androidx.room.TypeConverter
import com.example.myPlant.data.model.FlagInfo
import com.example.myPlant.data.model.AISuggestion
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {

    private val gson = Gson()

    // Converter for List<String>
    @TypeConverter
    fun fromStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromListString(list: List<String>): String {
        return gson.toJson(list)
    }

    // Converter for List<AISuggestion>
    @TypeConverter
    fun fromAISuggestionList(value: String): List<AISuggestion> {
        val listType = object : TypeToken<List<AISuggestion>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromListAISuggestion(list: List<AISuggestion>): String {
        return gson.toJson(list)
    }

    // Converter for Timestamp
    @TypeConverter
    fun fromTimestamp(timestamp: Timestamp?): Long? {
        return timestamp?.toDate()?.time
    }

    @TypeConverter
    fun toTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(Date(it)) }
    }

    @TypeConverter
    fun fromFlagInfo(flagInfo: FlagInfo?): String? {
        // If the flagInfo object is null, return null.
        // Otherwise, convert it to a JSON string.
        return flagInfo?.let { Gson().toJson(it) }
    }

    @TypeConverter
    fun toFlagInfo(jsonString: String?): FlagInfo? {
        // If the JSON string is null, return null.
        // Otherwise, convert it back into a FlagInfo object.
        return jsonString?.let { Gson().fromJson(it, FlagInfo::class.java) }
    }
}