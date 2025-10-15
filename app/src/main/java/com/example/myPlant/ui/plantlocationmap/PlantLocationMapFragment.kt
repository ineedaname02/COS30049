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
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.text.format
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.maps.model.*

class PlantLocationMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var heatmapToggle: SwitchMaterial
    private var isHeatmapMode = true // Default to heatmap mode

    private var lastObservations: List<Map<String, Any>>? = null
    private var heatmapOverlay: TileOverlay? = null
    private var isMapReady = false // ✅ Track if the map is initialized


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
        val view = inflater.inflate(R.layout.fragment_plant_location_map, container, false)
        heatmapToggle = view.findViewById(R.id.toggle_heatmap)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()

        heatmapToggle.setOnCheckedChangeListener { _, isChecked ->
            isHeatmapMode = isChecked
            if (isMapReady) {
                updateMapDisplay()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        // --- This is the guaranteed safe place to initialize and use the map ---
        googleMap = map
        isMapReady = true // ✅ Mark the map as ready
        Log.d("PlantLocationMap", "Google Map is ready.")

        // Start observing ViewModel data only AFTER the map is ready
        observeObservations()

        // Load the initial data
        viewModel.loadAllObservations()
    }

    private fun observeObservations() {
        viewModel.allObservations.observe(viewLifecycleOwner) { observations ->
            if (observations.isNullOrEmpty()) {
                Log.d("PlantLocationMap", "No observations found to display.")
                return@observe
            }
            // Store the data and update the map display based on the current mode
            lastObservations = observations
            updateMapDisplay()
        }
    }

    private fun updateMapDisplay() {
        // A check just in case, though the logic flow should prevent this
        if (!::googleMap.isInitialized) {
            Log.e("PlantLocationMap", "updateMapDisplay called before googleMap was initialized.")
            return
        }

        googleMap.clear()
        heatmapOverlay = null

        lastObservations?.let { observations ->
            if (isHeatmapMode) {
                addHeatmapToMap(observations)
            } else {
                addMarkersToMap(observations)
            }
        }
    }

    /**
     * Initializes the map fragment and requests the map asynchronously.
     */
    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    /**
     * Creates and adds individual markers to the map.
     */
    private fun addMarkersToMap(observations: List<Map<String, Any>>) {
        val boundsBuilder = LatLngBounds.Builder()

        for (obs in observations) {
            val geoMap = obs["geolocation"] as? Map<*, *>
            val lat = geoMap?.get("lat") as? Double
            val lng = geoMap?.get("lng") as? Double

            if (lat != null && lng != null) {
                val position = LatLng(lat, lng)

                // Extract details for the marker's info window
                val currentIdMap = obs["currentIdentification"] as? Map<*, *>
                val scientificName = currentIdMap?.get("scientificName") as? String ?: "N/A"

                val timestamp = obs["timestamp"] as? com.google.firebase.Timestamp // Use the full path to be explicit
                val dateString = timestamp?.toDate()?.let {
                    SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(it)
                } ?: "Unknown date"

                // Rarity would be a complex calculation, so we'll use confidence as a proxy for now
                val confidence = (currentIdMap?.get("confidence") as? Double)?.let {
                    "Confidence: ${"%.1f".format(it * 100)}%"
                } ?: ""

                googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(scientificName)
                        .snippet("$dateString | $confidence")
                )
                boundsBuilder.include(position)
            }
        }

        if (boundsBuilder.build().center != LatLng(0.0, 0.0)) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
        }
    }

    /**
     * Creates and adds a heatmap layer to the map.
     */
    private fun addHeatmapToMap(observations: List<Map<String, Any>>) {
        val latLngs = mutableListOf<LatLng>()
        for (obs in observations) {
            val geoMap = obs["geolocation"] as? Map<*, *>
            val lat = geoMap?.get("lat") as? Double
            val lng = geoMap?.get("lng") as? Double
            if (lat != null && lng != null) {
                latLngs.add(LatLng(lat, lng))
            }
        }

        if (latLngs.isEmpty()) return

        val gradient = Gradient(
            intArrayOf(Color.BLUE, Color.GREEN, Color.YELLOW, Color.RED),
            floatArrayOf(0.2f, 0.4f, 0.6f, 1.0f)
        )

        val heatmapProvider = HeatmapTileProvider.Builder()
            .data(latLngs)
            .gradient(gradient)
            .radius(50)
            .build()

        // Add the overlay and store it
        heatmapOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(heatmapProvider))

        // Zoom to show all points
        val boundsBuilder = LatLngBounds.Builder()
        latLngs.forEach { boundsBuilder.include(it) }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
    }
}
