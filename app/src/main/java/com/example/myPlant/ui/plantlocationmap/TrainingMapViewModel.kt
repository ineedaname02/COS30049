package com.example.myPlant.ui.plantlocationmap

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myPlant.data.model.TrainingData
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.data.repository.Graph // Import the Graph
import kotlinx.coroutines.launch
class TrainingMapViewModel : ViewModel() {

    // Get the single, safe instance of the repository from the Graph
    private val firebaseRepository: FirebaseRepository = Graph.firebaseRepository

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // This will hold the complete, unfiltered list from Firebase.
    private var allTrainingData: List<TrainingData> = emptyList()

    // This is what the UI will observe. It holds the currently displayed (filtered) list.
    private val _filteredTrainingDataForMap = MutableLiveData<List<TrainingData>>()
    val trainingDataForMap: LiveData<List<TrainingData>> = _filteredTrainingDataForMap

    // --- Functions ---

    fun loadTrainingDataForMap() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // This calls the existing getTrainingDataForMap function in your repository
                val data = firebaseRepository.getTrainingDataForMap()
                allTrainingData = data // Store the full list
                _filteredTrainingDataForMap.value = data // Initially, show all data
                Log.d("TrainingMapViewModel", "Fetched ${data.size} items for training map.")
            } catch (e: Exception) {
                Log.e("TrainingMapViewModel", "Error loading training data for map", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterMapData(query: String?) {
        if (query.isNullOrBlank()) {
            // If search is empty, show the full list
            _filteredTrainingDataForMap.value = allTrainingData
        } else {
            // Filter the full list based on the query
            val filteredList = allTrainingData.filter { trainingData ->
                trainingData.plantId.contains(query, ignoreCase = true)
            }
            _filteredTrainingDataForMap.value = filteredList
        }
    }
}
