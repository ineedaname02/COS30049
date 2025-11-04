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
                val data = firebaseRepository.getTrainingDataForMap()
                allTrainingData = data

                // Detailed debugging
                Log.d("TrainingMapViewModel", "=== TRAINING DATA DEBUG ===")
                Log.d("TrainingMapViewModel", "Total items fetched: ${data.size}")

                // Check items with geolocation
                val itemsWithGeo = data.filter { it.geolocation != null }
                Log.d("TrainingMapViewModel", "Items with geolocation: ${itemsWithGeo.size}")

                // Check items with IUCN categories
                val itemsWithIucn = data.filter { !it.iucnCategory.isNullOrEmpty() }
                Log.d("TrainingMapViewModel", "Items with IUCN category: ${itemsWithIucn.size}")

                // Log specific IUCN categories found
                val uniqueIucnCategories = itemsWithIucn.mapNotNull { it.iucnCategory }.distinct()
                Log.d("TrainingMapViewModel", "Unique IUCN categories: $uniqueIucnCategories")

                // Log a few sample items for inspection
                data.take(3).forEachIndexed { index, item ->
                    Log.d("TrainingMapViewModel", "Sample $index: plantId=${item.plantId}, " +
                            "geo=${item.geolocation != null}, " +
                            "iucn=${item.iucnCategory ?: "null"}")
                }

                applyFilters()
                Log.d("TrainingMapViewModel", "Final filtered count: ${_filteredTrainingDataForMap.value?.size ?: 0}")

            } catch (e: Exception) {
                Log.e("TrainingMapViewModel", "Error loading training data for map", e)
                _filteredTrainingDataForMap.value = emptyList()
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
                filteredList = filteredList.filter { trainingData ->
                    // Handle both null and empty IUCN categories
                    val iucn = trainingData.iucnCategory?.trim()?.lowercase()
                    when (rarity.lowercase()) {
                        "vulnerable" -> iucn == "vulnerable"
                        "near threatened" -> iucn == "near threatened" || iucn == "near_threatened"
                        "least concern" -> iucn == "least concern" || iucn == "least_concern"
                        "data deficient" -> iucn == "data deficient" || iucn == "data_deficient"
                        "not evaluated" -> iucn == "not evaluated" || iucn == "not_evaluated"
                        else -> iucn == rarity.trim().lowercase()
                    }
                }
            }
        }

        // 2. Apply date range filter (your existing code is fine)
        _dateRangeFilter.value?.let { dateRange ->
            val (start, end) = dateRange
            val inclusiveEndDate = Calendar.getInstance().apply {
                time = end
                add(Calendar.DAY_OF_YEAR, 1)
            }.time

            filteredList = filteredList.filter {
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