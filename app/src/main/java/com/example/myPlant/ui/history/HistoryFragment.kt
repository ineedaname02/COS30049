package com.example.myPlant.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // ✅ Import activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.BuildConfig
import com.example.myPlant.data.model.PlantViewModel // ✅ Use the shared PlantViewModel
import com.example.myPlant.data.model.PlantViewModelFactory
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.data.repository.PlantRepository
import com.example.myPlant.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // ✅ Use the shared PlantViewModel, same as the map fragment
    private val viewModel: PlantViewModel by activityViewModels {
        PlantViewModelFactory(
            plantRepository = PlantRepository(BuildConfig.PLANTNET_API_KEY),
            firebaseRepository = FirebaseRepository(requireContext())
        )
    }

    // The adapter is now correctly typed for `Observation`
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

        // Setup RecyclerView
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = adapter

        // Check if user is logged in
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            Toast.makeText(requireContext(), "Please log in to view your history", Toast.LENGTH_SHORT).show()
            binding.textEmptyHistory.text = "Login required to see history."
            binding.textEmptyHistory.visibility = View.VISIBLE
            binding.historyRecyclerView.visibility = View.GONE
            return
        }

        // ✅ Observe the correct LiveData from the shared ViewModel
        viewModel.userObservations.observe(viewLifecycleOwner) { observations ->
            if (observations.isNullOrEmpty()) {
                binding.textEmptyHistory.text = "You have no observation history yet."
                binding.textEmptyHistory.visibility = View.VISIBLE
                binding.historyRecyclerView.visibility = View.GONE
            } else {
                binding.textEmptyHistory.visibility = View.GONE
                binding.historyRecyclerView.visibility = View.VISIBLE
                // The adapter's submitList will handle updates efficiently
                adapter.submitList(observations)
            }
        }

        // ✅ Call the correct loading function from the shared ViewModel
        viewModel.loadUserObservations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}