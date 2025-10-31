package com.example.myPlant.ui.plantlocationmap

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView // Use the correct SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels // Use the correct 'by viewModels'
import com.example.myPlant.R
import com.example.myPlant.data.model.TrainingData
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import java.text.SimpleDateFormat
import java.util.*

class PlantLocationMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var heatmapToggle: SwitchMaterial
    private lateinit var searchView: SearchView

    private var isHeatmapMode = true
    private var isMapReady = false
    private var heatmapOverlay: TileOverlay? = null

    // ✅ Use the new, dedicated ViewModel for this fragment ONLY.
    private val viewModel: TrainingMapViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Use a clean layout. If you don't have this, we can create it.
        val view = inflater.inflate(R.layout.fragment_training_data_map, container, false)
        heatmapToggle = view.findViewById(R.id.training_map_toggle_heatmap)
        searchView = view.findViewById(R.id.map_search_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) { renderer ->
            Log.d("MapInitializer", "Using Google Maps renderer: ${renderer.name}")
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.training_map_container) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        setupUIListeners()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true
        googleMap.uiSettings.isZoomControlsEnabled = true

        googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(requireContext()))
        // Observe the LiveData from our new ViewModel
        viewModel.trainingDataForMap.observe(viewLifecycleOwner) { trainingData ->
            updateMapDisplay(trainingData)
        }

        // Load the data
        viewModel.loadTrainingDataForMap()
    }

    private fun setupUIListeners() {
        heatmapToggle.isChecked = isHeatmapMode
        heatmapToggle.setOnCheckedChangeListener { _, isChecked ->
            isHeatmapMode = isChecked
            if (isMapReady) {
                updateMapDisplay(viewModel.trainingDataForMap.value)
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.filterMapData(query)
                searchView.clearFocus()
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
        val boundsBuilder = LatLngBounds.Builder()
        for (item in data) {
            val lat = item.geolocation?.lat
            val lng = item.geolocation?.lng
            if (lat != null && lng != null) {
                val position = LatLng(lat, lng)
                val markerTitle = item.plantId.ifEmpty { "Unknown" }

                // Create the marker and add the TrainingData object as a tag
                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(markerTitle)
                )
                marker?.tag = item // ✅ ATTACH THE ENTIRE DATA OBJECT TO THE MARKER
                boundsBuilder.include(position)
            }
        }
        try {
            if (data.isNotEmpty()) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
            }
        } catch (e: IllegalStateException) {
            Log.w("TrainingDataMap", "Could not animate camera for markers: ${e.message}")
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


}
    