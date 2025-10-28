package com.example.myPlant.data.model

import android.net.Uri
import androidx.lifecycle.*
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.ml.ClassificationResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class PlantViewModel(
    private val plantRepository: PlantRepository,
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _result = MutableLiveData<PlantNetResponse?>()
    val result: LiveData<PlantNetResponse?> = _result
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _loadingMessage = MutableLiveData<String>()
    val loadingMessage: LiveData<String> = _loadingMessage
    var lastImageUris: List<Uri> = emptyList()
    var localPredictions: List<ClassificationResult>? = null
    private val _iucnStatus = MutableLiveData<String?>()
    val iucnStatus: LiveData<String?> = _iucnStatus

    // ✅ --- NEW CACHE-AWARE OBSERVATION LOGIC ---

    // The UI will observe this LiveData, which comes directly from the Room cache.
    val allObservations: LiveData<List<Observation>> = firebaseRepository.getAllObservations()

    // The UI will also observe this, which is also from the Room cache.
    val userObservations: LiveData<List<Observation>> =
        MutableLiveData(FirebaseAuth.getInstance().currentUser?.uid).switchMap { userId ->
            if (userId != null) {
                firebaseRepository.getUserObservations(userId)
            } else {
                // Return empty LiveData if user is not logged in
                MutableLiveData(emptyList())
            }
        }

    /**
     * This function now only needs to trigger the background refresh.
     * The UI will update automatically when the cache is updated.
     */
    fun loadAllObservations() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Fetching latest plant locations..."
            firebaseRepository.refreshAllObservations() // Triggers Firebase fetch and cache update
            _isLoading.value = false
            _loadingMessage.value = ""
        }
    }

    /**
     * This also only needs to trigger the background refresh for the user.
     */
    fun loadUserObservations() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Fetching your latest observations..."
            firebaseRepository.refreshUserObservations(userId) // Triggers user-specific fetch
            _isLoading.value = false
            _loadingMessage.value = ""
        }
    }

    fun identifyPlant(
        images: List<MultipartBody.Part>,
        organs: List<MultipartBody.Part>,
        project: String = "all"
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _loadingMessage.value = "Analyzing plant images..."

                val response = plantRepository.identifyPlant(images, organs, project) //repository

                if (response.isSuccessful) {
                    _loadingMessage.value = "Processing results..."
                    _result.value = response.body()
                } else {
                    _error.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
                _loadingMessage.value = ""
            }
        }
    }

    fun fetchIucnStatus(scientificName: String) {
        viewModelScope.launch {
            try {
                _loadingMessage.value = "Fetching conservation status..."
                val status = plantRepository.getIucnStatus(scientificName) //repository
                _iucnStatus.value = status
            } catch (e: Exception) {
                _error.value = "Failed to fetch IUCN status: ${e.message}"
            } finally {
                _loadingMessage.value = ""
            }
        }
    }
}