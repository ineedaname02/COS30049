package com.example.myPlant.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myPlant.data.model.PlantObservation
import com.example.myPlant.data.repository.FirebaseRepository
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    // Repository requires a Context in your project, so pass the application.
    private val repo = FirebaseRepository(application)

    private val _pendingObservations = MutableLiveData<List<PlantObservation>>(emptyList())
    val pendingObservations: LiveData<List<PlantObservation>> = _pendingObservations

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    fun fetchPendingObservations(limit: Int = 30) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val list = repo.fetchPendingObservations(limit)
                _pendingObservations.value = list
                if (list.isEmpty()) _message.value = "No pending observations."
            } catch (e: Exception) {
                _message.value = "Failed to fetch: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // In AdminViewModel.kt
    suspend fun processAdminValidation(
        observationId: String,
        adminId: String,
        isCorrect: Boolean,
        correctedScientificName: String? = null,
        correctedCommonName: String? = null
    ): Boolean { // ✅ Make it suspend and return Boolean
        return try {
            _isLoading.postValue(true)
            val ok = repo.processAdminValidation(
                observationId,
                adminId,
                isCorrect,
                correctedScientificName,
                correctedCommonName
            )
            _message.postValue(if (ok) "Validation saved successfully." else "Validation failed.")
            fetchPendingObservations()
            ok
        } catch (e: Exception) {
            _message.postValue("Error: ${e.message}")
            false
        } finally {
            _isLoading.postValue(false)
        }
    }

    // In AdminViewModel.kt
    fun onObservationProcessed(observationId: String) {
        // Remove from local list and update UI
        val currentList = _pendingObservations.value?.toMutableList() ?: mutableListOf()
        currentList.removeAll { it.id == observationId }
        _pendingObservations.value = currentList

        // If list is empty, fetch more
        if (currentList.isEmpty()) {
            fetchPendingObservations(limit = 30)
        }
    }
}
