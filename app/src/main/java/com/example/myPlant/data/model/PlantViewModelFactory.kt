package com.example.myPlant.data.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myPlant.data.model.PlantViewModel
import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.data.repository.FirebaseRepository

class PlantViewModelFactory(
    private val plantRepository: PlantRepository,
    private val firebaseRepository: FirebaseRepository // ✅ Add this parameter
    ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") // ✅ Pass both repositories to the ViewModel constructor
            return PlantViewModel(plantRepository, firebaseRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

