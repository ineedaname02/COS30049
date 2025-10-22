package com.example.myPlant.data.model

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myPlant.data.model.Observation
import com.example.myPlant.data.repository.FirebaseRepository
import kotlinx.coroutines.launch


class PlantHistoryViewModel(private val firebaseRepository: FirebaseRepository) : ViewModel() {

    private val _history = MutableLiveData<List<PlantObservation>>()
    val history: LiveData<List<PlantObservation>> = _history

    fun loadUserHistory(userId: String) {
        viewModelScope.launch {
            try {
                val observations = firebaseRepository.getUserObservations(userId)
                _history.postValue(observations)
            } catch (e: Exception) {
                Log.e("PlantHistoryViewModel", "Error loading history: ${e.message}")
                _history.postValue(emptyList())
            }
        }
    }
}