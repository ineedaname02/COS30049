package com.example.myPlant.ui.plantlocationmap

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.example.myPlant.R
import com.example.myPlant.BuildConfig
import com.example.myPlant.data.model.Observation
import com.example.myPlant.data.model.PlantViewModel
import com.example.myPlant.data.model.PlantViewModelFactory
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.data.repository.PlantRepository
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
    private var isHeatmapMode = true
    private var isMapReady = false
    private var heatmapOverlay: TileOverlay? = null

    // Use a shared ViewModel to get observation data
    private val viewModel: PlantViewModel by activityViewModels {
        PlantViewModelFactory(
            plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
            firebaseRepository = FirebaseRepository(requireContext())
        )
    }

    private val args: PlantLocationMapFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_plant_location_map, container, false)
        heatmapToggle = view.findViewById(R.id.toggle_heatmap)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()

        // ✅ Show or hide the heatmap toggle based on the mode
        if (args.mapMode == "MY_HISTORY") {
            heatmapToggle.visibility = View.GONE
            isHeatmapMode = false // Default to markers for personal history
        } else {
            heatmapToggle.visibility = View.VISIBLE
            heatmapToggle.isChecked = isHeatmapMode
            heatmapToggle.setOnCheckedChangeListener { _, isChecked ->
                isHeatmapMode = isChecked
                if (isMapReady) updateMapDisplay(viewModel.allObservations.value)
            }
        }
    }

    private fun setupMap() {
        // Use childFragmentManager for fragments inside fragments
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true
        Log.d("PlantLocationMap", "Map is ready in ${args.mapMode} mode.")

        // ✅ Decide which data to load and observe based on the mode
        if (args.mapMode == "MY_HISTORY") {
            viewModel.userObservations.observe(viewLifecycleOwner) { observations ->
                updateMapDisplay(observations)
            }
            viewModel.loadUserObservations()
        } else { // GLOBAL mode
            viewModel.allObservations.observe(viewLifecycleOwner) { observations ->
                updateMapDisplay(observations)
            }
            viewModel.loadAllObservations()
        }
    }

    private fun updateMapDisplay(observations: List<Observation>?) {
        if (!::googleMap.isInitialized || observations == null) {
            Log.d("PlantLocationMap", "Map or observations not ready.")
            return
        }
        googleMap.clear()

        // ✅ Apply admin/specialist logic HERE in the future
        // For example:
        // val filteredObservations = if (user.isAdmin) observations else observations.filter { it.iucnCategory != "Endangered" }
        // Then pass filteredObservations to the functions below.

        if (isHeatmapMode) {
            addHeatmapToMap(observations)
        } else {
            addMarkersToMap(observations)
        }
    }

    private fun addMarkersToMap(observations: List<Observation>) {
        if (observations.isEmpty()) return
        val boundsBuilder = LatLngBounds.Builder()

        for (obs in observations) {
            val lat = obs.geolocation?.lat
            val lng = obs.geolocation?.lng
            if (lat == null || lng == null) continue

            val position = LatLng(lat, lng)
            val name = obs.currentIdentification.scientificName.ifEmpty { "Unknown" }
            val snippet = obs.timestamp?.toDate()?.let {
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
            } ?: "Observation"

            googleMap.addMarker(MarkerOptions().position(position).title(name).snippet(snippet))
            boundsBuilder.include(position)
        }

        googleMap.setOnMapLoadedCallback {
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
            } catch (e: IllegalStateException) {
                Log.w("PlantLocationMap", "Could not animate camera to bounds: ${e.message}")
            }
        }
    }

    private fun addHeatmapToMap(observations: List<Observation>) {
        val latLngs = observations.mapNotNull { obs ->
            obs.geolocation?.let { LatLng(it.lat, it.lng) }
        }

        if (latLngs.isEmpty()) return

        val gradient = Gradient(
            intArrayOf(Color.BLUE, Color.GREEN, Color.YELLOW, Color.RED), // Colors for the heatmap
            floatArrayOf(0.2f, 0.4f, 0.6f, 1f) // Distribution points for the colors
        )

        val provider = HeatmapTileProvider.Builder()
            .data(latLngs)
            .gradient(gradient)
            .radius(50) // Radius of influence for each data point in pixels
            .build()

        heatmapOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))

        // Zoom camera to fit all heatmap points
        try {
            val bounds = LatLngBounds.Builder().apply { latLngs.forEach { include(it) } }.build()
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: IllegalStateException) {
            Log.w("PlantLocationMap", "Heatmap bounds error: ${e.message}")
        }
    }
}
