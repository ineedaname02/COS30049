package com.example.myPlant.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.BuildConfig
import com.example.myPlant.LoginActivity
import com.example.myPlant.data.model.PlantViewModelFactory
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

    //Send Button
    private var latestIucnCategory: String? = null
    private var lastClickTime = 0L
    private val debounceInterval = 1000L
    private var hasUploadedOnce = false

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
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to upload images", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            selectedImageUris.clear()
            selectedImageUris.addAll(uris.take(5))

            adapter = SelectedImagesAdapter(selectedImageUris)
            binding.imageRecyclerView.adapter = adapter
            binding.imageRecyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            // Make preview visible when images are selected
            binding.imageRecyclerView.visibility = View.VISIBLE

            binding.textHome.text = "Selected ${selectedImageUris.size} image(s)"
            requestLocationPermission()
        }
    }

    private var cameraImageUri: Uri? = null


    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraImageUri != null) {
            // Check auth again
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to upload images", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            // Add captured image to list (append or replace as you prefer)
            // Here we append to the list; if you want only one image, use clear() first
            selectedImageUris.add(0, cameraImageUri!!) // add to front
            // Limit to 5 images
            if (selectedImageUris.size > 5) {
                // drop extras from the end
                while (selectedImageUris.size > 5) selectedImageUris.removeAt(selectedImageUris.lastIndex)
            }

            // Update adapter / UI
            adapter = SelectedImagesAdapter(selectedImageUris)
            binding.imageRecyclerView.adapter = adapter
            binding.imageRecyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            // Show the preview bar when a photo is taken
            binding.imageRecyclerView.visibility = View.VISIBLE

            binding.textHome.text = "Selected ${selectedImageUris.size} image(s)"

            // Request location now that we have an image
            requestLocationPermission()

            // Optional: automatically kick off identification/upload
            // lifecycleScope.launch { doAutoIdentifyOrUpload() }
        } else {
            Toast.makeText(requireContext(), "Photo capture cancelled or failed", Toast.LENGTH_SHORT).show()
        }
    }


    fun launchCamera() {
        try {
            // Create a temporary file in cache dir
            val photoFile = File.createTempFile(
                "plant_photo_${System.currentTimeMillis()}_",
                ".jpg",
                requireContext().cacheDir
            ).apply {
                // optional: deleteOnExit won't work reliably on Android; manage cleanup if desired
            }

            // Generate a content:// Uri via FileProvider
            cameraImageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                photoFile
            )

            // Launch camera app to save image to that Uri
            takePhotoLauncher.launch(cameraImageUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Unable to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
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
        val plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY)
        firebaseRepository = FirebaseRepository(requireContext())
        val factory = PlantViewModelFactory(plantRepository, firebaseRepository)
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[PlantViewModel::class.java]

        firebaseRepository = FirebaseRepository(requireContext())

        viewModel.result.observe(viewLifecycleOwner) { response ->
            showResults(response, latestIucnCategory) // pass the category if available

            val topSpecies = response?.results?.firstOrNull()?.species?.scientificNameWithoutAuthor
            if (!topSpecies.isNullOrBlank()) {
                viewModel.fetchIucnStatus(topSpecies)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) {
            binding.textHome.text = "Error: $it"
            hideLoadingIndicator()
        }

        // Loading state observer
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showLoadingIndicator()
            } else {
                hideLoadingIndicator()
            }
        }

        // Loading message observer
        viewModel.loadingMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                binding.loadingMessage.text = message
            }
        }

        if (viewModel.lastImageUris.isNotEmpty()) {
            val adapter = ImagePreviewAdapter(viewModel.lastImageUris)
            binding.imageRecyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            binding.imageRecyclerView.adapter = adapter
            binding.imageRecyclerView.visibility = View.VISIBLE
        }

        viewModel.iucnStatus.observe(viewLifecycleOwner) { category ->
            latestIucnCategory = category

            // Rebuild full result view including the new IUCN info
            showResults(viewModel.result.value, latestIucnCategory)

            if (!hasUploadedOnce && viewModel.result.value != null) {
                hasUploadedOnce = true
                lifecycleScope.launch {
                    uploadObservationToFirebase(viewModel.result.value, category)
                }
            }
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
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < debounceInterval) {
                return@setOnClickListener // Ignore click if within debounce interval
            }
            lastClickTime = currentTime

            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to identify plants", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            binding.imageRecyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            if (selectedImageUris.isNotEmpty()) {
                // Set up adapter
                val adapter = ImagePreviewAdapter(selectedImageUris)
                binding.imageRecyclerView.adapter = adapter

                // Ensure preview is visible while sending/uploading
                binding.imageRecyclerView.visibility = View.VISIBLE

                // Store in ViewModel for restoration
                viewModel.lastImageUris = selectedImageUris

                val imageParts = selectedImageUris.map { uri -> prepareImagePart(uri) }
                val organParts = List(imageParts.size) {
                    MultipartBody.Part.createFormData("organs", "leaf")
                }

                viewModel.identifyPlant(images = imageParts, organs = organParts, project = "all")
                hasUploadedOnce = false
            } else {
                binding.textHome.text = "Please upload at least one image first."
            }

        }

        binding.buttonCorrect.setOnClickListener {
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to confirm identifications", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            currentObservationId?.let { observationId ->
                val topSuggestion = currentAiSuggestions.firstOrNull()
                if (topSuggestion != null) {
                    lifecycleScope.launch {
                        try {
                            firebaseRepository.confirmObservation(
                                observationId = observationId,
                                plantId = topSuggestion.plantId,
                                scientificName = topSuggestion.scientificName
                            )
                            Toast.makeText(requireContext(), "Thanks for confirming! Added to training data.", Toast.LENGTH_SHORT).show()
                            resetUI()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Confirmation failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "No AI suggestions available", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(requireContext(), "No observation to confirm", Toast.LENGTH_SHORT).show()
        }


        // Wrong button
        // ✅ Wrong button
        binding.buttonWrong.setOnClickListener {
            if (!isUserAuthenticated()) {
                Toast.makeText(requireContext(), "Please log in to flag identifications", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            currentObservationId?.let { observationId ->
                lifecycleScope.launch {
                    try {
                        firebaseRepository.flagObservation(
                            observationId = observationId,
                            reason = "unsure"
                        )
                        Toast.makeText(requireContext(), "Image sent to experts for review.", Toast.LENGTH_LONG).show()
                        resetUI()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Flagging failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        binding.textHome.append("\n Location: ${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}")
                    }
                }
            },
            Looper.getMainLooper()
        )
    }

    private suspend fun uploadObservationToFirebase(
        response: PlantNetResponse?,
        iucnCategory: String? = null
    ) {
        // Double-check authentication before upload
        if (!isUserAuthenticated()) {
            Toast.makeText(requireContext(), "Please log in to upload observations", Toast.LENGTH_LONG).show()
            return
        }

        // Show upload loading indicator
        requireActivity().runOnUiThread {
            binding.loadingMessage.text = "Uploading to database..."
            binding.loadingContainer.visibility = View.VISIBLE
        }

        val geoLocation = currentLocation?.let {
            GeoLocation(it.latitude, it.longitude)
        }

        val smartPlantAISuggestions = emptyList<AISuggestion>() // future AI

        try {
            val id = firebaseRepository.uploadPlantObservation(
                plantNetResponse = response,
                smartPlantAISuggestions = smartPlantAISuggestions,
                imageUris = selectedImageUris,
                userNote = "User submitted plant for identification",
                location = geoLocation,
                iucnCategory = iucnCategory
            )

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

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            // Hide loading indicator
            requireActivity().runOnUiThread {
                binding.loadingContainer.visibility = View.GONE
            }
        }

    }


    private fun generatePlantId(result: Result): String {
        val scientificName = result.species?.scientificNameWithoutAuthor ?: "unknown"
        return scientificName.replace("[^A-Za-z0-9]".toRegex(), "_").lowercase()
    }

    private fun showResults(response: PlantNetResponse?, iucnCategory: String? = null) {
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

        if (iucnCategory != null) {
            sb.append("\n🦎 IUCN Red List: $iucnCategory")
        }

        sb.append("\n\nPlease confirm if the identification is correct or flag for expert review.")
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
        binding.imageRecyclerView.visibility = View.GONE
        binding.textHome.text = "Upload plant images for identification"
    }

    // ---------------------------
    // LOADING INDICATOR METHODS
    // ---------------------------
    private fun showLoadingIndicator() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.buttonSend.isEnabled = false
        binding.buttonSend.text = "Analyzing..."
        binding.buttonUpload.isEnabled = false
    }

    private fun hideLoadingIndicator() {
        binding.loadingContainer.visibility = View.GONE
        binding.buttonSend.isEnabled = true
        binding.buttonSend.text = "Send to PlantNet"
        binding.buttonUpload.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}