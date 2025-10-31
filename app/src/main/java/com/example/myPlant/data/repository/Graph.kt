package com.example.myPlant.data.repository

import android.content.Context
import com.example.myPlant.BuildConfig

/**
 * A simple, static object that provides single instances of repositories.
 * This acts as a manual dependency injector.
 */
object Graph {

    // The single instance of FirebaseRepository for the entire app
    lateinit var firebaseRepository: FirebaseRepository
        private set // This makes it read-only from outside the Graph

    // The single instance of PlantRepository for the entire app
    lateinit var plantRepository: PlantRepository
        private set

    /**
     * This function should be called once from the Application class
     * to initialize all the repositories.
     */
    fun provide(context: Context) {
        // Use the applicationContext to prevent memory leaks
        firebaseRepository = FirebaseRepository(context.applicationContext)
        plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY)
    }
}
    