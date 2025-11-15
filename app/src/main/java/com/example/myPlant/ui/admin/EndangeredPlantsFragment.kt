package com.example.myPlant.ui.admin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.data.model.EndangeredData
import com.example.myPlant.databinding.FragmentEndangeredPlantsBinding
import com.example.myPlant.data.model.EndangeredPlantsViewModel
import com.example.myPlant.ui.admin.EndangeredPlantsViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.myPlant.data.repository.FirebaseRepository

class EndangeredPlantsFragment : Fragment() {

    private var _binding: FragmentEndangeredPlantsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: EndangeredPlantsViewModel
    private lateinit var adapter: EndangeredPlantsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEndangeredPlantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel with Factory
        val repository = FirebaseRepository(requireContext())
        val factory = EndangeredPlantsViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(EndangeredPlantsViewModel::class.java)

        setupRecyclerView()
        setupObservers()

        // Load data
        viewModel.loadEndangeredPlants()
    }

    private fun setupRecyclerView() {
        adapter = EndangeredPlantsAdapter(emptyList()) { plant ->
            // Handle item click
            Log.d("EndangeredPlants", "Clicked on: ${plant.scientificName}")
            showPlantDetails(plant)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EndangeredPlantsFragment.adapter
        }
    }

    private fun setupObservers() {
        // Observe plants list
        lifecycleScope.launch {
            viewModel.endangeredPlants.collect { plants ->
                adapter.updateList(plants)
                updateEmptyState(plants.isEmpty())
            }
        }

        // Observe loading state
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Observe error messages
        lifecycleScope.launch {
            viewModel.errorMessage.collect { message ->
                message?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    viewModel.errorMessageShown()
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showPlantDetails(plant: EndangeredData) {
        // Show plant details in a dialog or navigate to details screen
        val message = """
            Scientific Name: ${plant.scientificName}
            Common Name: ${plant.commonName.ifEmpty { "None" }}
            IUCN Category: ${plant.iucnCategory}
            Plant ID: ${plant.plantId}
            Observation ID: ${plant.observationId}
            Added by: ${plant.addedBy}
            Notes: ${plant.notes}
        """.trimIndent()

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}