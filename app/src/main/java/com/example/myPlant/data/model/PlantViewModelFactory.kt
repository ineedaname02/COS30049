package com.example.myPlant.data.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myPlant.data.repository.ObservationRepository
import com.example.myPlant.data.repository.PlantRepository

/**
 * Factory for creating a PlantViewModel with its necessary dependencies (repositories).
 */
class PlantViewModelFactory(
    private val plantRepository: PlantRepository,
    private val observationRepository: ObservationRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Check if the requested ViewModel class is our PlantViewModel
        if (modelClass.isAssignableFrom(PlantViewModel::class.java)) {
            // If it is, create an instance of it, passing in the repositories.
            // The Suppress is needed because the compiler can't guarantee the cast, but we know it's safe.
            @Suppress("UNCHECKED_CAST")
            return PlantViewModel(plantRepository, observationRepository) as T
        }
        // If it's some other ViewModel, throw an error.
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
