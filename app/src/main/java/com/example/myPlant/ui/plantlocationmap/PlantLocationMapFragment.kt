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
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.MapsInitializer
import com.example.myPlant.data.local.AppDatabase

class PlantLocationMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var heatmapToggle: SwitchMaterial
    private var isHeatmapMode = true
    private var isMapReady = false
    private var heatmapOverlay: TileOverlay? = null

    private val viewModel: PlantViewModel by activityViewModels {PlantViewModelFactory(
        plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
        firebaseRepository = FirebaseRepository(AppDatabase.getDatabase(requireContext()).observationDao()),
        context = requireContext() // ✅ Add context
    )
    }

    private val args: PlantLocationMapFragmentArgs by navArgs()
    private var currentMapMode: String = "GLOBAL" // A reliable variable to hold the true mode

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_plant_location_map, container, false)
        heatmapToggle = view.findViewById(R.id.toggle_heatmap)

        // **DETERMINE THE TRUE MODE HERE, ONCE.**
        // This is the failsafe check that bypasses the argument bug.
        val previousDestinationId = findNavController().previousBackStackEntry?.destination?.id
        currentMapMode = if (previousDestinationId == R.id.action_global_nav_my_observations_map) {
            "MY_HISTORY"
        } else {
            "GLOBAL" // Default to GLOBAL for all other cases
        }
        Log.d("PlantLocationMap", "MODE DETERMINED in onCreateView: $currentMapMode")

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) { renderer ->
            Log.d("PlantLocationMap", "Maps renderer initialized: $renderer")
        }

        setupMap() // This will call onMapReady where data is loaded

        // **ALL UI LOGIC STAYS IN onViewCreated**
        // This ensures the heatmap toggle is correctly managed every time the view is created.
        if (currentMapMode == "MY_HISTORY") {
            isHeatmapMode = false // History map shows markers
            heatmapToggle.visibility = View.GONE
        } else { // GLOBAL mode
            isHeatmapMode = true // Global map shows heatmap
            heatmapToggle.visibility = View.VISIBLE
            heatmapToggle.isChecked = true

            // Set up the listener only for the global map
            heatmapToggle.setOnCheckedChangeListener { _, isChecked ->
                isHeatmapMode = isChecked
                if (isMapReady) {
                    updateMapDisplay(viewModel.allObservations.value)
                }
            }
        }
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true

        // ✅ SET THE CUSTOM INFO WINDOW ADAPTER
        googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(requireContext()))

        viewModel.allObservations.removeObservers(viewLifecycleOwner)
        viewModel.userObservations.removeObservers(viewLifecycleOwner)

        Log.d("PlantLocationMap", "Loading data for mode: $currentMapMode")

        if (currentMapMode == "MY_HISTORY") {
            // Load and observe user data
            viewModel.userObservations.observe(viewLifecycleOwner) { observations ->
                Log.d("PlantLocationMap", "Updating map with ${observations?.size ?: 0} user observations.")
                updateMapDisplay(observations)
            }
            viewModel.loadUserObservations()
        } else { // GLOBAL mode
            // Load and observe global data
            viewModel.allObservations.observe(viewLifecycleOwner) { observations ->
                Log.d("PlantLocationMap", "Updating map with ${observations?.size ?: 0} global observations.")
                updateMapDisplay(observations)
            }
            viewModel.loadAllObservations()
        }
    }

    private fun updateMapDisplay(observations: List<Observation>?) {
        if (!::googleMap.isInitialized || !isMapReady || observations == null) {
            Log.w("PlantLocationMap", "Cannot update map, not ready or observations are null.")
            return
        }
        googleMap.clear()
        heatmapOverlay?.remove() // Explicitly remove old heatmap overlay

        if (observations.isEmpty()) {
            Log.d("PlantLocationMap", "No observations to display.")
            return
        }

        if (isHeatmapMode) {
            addHeatmapToMap(observations)
        } else {
            addMarkersToMap(observations)
        }
    }

    private fun addMarkersToMap(observations: List<Observation>) {
        val boundsBuilder = LatLngBounds.Builder()
        for (obs in observations) {
            val lat = obs.geolocation?.lat
            val lng = obs.geolocation?.lng
            if (lat != null && lng != null) {
                val position = LatLng(lat, lng)
                val name = obs.currentIdentification.scientificName.ifEmpty { "Unknown" }

                val markerColor = when (obs.iucnCategory) {
                    // ... (your existing color logic) ...
                    "Endangered", "Critically Endangered" -> BitmapDescriptorFactory.HUE_RED
                    "Vulnerable" -> BitmapDescriptorFactory.HUE_ORANGE
                    "Near Threatened" -> BitmapDescriptorFactory.HUE_YELLOW
                    else -> BitmapDescriptorFactory.HUE_ROSE
                }

                // The snippet is no longer needed, as the custom window will show the info.
                // You can leave it empty or remove it.
                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(name)
                        // .snippet("") // This is no longer necessary
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                )

                // ✅ THIS IS THE KEY: Attach the full Observation object to the marker.
                marker?.tag = obs

                boundsBuilder.include(position)
            }
        }
        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
        } catch (e: IllegalStateException) {
            // Happens if no markers were added
            Log.w("PlantLocationMap", "Could not animate camera for markers: ${e.message}")
        }
    }

    private fun addHeatmapToMap(observations: List<Observation>) {
        val latLngs = observations.mapNotNull { obs ->
            obs.geolocation?.let { LatLng(it.lat, it.lng) }
        }
        if (latLngs.isEmpty()) return

        val gradient = Gradient(
            intArrayOf(Color.BLUE, Color.YELLOW, Color.RED),
            floatArrayOf(0.2f, 0.6f, 1.0f)
        )
        val provider = HeatmapTileProvider.Builder()
            .data(latLngs)
            .gradient(gradient)
            .radius(50)
            .build()
        heatmapOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))

        try {
            val bounds = LatLngBounds.Builder().apply { latLngs.forEach { include(it) } }.build()
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: IllegalStateException) {
            Log.w("PlantLocationMap", "Could not animate camera for heatmap: ${e.message}")
        }
    }
}