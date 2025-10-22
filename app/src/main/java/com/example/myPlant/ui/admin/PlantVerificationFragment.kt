package com.example.myPlant.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myPlant.R
import com.example.myPlant.data.repository.PlantRepository
import kotlinx.coroutines.launch
import android.widget.TextView

class PlantVerificationFragment : Fragment() {

    private lateinit var plantRepository: PlantRepository
    private lateinit var textResult: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_plant_verification, container, false)
        textResult = view.findViewById(R.id.textResult)

        plantRepository = PlantRepository("YOUR_API_KEY_HERE")

        // Example test
        lifecycleScope.launch {
            val status = plantRepository.getIucnStatus("Nepenthes rafflesiana")
            textResult.text = status ?: "No status found"
        }

        return view
    }
}
