package com.example.myPlant.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myPlant.data.model.EndangeredPlantsViewModel

import com.example.myPlant.data.repository.FirebaseRepository

class EndangeredPlantsViewModelFactory(
    private val repository: FirebaseRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EndangeredPlantsViewModel::class.java)) {
            return EndangeredPlantsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}