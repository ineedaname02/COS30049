package com.example.myPlant.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.example.myPlant.R
import com.google.firebase.auth.FirebaseAuth
import com.example.myPlant.data.model.Observation


class PlantVerificationFragment : Fragment() {

    // --- ViewModel ---
    private val adminViewModel: AdminViewModel by viewModels()

    // --- UI elements ---
    private lateinit var imgObservation: ImageView
    private lateinit var tvAiName: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvIucn: TextView
    private lateinit var btnWrong: Button
    private lateinit var btnCorrect: Button
    private lateinit var btnNext: Button
    private lateinit var correctionForm: LinearLayout
    private lateinit var etCorrectedScientific: EditText
    private lateinit var etCorrectedCommon: EditText
    private lateinit var btnSubmitCorrection: Button
    private lateinit var progress: ProgressBar

    // --- Data holders ---
    private var pendingList: MutableList<Observation> = mutableListOf()
    private var currentIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_plant_verification, container, false)
        setupUi(root)
        setupObservers()
        setupListeners()
        adminViewModel.fetchPendingObservations(limit = 30)
        return root
    }

    private fun setupUi(root: View) {
        imgObservation = root.findViewById(R.id.imgObservation)
        tvAiName = root.findViewById(R.id.tvAiName)
        tvConfidence = root.findViewById(R.id.tvConfidence)
        tvIucn = root.findViewById(R.id.tvIucn)
        btnWrong = root.findViewById(R.id.btnWrong)
        btnCorrect = root.findViewById(R.id.btnCorrect)
        btnNext = root.findViewById(R.id.btnNext)
        correctionForm = root.findViewById(R.id.correctionForm)
        etCorrectedScientific = root.findViewById(R.id.etCorrectedScientific)
        etCorrectedCommon = root.findViewById(R.id.etCorrectedCommon)
        btnSubmitCorrection = root.findViewById(R.id.btnSubmitCorrection)
        progress = root.findViewById(R.id.progress)
    }

    private fun setupObservers() {
        // Observe loading state
        adminViewModel.isLoading.observe(viewLifecycleOwner, Observer { loading ->
            progress.visibility = if (loading) View.VISIBLE else View.GONE
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
        btnWrong.setOnClickListener { showCorrectionForm() }
        btnCorrect.setOnClickListener { onAdminMarkCorrect() }
        btnSubmitCorrection.setOnClickListener { onSubmitCorrection() }
        btnNext.setOnClickListener { showNext() }
    }

    private fun showEmptyState() {
        tvAiName.text = "No pending observations"
        tvConfidence.text = ""
        tvIucn.text = ""
        imgObservation.setImageResource(R.drawable.ic_launcher_foreground)

        correctionForm.visibility = View.GONE
    }

    private fun displayObservation(obs: Observation) {
        tvAiName.text = "AI GIVEN NAME: ${obs.currentIdentification.scientificName}"
        tvConfidence.text = String.format("Confidence: %.2f", obs.currentIdentification.confidence * 100)
        tvIucn.text = "IUCN: ${obs.iucnCategory ?: "-"}"
        correctionForm.visibility = View.GONE
        etCorrectedScientific.setText("")
        etCorrectedCommon.setText("")

        val url = obs.plantImageUrls.firstOrNull()
        if (!url.isNullOrEmpty()) {
            Glide.with(this).load(url).into(imgObservation)
        } else {
            imgObservation.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    private fun showCorrectionForm() {
        correctionForm.visibility = View.VISIBLE
    }

    private fun onAdminMarkCorrect() {
        if (pendingList.isEmpty() || currentIndex >= pendingList.size) return
        val obs = pendingList[currentIndex] // ✅ Get current item from the list
        adminViewModel.processAdminValidation(
            observationId = obs.observationId, // ✅ Use new property
            isCorrect = true
        )
        removeCurrentAndAdvance()
    }

    private fun onSubmitCorrection() {
        if (pendingList.isEmpty() || currentIndex >= pendingList.size) return
        val obs = pendingList[currentIndex] // ✅ Get current item from the list
        val correctedSci = etCorrectedScientific.text.toString().trim()

        if (correctedSci.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter corrected scientific name", Toast.LENGTH_SHORT).show()
            return
        }

        adminViewModel.processAdminValidation(
            observationId = obs.observationId, // ✅ Use new property
            isCorrect = false,
            correctedScientificName = correctedSci
        )
        removeCurrentAndAdvance()
    }

    private fun removeCurrentAndAdvance() {
        if (currentIndex < pendingList.size) {
            pendingList.removeAt(currentIndex)
        }
        if (pendingList.isEmpty()) {
            showEmptyState()
        } else {
            // Ensure index is valid after removal
            if (currentIndex >= pendingList.size) {
                currentIndex = pendingList.lastIndex
            }
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
}
