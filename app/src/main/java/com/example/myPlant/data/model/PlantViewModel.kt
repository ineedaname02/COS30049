package com.example.myPlant.data.model

import androidx.lifecycle.*

import com.example.myPlant.data.repository.PlantRepository
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import android.net.Uri

class PlantViewModel(private val repository: PlantRepository) : ViewModel() {

    private val _result = MutableLiveData<PlantNetResponse?>()
    val result: LiveData<PlantNetResponse?> = _result

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loadingMessage = MutableLiveData<String>()
    val loadingMessage: LiveData<String> = _loadingMessage

    var lastImageUris: List<Uri> = emptyList()

    fun identifyPlant(
        images: List<MultipartBody.Part>,
        organs: List<MultipartBody.Part>,
        project: String = "all"
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _loadingMessage.value = "Analyzing plant images..."
                
                val response = repository.identifyPlant(images, organs, project)
                
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

    private val _iucnStatus = MutableLiveData<String?>()
    val iucnStatus: LiveData<String?> = _iucnStatus

    fun fetchIucnStatus(scientificName: String) {
        viewModelScope.launch {
            try {
                _loadingMessage.value = "Fetching conservation status..."
                val status = repository.getIucnStatus(scientificName)
                _iucnStatus.value = status
            } catch (e: Exception) {
                _error.value = "Failed to fetch IUCN status: ${e.message}"
            } finally {
                _loadingMessage.value = ""
            }
        }
    }
}
