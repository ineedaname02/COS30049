package com.example.myPlant.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.BuildConfig
import com.example.myPlant.LoginActivity
import com.example.myPlant.PlantViewModelFactory
import com.example.myPlant.data.model.*
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.databinding.FragmentHomeBinding
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PlantViewModel
    private lateinit var adapter: SelectedImagesAdapter
    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var firebaseRepository: FirebaseRepository

    // Firebase Auth
    private lateinit var auth: FirebaseAuth

    // GPS Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    private var currentObservationId: String? = null
    private var currentAiSuggestions: List<AISuggestion> = emptyList()

    // Permission request
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getCurrentLocation()
            }
            else -> {
                Toast.makeText(requireContext(), "Location permission denied. Uploading without location data.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // Check auth before allowing image selection
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to upload images", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            selectedImageUris.clear()
            selectedImageUris.addAll(uris.take(5)) // limit to 5

            adapter = SelectedImagesAdapter(selectedImageUris)
            binding.imageRecyclerView.adapter = adapter
            binding.imageRecyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            binding.textHome.text = "Selected ${selectedImageUris.size} image(s)"

            // Request location when images are selected
            requestLocationPermission()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Verify user authentication
        if (!isUserAuthenticated()) {
            showAuthenticationRequired()
            return root
        }

        // Location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // ViewModel setup
        val repository = PlantRepository(BuildConfig.PLANTNET_API_KEY)
        val factory = PlantViewModelFactory(repository)
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[PlantViewModel::class.java]

        firebaseRepository = FirebaseRepository(requireContext())

        // Observe PlantNet API results
        viewModel.result.observe(viewLifecycleOwner) { response ->
            showResults(response)
            lifecycleScope.launch {
                uploadObservationToFirebase(response)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) {
            binding.textHome.text = "Error: $it"
        }

        setupUI()
        return root
    }

    override fun onResume() {
        super.onResume()
        // Re-check authentication when fragment becomes visible
        if (!isUserAuthenticated()) {
            showAuthenticationRequired()
        }
    }

    private fun setupUI() {
        // Upload button
        binding.buttonUpload.setOnClickListener {
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to upload images", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            pickImageLauncher.launch("image/*")
        }

        // Send button
        binding.buttonSend.setOnClickListener {
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to identify plants", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (selectedImageUris.isNotEmpty()) {
                val imageParts = selectedImageUris.map { uri -> prepareImagePart(uri) }
                val organParts = List(imageParts.size) {
                    MultipartBody.Part.createFormData("organs", "leaf")
                }
                viewModel.identifyPlant(
                    images = imageParts,
                    organs = organParts,
                    project = "all"
                )
            } else {
                binding.textHome.text = "Please upload at least one image first."
            }
        }

        // Correct button
        binding.buttonCorrect.setOnClickListener {
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to confirm identifications", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            currentObservationId?.let { observationId ->
                val topSuggestion = currentAiSuggestions.firstOrNull()
                if (topSuggestion != null) {
                    lifecycleScope.launch {
                        firebaseRepository.confirmObservation(
                            observationId = observationId,
                            plantId = topSuggestion.plantId,
                            scientificName = topSuggestion.scientificName
                        ).onSuccess {
                            Toast.makeText(requireContext(), "Thanks for confirming! Added to training data.", Toast.LENGTH_SHORT).show()
                            resetUI()
                        }.onFailure { exception ->
                            Toast.makeText(requireContext(), "Confirmation failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "No AI suggestions available", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(requireContext(), "No observation to confirm", Toast.LENGTH_SHORT).show()
        }

        // Wrong button
        binding.buttonWrong.setOnClickListener {
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to flag identifications", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            currentObservationId?.let { observationId ->
                lifecycleScope.launch {
                    firebaseRepository.flagObservation(
                        observationId = observationId,
                        reason = "unsure"
                    ).onSuccess {
                        Toast.makeText(requireContext(), "Image sent to experts for review.", Toast.LENGTH_LONG).show()
                        resetUI()
                    }.onFailure { exception ->
                        Toast.makeText(requireContext(), "Flagging failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: Toast.makeText(requireContext(), "No observation to flag", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null && !auth.currentUser!!.isAnonymous
    }

    private fun showAuthenticationRequired() {
        binding.textHome.text = "Please log in to use plant identification features"

        // Disable all interactive elements
        binding.buttonUpload.isEnabled = false
        binding.buttonSend.isEnabled = false
        binding.buttonCorrect.isEnabled = false
        binding.buttonWrong.isEnabled = false

        // Show login prompt
        binding.textHome.setOnClickListener {
            // Navigate to login activity
            val intent = Intent(requireContext(), LoginActivity::class.java)
            startActivity(intent)
        }

        Toast.makeText(requireContext(), "Authentication required", Toast.LENGTH_LONG).show()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    currentLocation = locationResult.lastLocation
                    fusedLocationClient.removeLocationUpdates(this)

                    currentLocation?.let { location ->
                        binding.textHome.append("\n📍 Location: ${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}")
                    }
                }
            },
            Looper.getMainLooper()
        )
    }

    private suspend fun uploadObservationToFirebase(response: PlantNetResponse?) {
        // Double-check authentication before upload
        if (!isUserAuthenticated()) {
            Toast.makeText(requireContext(), "Please log in to upload observations", Toast.LENGTH_LONG).show()
            return
        }

        val geoLocation = currentLocation?.let {
            GeoLocation(it.latitude, it.longitude)
        }

        val smartPlantAISuggestions = emptyList<AISuggestion>() // future AI

        firebaseRepository.uploadPlantObservation(
            plantNetResponse = response,
            smartPlantAISuggestions = smartPlantAISuggestions,
            imageUris = selectedImageUris,
            userNote = "User submitted plant for identification",
            location = geoLocation
        ).onSuccess { id ->
            currentObservationId = id
            currentAiSuggestions = response?.results?.mapIndexed { index, plantNetResult ->
                AISuggestion(
                    suggestionId = "plantnet_$index",
                    source = "plantnet",
                    plantId = generatePlantId(plantNetResult),
                    scientificName = plantNetResult.species?.scientificNameWithoutAuthor ?: "Unknown",
                    commonNames = plantNetResult.species?.commonNames ?: emptyList(),
                    confidence = plantNetResult.score ?: 0.0,
                    externalIds = ExternalIds(
                        plantNetId = plantNetResult.species?.scientificNameWithoutAuthor,
                        gbifId = plantNetResult.gbif?.id?.toString(),
                        powoId = plantNetResult.powo?.id
                    )
                )
            } ?: emptyList()
        }.onFailure { exception ->
            Toast.makeText(requireContext(), "Upload failed: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generatePlantId(result: Result): String {
        val scientificName = result.species?.scientificNameWithoutAuthor ?: "unknown"
        return scientificName.replace("[^A-Za-z0-9]".toRegex(), "_").lowercase()
    }

    private fun showResults(response: PlantNetResponse?) {
        val results = response?.results
        if (results.isNullOrEmpty()) {
            binding.textHome.text = "No results found"
            return
        }

        val sb = StringBuilder("🌿 PlantNet Identification Results:\n\n")
        for (result in results.take(3)) {
            val speciesName = result.species?.scientificNameWithoutAuthor ?: "Unknown"
            val commonName = result.species?.commonNames?.joinToString() ?: "No common name"
            val confidencePercent = (result.score ?: 0.0) * 100
            sb.append("🔍 Species: $speciesName\n")
            sb.append("📝 Common: $commonName\n")
            sb.append("🎯 Confidence: ${"%.1f".format(confidencePercent)}%\n---\n")
        }

        currentLocation?.let { location ->
            sb.append("\n📍 Location: ${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}\n")
        }

        sb.append("\nPlease confirm if the identification is correct or flag for expert review.")
        binding.textHome.text = sb.toString().trim()
    }

    private fun prepareImagePart(uri: Uri): MultipartBody.Part {
        val context = requireContext()
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, getFileNameFromUri(uri))
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("images", file.name, requestBody)
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "image.jpg"
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
        return name
    }

    private fun resetUI() {
        selectedImageUris.clear()
        currentObservationId = null
        currentAiSuggestions = emptyList()
        currentLocation = null
        binding.imageRecyclerView.adapter = null
        binding.imageRecyclerView.layoutManager = null
        binding.textHome.text = "Upload plant images for identification"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}