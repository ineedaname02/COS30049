package com.example.myPlant.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myPlant.data.local.AppDatabase
import com.example.myPlant.data.model.Observation
import com.example.myPlant.data.repository.FirebaseRepository
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    // ✅ FIX 1: Correctly declare the repository variable
    private val repo: FirebaseRepository

    init {
        val observationDao = AppDatabase.getDatabase(application).observationDao()
        repo = FirebaseRepository(observationDao)
    }

    // ✅ FIX 2: Change LiveData to use the correct Observation model
    private val _pendingObservations = MutableLiveData<List<Observation>>(emptyList())
    val pendingObservations: LiveData<List<Observation>> = _pendingObservations

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    fun fetchPendingObservations(limit: Int = 30) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // ✅ FIX 3: Call the new placeholder method
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

    fun processAdminValidation(
        observationId: String,
        isCorrect: Boolean,
        correctedScientificName: String? = null
    ) {
        val adminId = "current_admin_id" // Placeholder for actual admin user ID
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // ✅ FIX 4: Call the new placeholder method
                val ok = repo.processAdminValidation(
                    observationId,
                    adminId,
                    isCorrect,
                    correctedScientificName,
                )
                _message.value = if (ok) "Validation saved successfully." else "Validation failed."
                fetchPendingObservations() // Refresh the list
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
