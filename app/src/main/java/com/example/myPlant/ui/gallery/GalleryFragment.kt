package com.example.myPlant.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.BuildConfig
import com.example.myPlant.data.model.PlantViewModel
import com.example.myPlant.data.model.PlantViewModelFactory
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.databinding.FragmentGalleryBinding
import com.example.myPlant.ui.history.HistoryAdapter

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by activityViewModels {
        PlantViewModelFactory(
            plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
            firebaseRepository = FirebaseRepository(requireContext())
        )
    }

    private val adapter = HistoryAdapter() // Reuse the same adapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.galleryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.galleryRecyclerView.adapter = adapter

        viewModel.allObservations.observe(viewLifecycleOwner) { observations ->
            if (observations.isNullOrEmpty()) {
                binding.textEmptyGallery.text = "No plants have been uploaded yet."
                binding.textEmptyGallery.visibility = View.VISIBLE
                binding.galleryRecyclerView.visibility = View.GONE
            } else {
                binding.textEmptyGallery.visibility = View.GONE
                binding.galleryRecyclerView.visibility = View.VISIBLE
                adapter.submitList(observations)
            }
        }

        viewModel.loadAllObservations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}