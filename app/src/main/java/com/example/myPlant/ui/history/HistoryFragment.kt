package com.example.myPlant.ui.history

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
import com.example.myPlant.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by activityViewModels {
        PlantViewModelFactory(
            plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
            firebaseRepository = FirebaseRepository(requireContext())
        )
    }

    private val adapter = HistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = adapter

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            binding.textEmptyHistory.apply {
                text = "Login required to see your history."
                visibility = View.VISIBLE
            }
            binding.historyRecyclerView.visibility = View.GONE
            Toast.makeText(requireContext(), "Please log in to view your history", Toast.LENGTH_SHORT).show()
            return
        }

        // Observe the observations data
        viewModel.userObservations.observe(viewLifecycleOwner) { observations ->
            if (observations.isNullOrEmpty()) {
                binding.textEmptyHistory.apply {
                    text = "You have no observation history yet."
                    visibility = View.VISIBLE
                }
                binding.historyRecyclerView.visibility = View.GONE
            } else {
                binding.textEmptyHistory.visibility = View.GONE
                binding.historyRecyclerView.visibility = View.VISIBLE
                adapter.submitList(observations)
            }
        }

        // Load data if not already present
        if (viewModel.userObservations.value.isNullOrEmpty()) {
            viewModel.loadUserObservations()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}