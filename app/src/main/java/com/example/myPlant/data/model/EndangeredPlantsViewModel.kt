package com.example.myPlant.data.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.myPlant.data.repository.FirebaseRepository

class EndangeredPlantsViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _endangeredPlants = MutableStateFlow<List<EndangeredData>>(emptyList())
    val endangeredPlants: StateFlow<List<EndangeredData>> = _endangeredPlants.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadEndangeredPlants() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val plants = repository.getAllEndangeredPlants()
                _endangeredPlants.value = plants
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load endangered plants: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeFromEndangered(plantId: String) {
        viewModelScope.launch {
            try {
                val success = repository.removeFromEndangeredList(plantId)
                if (success) {
                    _endangeredPlants.value = _endangeredPlants.value.filter { it.id != plantId }
                } else {
                    _errorMessage.value = "Failed to remove plant"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to remove plant: ${e.message}"
            }
        }
    }

    fun errorMessageShown() {
        _errorMessage.value = null
    }
}