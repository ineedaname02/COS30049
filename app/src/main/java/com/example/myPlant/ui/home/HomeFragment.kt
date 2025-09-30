package com.example.myPlant.ui.home

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myPlant.PlantViewModelFactory
import com.example.myPlant.databinding.FragmentHomeBinding

import com.example.myPlant.data.model.PlantViewModel
import com.example.myPlant.data.model.PlantNetResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import com.example.myPlant.BuildConfig

import androidx.recyclerview.widget.LinearLayoutManager

import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.data.repository.FirebaseRepository

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PlantViewModel
    private lateinit var adapter: SelectedImagesAdapter
    private val selectedImageUris = mutableListOf<Uri>()

    private lateinit var firebaseRepository: FirebaseRepository

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(uris.take(5)) // limit to 5

            adapter = SelectedImagesAdapter(selectedImageUris)
            binding.imageRecyclerView.adapter = adapter
            binding.imageRecyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            binding.textHome.text = "Selected ${selectedImageUris.size} image(s)"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // ViewModel setup
        val repository = PlantRepository(BuildConfig.PLANTNET_API_KEY)
        val factory = PlantViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[PlantViewModel::class.java]

        //this above viewModel.result.observe(viewLifecycleOwner)
        firebaseRepository = FirebaseRepository(requireContext())

        viewModel.result.observe(viewLifecycleOwner) { response ->
            showResults(response)
            firebaseRepository.uploadPlantResult(response, selectedImageUris)
        }

        viewModel.error.observe(viewLifecycleOwner) {
            binding.textHome.text = "Error: $it"
        }



        // Upload button
        binding.buttonUpload.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Send button
        binding.buttonSend.setOnClickListener {
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
            Toast.makeText(requireContext(), "Thanks for confirming!", Toast.LENGTH_SHORT).show()
        }

        val firebaseRepo = FirebaseRepository(requireContext())

        binding.buttonWrong.setOnClickListener {
            val plantId = "7a9481e2-4dfa-449f-944a-7ec87f713776" // This should come from the plant you uploaded
            firebaseRepo.flagPlant(plantId, "User reported incorrect identification")
        }

        return root
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

    private fun showResults(response: PlantNetResponse?) {
        val results = response?.results
        if (results.isNullOrEmpty()) {
            binding.textHome.text = "No results found"
            return
        }

        val sb = StringBuilder()
        for (result in results) {
            val speciesName = result.species?.scientificNameWithoutAuthor ?: "Unknown"
            val commonName = result.species?.commonNames?.joinToString() ?: "No common name"
            val score = String.format("%.2f", result.score ?: 0.0)

            sb.append("Species: $speciesName\n")
            sb.append("Common Name(s): $commonName\n")
            sb.append("Confidence: $score\n\n")
        }

        binding.textHome.text = sb.toString().trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}