package com.example.myPlant.data.model

import android.net.Uri
import androidx.lifecycle.*
import com.example.myPlant.data.repository.ObservationRepository // <-- Import FirebaseRepository
import com.example.myPlant.data.repository.PlantRepository
import kotlinx.coroutines.launch
import okhttp3.MultipartBody


// ✅ 1. UPDATE THE CONSTRUCTOR to accept both repositories
class PlantViewModel(
    private val plantRepository: PlantRepository,
    private val observationRepository: ObservationRepository
) : ViewModel() {

    // --- This part for PlantNet identification stays the same ---
    private val _result = MutableLiveData<PlantNetResponse?>()
    val result: LiveData<PlantNetResponse?> = _result

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun identifyPlant(
        images: List<MultipartBody.Part>,
        organs: List<MultipartBody.Part>,
        project: String = "all"
    ) {
        viewModelScope.launch {
            try {
                val response = plantRepository.identifyPlant(images, organs, project)
                if (response.isSuccessful) {
                    _result.value = response.body()
                } else {
                    _error.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // --- ✅ 2. ADD THIS NEW CODE for loading map data ---

    // LiveData to hold the list of all observations from Firebase
    private val _allObservations = MutableLiveData<List<Map<String, Any>>>()
    val allObservations: LiveData<List<Map<String, Any>>> = _allObservations

    fun loadAllObservations() {
        viewModelScope.launch {
            // Call the correct repository
            val observations = observationRepository.getAllObservations()
            _allObservations.postValue(observations)
        }
    }
    // ✅ ADD this function, called by the fragment
    suspend fun uploadPlantObservation(
        plantNetResponse: PlantNetResponse?,
        imageUris: List<Uri>,
        location: GeoLocation?
    ): kotlin.Result<String> {
        // Delegate the complex work to the repository
        return observationRepository.uploadPlantObservation(
            plantNetResponse = plantNetResponse,
            imageUris = imageUris,
            location = location
        )
    }

    // ✅ ADD this function, called by the fragment
    suspend fun confirmObservation(observationId: String, plantId: String, scientificName: String): kotlin.Result<Unit> {
        return observationRepository.confirmObservation(observationId, plantId, scientificName)
    }

    // ✅ ADD this function, called by the fragment
    suspend fun flagObservation(observationId: String, reason: String): kotlin.Result<Unit> {
        return observationRepository.flagObservation(observationId, reason)
    }

    // ✅ ADD this helper to avoid duplicating logic in the fragment
    fun getAiSuggestionsFromResponse(response: PlantNetResponse?): List<AISuggestion> {
        return observationRepository.createSuggestionsFrom(response) // Assuming this function is public in ObservationRepository
    }
}
