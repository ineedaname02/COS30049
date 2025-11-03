package com.example.myPlant.ui.plantlocationmap

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
//import androidx.privacysandbox.tools.core.generator.build
import com.example.myPlant.R
import com.example.myPlant.data.model.TrainingData
import com.example.myPlant.databinding.FragmentTrainingDataMapBinding
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.*
// Import java.util.Date for the date picker
import java.util.Date as UtilDate
import com.google.type.Date as GoogleDate
import java.util.Calendar

class PlantLocationMapFragment : Fragment(), OnMapReadyCallback {

    // 1. ✅ DECLARE BINDING VARIABLES
    private var _binding: FragmentTrainingDataMapBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var googleMap: GoogleMap
    private var isHeatmapMode = true
    private var isMapReady = false
    private var heatmapOverlay: TileOverlay? = null

    private val viewModel: TrainingMapViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // 2. ✅ INFLATE THE LAYOUT USING VIEW BINDING
        _binding = FragmentTrainingDataMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) { renderer ->
            Log.d("MapInitializer", "Using Google Maps renderer: ${renderer.name}")
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.training_map_container) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // These lines will now work correctly
        binding.rarityFilterChips.setOnCheckedChangeListener { group, checkedId ->
            val newFilter = when (checkedId) {
                R.id.chip_critically_endangered -> "Critically Endangered"
                R.id.chip_endangered -> "Endangered"
                R.id.chip_vulnerable -> "Vulnerable"
                R.id.chip_extinct -> "Extinct"
                R.id.chip_extinct_in_wild -> "Extinct in the Wild"
                R.id.chip_near_threatened -> "Near Threatened"
                R.id.chip_least_concern -> "Least Concern"
                R.id.chip_data_deficient -> "Data Deficient"
                R.id.chip_not_evaluated -> "Not Evaluated"
                else -> "All"
            }
            viewModel.setRarityFilter(newFilter)
        }

        binding.dateFilterButton.setOnClickListener {
            showDateRangePicker()
        }
        binding.dateFilterResetButton.setOnClickListener {
            // Reset the button's text to its default state
            binding.dateFilterButton.text = "Filter by Date"
            // Hide the reset button again
            binding.dateFilterResetButton.visibility = View.GONE
            // Call the new function in the ViewModel to clear the filter
            viewModel.clearDateRangeFilter()
        }

        setupUIListeners()
    }

    // 3. ✅ CLEAN UP THE BINDING IN onDestroyView
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(requireContext()))

        viewModel.trainingDataForMap.observe(viewLifecycleOwner) { trainingData ->
            updateMapDisplay(trainingData)
        }
        viewModel.loadTrainingDataForMap()
    }

    private fun setupUIListeners() {
        // Use 'binding' to access views
        binding.trainingMapToggleHeatmap.isChecked = isHeatmapMode
        binding.trainingMapToggleHeatmap.setOnCheckedChangeListener { _, isChecked ->
            isHeatmapMode = isChecked
            if (isMapReady) {
                updateMapDisplay(viewModel.trainingDataForMap.value)
            }
        }

        binding.mapSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.filterMapData(query)
                binding.mapSearchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterMapData(newText)
                return true
            }
        })
    }

    private fun updateMapDisplay(data: List<TrainingData>?) {
        if (!isMapReady || data == null) return
        googleMap.clear()
        heatmapOverlay?.remove()

        if (data.isEmpty()) {
            Log.d("TrainingDataMap", "No training data to display.")
            return
        }

        if (isHeatmapMode) {
            addHeatmapToMap(data)
        } else {
            addMarkersToMap(data)
        }
    }

    private fun addMarkersToMap(data: List<TrainingData>) {
        googleMap.clear()
        val boundsBuilder = LatLngBounds.Builder()

        for (item in data) {
            val lat = item.geolocation?.lat
            val lng = item.geolocation?.lng
            if (lat != null && lng != null) {
                val position = LatLng(lat, lng)
                val markerTitle = item.plantId.ifEmpty { "Unknown" }

                val marker = googleMap.addMarker(
                    MarkerOptions().position(position).title(markerTitle)
                )
                marker?.tag = item // This is the critical link!

                boundsBuilder.include(position)
            }
        }
        if (data.isNotEmpty()) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
        }
    }

    private fun addHeatmapToMap(data: List<TrainingData>) {
        val latLngs = data.mapNotNull { it.geolocation?.let { geo -> LatLng(geo.lat, geo.lng) } }
        if (latLngs.isEmpty()) return

        val gradient = Gradient(
            intArrayOf(Color.BLUE, Color.GREEN, Color.RED),
            floatArrayOf(0.2f, 0.5f, 0.9f)
        )
        val provider = HeatmapTileProvider.Builder().data(latLngs).gradient(gradient).radius(50).build()
        heatmapOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))

        try {
            val bounds = LatLngBounds.Builder().apply { latLngs.forEach { include(it) } }.build()
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: IllegalStateException) {
            Log.w("TrainingDataMap", "Could not animate camera for heatmap: ${e.message}")
        }
    }

    private fun showDateRangePicker() {
        val datePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select a Date Range")
            .build()

        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            val startDateLong = dateSelection.first
            val endDateLong = dateSelection.second

            val startDateAsUtilDate = UtilDate(startDateLong)
            val endDateAsUtilDate = UtilDate(endDateLong)

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.dateFilterButton.text = "Date: ${sdf.format(startDateAsUtilDate)} - ${sdf.format(endDateAsUtilDate)}"

            // ✅ Make the reset button visible now that a filter is active
            binding.dateFilterResetButton.visibility = View.VISIBLE

            viewModel.setDateRangeFilter(startDateAsUtilDate, endDateAsUtilDate)
        }

        datePicker.show(childFragmentManager, "DATE_PICKER")
    }
}
