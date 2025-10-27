package com.example.myPlant.data.local

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    var userRole: String?
        get() = prefs.getString("user_role", null)
        set(value) = prefs.edit().putString("user_role", value).apply()

    /**
     * Theme mode stored as a simple string: "default" or "dark"
     * Default is "default" (light mode as app currently uses).
     */
    var themeMode: String
        get() = prefs.getString("theme_mode", "default") ?: "default"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
