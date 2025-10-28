package com.example.myPlant.data.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myPlant.data.model.PlantViewModel
import com.example.myPlant.data.local.AppDatabase
import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.data.repository.FirebaseRepository

class PlantViewModelFactory(
    private val plantRepository: PlantRepository,
    private val firebaseRepository: FirebaseRepository,
    private val context: Context // ✅ Add Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlantViewModel::class.java)) {
            // ✅ Get DAO and create the repository with it
            val dao = AppDatabase.getDatabase(context).observationDao()
            val repoWithCache = FirebaseRepository(dao)

            @Suppress("UNCHECKED_CAST")
            return PlantViewModel(plantRepository, repoWithCache) as T // ✅ Pass the new repo
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}