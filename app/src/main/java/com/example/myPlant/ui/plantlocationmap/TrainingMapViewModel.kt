package com.example.myPlant.ui.plantlocationmap

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myPlant.data.model.TrainingData
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.data.repository.Graph // Import the Graph
import com.google.type.Date
import kotlinx.coroutines.launch
import java.util.Calendar

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

    private val _rarityFilter = MutableLiveData<String?>("All")
    private val _dateRangeFilter = MutableLiveData<Pair<java.util.Date, java.util.Date>?>() // Use java.util.Date

    init {
        // Load the data when the ViewModel is created.
        loadTrainingDataForMap()
    }

    // --- Functions ---

    fun loadTrainingDataForMap() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // This calls the existing getTrainingDataForMap function in your repository
                val data = firebaseRepository.getTrainingDataForMap()
                allTrainingData = data // Store the full list
                // Apply any existing filters right after fetching
                applyFilters()
                Log.d("TrainingMapViewModel", "Fetched ${data.size} items for training map.")
            } catch (e: Exception) {
                Log.e("TrainingMapViewModel", "Error loading training data for map", e)
                _filteredTrainingDataForMap.value = emptyList() // Ensure UI is cleared on error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterMapData(query: String?) {
        if (query.isNullOrBlank()) {
            // If search is empty, show the full list (after applying other filters)
            applyFilters()
        } else {
            // Filter the full list based on the query, then apply other filters
            val searchFilteredList = allTrainingData.filter { trainingData ->
                trainingData.plantId.contains(query, ignoreCase = true)
            }
            // Now apply other filters like rarity and date to the search-filtered list
            applyFilters(searchFilteredList)
        }
    }

    // Function to update the rarity filter from the Fragment
    fun setRarityFilter(rarity: String?) {
        _rarityFilter.value = rarity
        applyFilters()
    }

    // Function to update the date range from the Fragment
    fun setDateRangeFilter(startDate: java.util.Date, endDate: java.util.Date) {
        _dateRangeFilter.value = Pair(startDate, endDate)
        applyFilters()
    }

    // Central function to apply all filters to the original data
    private fun applyFilters(baseList: List<TrainingData> = allTrainingData) {
        var filteredList = baseList

        // 1. Apply rarity filter
        _rarityFilter.value?.let { rarity ->
            if (rarity != "All") {
                filteredList = filteredList.filter { it.iucnCategory.equals(rarity, ignoreCase = true) }
            }
        }

        // 2. Apply date range filter
        _dateRangeFilter.value?.let { dateRange ->
            val (start, end) = dateRange
            // Add a day to the end date to make the range inclusive
            val inclusiveEndDate = Calendar.getInstance().apply {
                time = end
                add(Calendar.DAY_OF_YEAR, 1)
            }.time

            filteredList = filteredList.filter {
                // It is safer to use java.util.Date for comparison
                val verificationDate = it.verificationDate?.toDate()
                verificationDate != null &&
                        !verificationDate.before(start) &&
                        verificationDate.before(inclusiveEndDate)
            }
        }

        // Post the final filtered list to the UI
        _filteredTrainingDataForMap.value = filteredList
    }
    fun clearDateRangeFilter() {
        _dateRangeFilter.value = null // Set the filter to null
        applyFilters() // Re-apply all filters, which will now exclude the date range
    }
}