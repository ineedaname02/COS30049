package com.example.myPlant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myPlant.data.model.PlantViewModel
import com.example.myPlant.data.repository.PlantRepository

class PlantViewModelFactory(private val repository: PlantRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlantViewModel::class.java)) {
            return PlantViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
