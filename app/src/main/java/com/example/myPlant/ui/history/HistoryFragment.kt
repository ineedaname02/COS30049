package com.example.myPlant.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.data.repository.FirebaseRepository
import com.example.myPlant.databinding.FragmentHistoryBinding
import com.example.myPlant.data.model.PlantHistoryViewModel
import com.google.firebase.auth.FirebaseAuth

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PlantHistoryViewModel
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

        val repository = FirebaseRepository(requireContext())
        viewModel = PlantHistoryViewModel(repository)

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && !user.isAnonymous) {
            val userId = user.uid
            viewModel.loadUserHistory(userId)
        } else {
            Toast.makeText(requireContext(), "Please log in to view your history", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.history.observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                binding.textEmptyHistory.visibility = View.VISIBLE
                binding.historyRecyclerView.visibility = View.GONE
            } else {
                binding.textEmptyHistory.visibility = View.GONE
                binding.historyRecyclerView.visibility = View.VISIBLE
                adapter.submitList(list)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
