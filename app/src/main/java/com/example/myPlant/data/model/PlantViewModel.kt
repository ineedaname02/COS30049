package com.example.myPlant.data.model

import androidx.lifecycle.*

import com.example.myPlant.data.repository.PlantRepository
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class PlantViewModel(private val repository: PlantRepository) : ViewModel() {

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
                val response = repository.identifyPlant(images, organs, project)
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
}
