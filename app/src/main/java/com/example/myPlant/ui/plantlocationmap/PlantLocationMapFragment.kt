package com.example.myPlant.ui.plantlocationmap

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.myPlant.R
import com.example.myPlant.BuildConfig
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


//class PlantLocationMapFragment : Fragment(), OnMapReadyCallback {
//
//    private lateinit var googleMap: GoogleMap
//    private lateinit var heatmapToggle: SwitchMaterial
//    private var isHeatmapMode = true
//    private var isMapReady = false
//    private var lastObservations: List<Map<String, Any>>? = null
//    private var heatmapOverlay: TileOverlay? = null
//
//    private val viewModel: PlantViewModel by activityViewModels {
//        PlantViewModelFactory(
//            plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
//            firebaseRepository = FirebaseRepository(requireContext())
//        )
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
//    ): View {
//        val view = inflater.inflate(R.layout.fragment_plant_location_map, container, false)
//        heatmapToggle = view.findViewById(R.id.toggle_heatmap)
//        return view
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        setupMap()
//
//        heatmapToggle.setOnCheckedChangeListener { _, isChecked ->
//            isHeatmapMode = isChecked
//            if (isMapReady) updateMapDisplay()
//        }
//    }
//
//    private fun setupMap() {
//        val mapFragment =
//            childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
//        mapFragment?.getMapAsync(this)
//    }
//
//    override fun onMapReady(map: GoogleMap) {
//        googleMap = map
//        isMapReady = true
//        Log.d("PlantLocationMap", "Google Map is ready")
//
//        if (lastObservations == null) {
//            observeObservations()
//            viewModel.loadAllObservations()
//        } else {
//            updateMapDisplay()
//        }
//    }
//
//    private fun observeObservations() {
//        viewModel.allObservations.observe(viewLifecycleOwner) { observations ->
//            if (observations.isNullOrEmpty()) {
//                Log.d("PlantLocationMap", "No observations found.")
//                return@observe
//            }
//            lastObservations = observations
//            updateMapDisplay()
//        }
//    }
//
//    private fun updateMapDisplay() {
//        if (!::googleMap.isInitialized || lastObservations == null) return
//
//        googleMap.clear()
//        heatmapOverlay = null
//
//        if (isHeatmapMode)
//            addHeatmapToMap(lastObservations!!)
//        else
//            addMarkersToMap(lastObservations!!)
//    }
//
//    private fun addMarkersToMap(observations: List<Map<String, Any>>) {
//        val boundsBuilder = LatLngBounds.Builder()
//        var hasPoints = false
//
//        for (obs in observations) {
//            val geoMap = obs["geolocation"] as? Map<*, *>
//            val lat = geoMap?.get("lat") as? Double
//            val lng = geoMap?.get("lng") as? Double
//            if (lat == null || lng == null) continue
//
//            val position = LatLng(lat, lng)
//            val idMap = obs["currentIdentification"] as? Map<*, *>
//            val name = idMap?.get("scientificName") as? String ?: "Unknown"
//            val confidence = (idMap?.get("confidence") as? Double)?.let {
//                "Confidence: ${"%.1f".format(it * 100)}%"
//            } ?: ""
//
//            val timestamp = obs["timestamp"]
//            val dateString = when (timestamp) {
//                is com.google.firebase.Timestamp ->
//                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(timestamp.toDate())
//                is Long ->
//                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
//                else -> "Unknown date"
//            }
//
//            googleMap.addMarker(
//                MarkerOptions()
//                    .position(position)
//                    .title(name)
//                    .snippet("$dateString | $confidence")
//            )
//
//            boundsBuilder.include(position)
//            hasPoints = true
//        }
//
//        if (hasPoints) {
//            try {
//                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
//            } catch (e: Exception) {
//                Log.w("PlantLocationMap", "Could not set camera bounds: ${e.message}")
//            }
//        }
//    }
//
//    private fun addHeatmapToMap(observations: List<Map<String, Any>>) {
//        val latLngs = observations.mapNotNull {
//            val geo = it["geolocation"] as? Map<*, *>
//            val lat = geo?.get("lat") as? Double
//            val lng = geo?.get("lng") as? Double
//            if (lat != null && lng != null) LatLng(lat, lng) else null
//        }
//
//        if (latLngs.isEmpty()) return
//
//        val gradient = Gradient(
//            intArrayOf(Color.BLUE, Color.GREEN, Color.YELLOW, Color.RED),
//            floatArrayOf(0.2f, 0.4f, 0.6f, 1f)
//        )
//
//        val provider = HeatmapTileProvider.Builder()
//            .data(latLngs)
//            .gradient(gradient)
//            .radius(50)
//            .build()
//
//        heatmapOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
//
//        try {
//            val bounds = LatLngBounds.Builder().apply { latLngs.forEach { include(it) } }.build()
//            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
//        } catch (e: Exception) {
//            Log.w("PlantLocationMap", "Heatmap bounds error: ${e.message}")
//        }
//    }
//}
