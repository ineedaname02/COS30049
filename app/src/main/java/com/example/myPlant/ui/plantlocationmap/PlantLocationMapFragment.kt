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
class PlantLocationMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    // Use activityViewModels to share the ViewModel across fragments.
    private val viewModel: PlantViewModel by activityViewModels {
        // ✅ Use the new, cleaner factory

        PlantViewModelFactory(
            plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
            observationRepository = ObservationRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // This is a standard and clean way to inflate the layout.
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
            // Clear previous markers to prevent duplicates on data refresh.
            googleMap.clear()
            addMarkersToMap(observations)
        }
    }

    /**
     * Adds markers for each observation to the map and moves the camera to fit them all.
     */
    private fun addMarkersToMap(observations: List<Map<String, Any>>) {
        // LatLngBounds will help us zoom to fit all markers on the screen.
        val boundsBuilder = LatLngBounds.Builder()
        var markersAdded = 0

        for (obs in observations) {
            // Safely extract data from the map, providing default values.
            val lat = obs["latitude"] as? Double
            val lng = obs["longitude"] as? Double
            val plantName = obs["plantName"] as? String ?: "Observation"
            val scientificName = obs["scientificName"] as? String ?: "N/A"

            // Only add a marker if both latitude and longitude are valid.
            if (lat != null && lng != null) {
                val position = LatLng(lat, lng)
                googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(plantName)
                        .snippet(scientificName)
                )
                boundsBuilder.include(position) // Add the marker's position to the bounds.
                markersAdded++
            }
        }

        // Only move the camera if at least one valid marker was added.
        if (markersAdded > 0) {
            val bounds = boundsBuilder.build()
            // Animate the camera to show all markers with a 100dp padding.
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }
}
