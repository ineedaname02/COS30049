package com.example.myPlant.ui.plantlocationmap

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.myPlant.R
import com.example.myPlant.data.model.PlantViewModel
import com.example.myPlant.data.model.PlantViewModelFactory
import com.example.myPlant.data.repository.ObservationRepository
import com.example.myPlant.data.repository.PlantRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.example.myPlant.BuildConfig
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.Gradient
import android.graphics.Color
import com.google.android.gms.maps.model.TileOverlayOptions
class PlantLocationMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    // Use activityViewModels to share the ViewModel across fragments.
    private val viewModel: PlantViewModel by activityViewModels {
        PlantViewModelFactory(
            plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
            observationRepository = ObservationRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plant_location_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set up the map and start loading data.
        setupMap()
        viewModel.loadAllObservations()
    }

    /**
     * Initializes the map fragment and requests the map asynchronously.
     */
    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    /**
     * This callback is triggered when the map is ready to be used.
     * This is the main entry point for map interaction.
     */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        observeObservations()
    }

    /**
     * Observes the LiveData from the ViewModel and updates the map when data changes.
     */
    private fun observeObservations() {
        viewModel.allObservations.observe(viewLifecycleOwner) { observations ->
            // Defensive check to ensure we don't process null or empty data.
            if (observations.isNullOrEmpty()) {
                Log.d("PlantLocationMap", "No observations found to display.")
                return@observe
            }
            googleMap.clear()
            addHeatmapToMap(observations)
        }
    }
    /**
     * ✅ REPLACED `addMarkersToMap` WITH THIS NEW FUNCTION
     * Creates a heatmap layer from the observation data.
     */
    private fun addHeatmapToMap(observations: List<Map<String, Any>>) {
        val latLngs = mutableListOf<LatLng>()
        val boundsBuilder = LatLngBounds.Builder()

        for (obs in observations) {
            val geoMap = obs["geolocation"] as? Map<*, *>
            val lat = geoMap?.get("lat") as? Double
            val lng = geoMap?.get("lng") as? Double

            if (lat != null && lng != null) {
                val position = LatLng(lat, lng)
                latLngs.add(position) // Add position to the list for the heatmap
                boundsBuilder.include(position) // Also add to bounds for zooming
            }
        }

        if (latLngs.isEmpty()) {
            Log.d("PlantLocationMap", "No valid LatLngs found for heatmap.")
            return
        }

        // --- HEATMAP CREATION ---

        // 1. Define the color gradient for the heatmap
        // (e.g., from transparent blue to hot red)
        val gradient = Gradient(
            intArrayOf(Color.BLUE, Color.GREEN, Color.YELLOW, Color.RED), // Colors
            floatArrayOf(0.2f, 0.4f, 0.6f, 1.0f) // Start points for each color
        )

        // 2. Create the heatmap provider with our location data
        val heatmapProvider = HeatmapTileProvider.Builder()
            .data(latLngs)
            .gradient(gradient)
            .radius(50) // Adjust radius for desired "blob" size
            .build()

        // 3. Add the heatmap as a tile overlay on the map
        googleMap.addTileOverlay(
            TileOverlayOptions().tileProvider(heatmapProvider)
        )

        // --- END HEATMAP CREATION ---

        // Animate camera to show the area where data exists
        val bounds = boundsBuilder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }
}
