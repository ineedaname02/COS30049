package com.example.myPlant.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.example.myPlant.R
import com.example.myPlant.data.model.PlantObservation
import com.example.myPlant.databinding.FragmentPlantVerificationBinding
import com.google.firebase.auth.FirebaseAuth

class PlantVerificationFragment : Fragment() {

    // --- ViewModel ---
    private val adminViewModel: AdminViewModel by viewModels()

    // --- View Binding ---
    private var _binding: FragmentPlantVerificationBinding? = null
    private val binding get() = _binding!!

    // --- Data holders ---
    private var pendingList: MutableList<PlantObservation> = mutableListOf()
    private var currentIndex = 0
    private var currentObs: PlantObservation? = null
    private val adminId get() = FirebaseAuth.getInstance().currentUser?.uid ?: "admin_unknown"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlantVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupListeners()
        adminViewModel.fetchPendingObservations(limit = 30)
    }

    private fun setupObservers() {
        // Observe loading state
        adminViewModel.isLoading.observe(viewLifecycleOwner, Observer { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        })

        // Observe messages (Toast)
        adminViewModel.message.observe(viewLifecycleOwner, Observer { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        })

        // Observe pending observations
        adminViewModel.pendingObservations.observe(viewLifecycleOwner, Observer { list ->
            pendingList.clear()
            pendingList.addAll(list)
            currentIndex = 0
            if (pendingList.isNotEmpty()) displayObservation(pendingList[0])
            else showEmptyState()
        })
    }

    private fun setupListeners() {
        binding.btnWrong.setOnClickListener { showCorrectionForm() }
        binding.btnCorrect.setOnClickListener { onAdminMarkCorrect() }
        binding.btnSubmitCorrection.setOnClickListener { onSubmitCorrection() }
        binding.btnNext.setOnClickListener { showNext() }
    }

    private fun showEmptyState() {
        binding.tvAiName.text = "No pending observations"
        binding.tvConfidence.text = ""
        binding.tvIucn.text = ""
        binding.imgObservation.setImageResource(R.drawable.ic_launcher_foreground)
        binding.correctionForm.visibility = View.GONE
    }

    private fun displayObservation(obs: PlantObservation) {
        currentObs = obs
        binding.tvAiName.text = "AI GIVEN NAME: ${obs.scientificName}"
        binding.tvConfidence.text = String.format("Confidence: %.2f", obs.confidence)
        binding.tvIucn.text = "IUCN: ${obs.iucnCategory ?: "-"}"
        binding.correctionForm.visibility = View.GONE
        binding.etCorrectedScientific.setText("")
        binding.etCorrectedCommon.setText("")

        val url = obs.imageUrls.firstOrNull()
        if (!url.isNullOrEmpty()) Glide.with(this).load(url).into(binding.imgObservation)
        else binding.imgObservation.setImageResource(R.drawable.ic_launcher_foreground)
    }

    private fun showCorrectionForm() {
        binding.correctionForm.visibility = View.VISIBLE
    }

    private fun onAdminMarkCorrect() {
        val obs = currentObs ?: return
        adminViewModel.processAdminValidation(
            observationId = obs.id,
            adminId = adminId,
            isCorrect = true
        )
        removeCurrentAndAdvance()
    }

    private fun onSubmitCorrection() {
        val obs = currentObs ?: return
        val correctedSci = binding.etCorrectedScientific.text.toString().trim()
        val correctedCommon = binding.etCorrectedCommon.text.toString().trim().ifEmpty { null }

        if (correctedSci.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter corrected scientific name", Toast.LENGTH_SHORT).show()
            return
        }

        adminViewModel.processAdminValidation(
            observationId = obs.id,
            adminId = adminId,
            isCorrect = false,
            correctedScientificName = correctedSci,
            correctedCommonName = correctedCommon
        )
        removeCurrentAndAdvance()
    }

    private fun removeCurrentAndAdvance() {
        if (currentIndex < pendingList.size) {
            pendingList.removeAt(currentIndex)
        }
        if (pendingList.isEmpty()) {
            showEmptyState()
        } else if (currentIndex >= pendingList.size) {
            displayObservation(pendingList.last())
        } else {
            displayObservation(pendingList[currentIndex])
        }
    }

    private fun showNext() {
        if (pendingList.isEmpty()) {
            adminViewModel.fetchPendingObservations(limit = 30)
            return
        }
        currentIndex = (currentIndex + 1) % pendingList.size
        displayObservation(pendingList[currentIndex])
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
