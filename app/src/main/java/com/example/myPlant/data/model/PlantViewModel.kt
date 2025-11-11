package com.example.myPlant.data.model

import androidx.lifecycle.*
import android.util.Log
import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.data.repository.FirebaseRepository
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.example.myPlant.ml.ClassificationResult

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

    private val _allObservations = MutableLiveData<List<Observation>>()
    val allObservations: LiveData<List<Observation>> = _allObservations

    // ✅ FIXED: Proper user observations LiveData
    private val _userObservations = MutableLiveData<List<Observation>>()
    val userObservations: LiveData<List<Observation>> = _userObservations

    var localPredictions: List<ClassificationResult>? = null

    private val _iucnStatus = MutableLiveData<String?>()
    val iucnStatus: LiveData<String?> = _iucnStatus

    fun identifyPlant(
        images: List<MultipartBody.Part>,
        organs: List<MultipartBody.Part>,
        project: String = "all"
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _loadingMessage.value = "Analyzing plant images..."

                val response = plantRepository.identifyPlant(images, organs, project)

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
                val status = plantRepository.getIucnStatus(scientificName)
                _iucnStatus.value = status
            } catch (e: Exception) {
                _error.value = "Failed to fetch IUCN status: ${e.message}"
            } finally {
                _loadingMessage.value = ""
            }
        }
    }

    fun loadAllObservations() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Fetching all plant locations..."
            try {
                val observations = firebaseRepository.getAllObservations()
                _allObservations.value = observations
            } catch (e: Exception) {
                _error.value = "Error fetching observations: ${e.message}"
            } finally {
                _isLoading.value = false
                _loadingMessage.value = ""
            }
        }
    }

    // ✅ FIXED: Proper user observations loading
    fun loadUserObservations() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            _error.value = "User not authenticated."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "Fetching your observations..."
            try {
                // Use the decrypted version if available, otherwise fallback to regular
                val observations = try {
                    // Try to use decrypted observations first
                    firebaseRepository.getDecryptedUserObservations(userId)
                } catch (e: Exception) {
                    // Fallback to regular observations if decrypted method doesn't exist yet
                    Log.w("PlantViewModel", "Using fallback observations: ${e.message}")
                    firebaseRepository.getUserObservations(userId)
                }
                _userObservations.value = observations
            } catch (e: Exception) {
                _error.value = "Error fetching your observations: ${e.message}"
            } finally {
                _isLoading.value = false
                _loadingMessage.value = ""
            }
        }
    }
}