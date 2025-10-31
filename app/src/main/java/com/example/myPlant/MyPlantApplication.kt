package com.example.myPlant

import android.app.Application
import com.example.myPlant.data.repository.Graph

/**
 * The Application class is the first component to be instantiated when the
 * app process is created. We use it to initialize our Graph.
 */
class MyPlantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // This initializes the Graph and creates the single instances of our repositories.
        Graph.provide(this)
    }
}
    