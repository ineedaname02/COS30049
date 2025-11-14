package com.example.myPlant.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.databinding.ActivityIotHistoryBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.myPlant.ui.iot.IotReading
import com.example.myPlant.ui.iot.IotHistoryAdapter

class IotHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIotHistoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val historyList = mutableListOf<IotReading>()
    private lateinit var adapter: IotHistoryAdapter

    private lateinit var btnAllDevices: MaterialButton
    private lateinit var btnDevice001: MaterialButton
    private lateinit var btnDevice002: MaterialButton

    private var currentFilter: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIotHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        btnAllDevices = binding.btnAllDevices
        btnDevice001 = binding.btnDevice001
        btnDevice002 = binding.btnDevice002

        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = IotHistoryAdapter(historyList)
        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = adapter

        setupFilterButtons()

        loadHistory()
    }

    private fun setupFilterButtons() {
        btnAllDevices.setOnClickListener {
            setActiveFilter("all")
            loadHistory()
        }

        btnDevice001.setOnClickListener {
            setActiveFilter("device001")
            loadHistory()
        }

        btnDevice002.setOnClickListener {
            setActiveFilter("device002")
            loadHistory()
        }

        setActiveFilter("all")
    }

    private fun setActiveFilter(filter: String) {
        currentFilter = filter

        val activeColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
        val inactiveColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        val whiteColor = ContextCompat.getColor(this, android.R.color.white)

        btnAllDevices.setBackgroundColor(inactiveColor)
        btnDevice001.setBackgroundColor(inactiveColor)
        btnDevice002.setBackgroundColor(inactiveColor)

        btnAllDevices.setTextColor(whiteColor)
        btnDevice001.setTextColor(whiteColor)
        btnDevice002.setTextColor(whiteColor)

        // Set active button
        when (filter) {
            "all" -> {
                btnAllDevices.setBackgroundColor(activeColor)
            }
            "device001" -> {
                btnDevice001.setBackgroundColor(activeColor)
            }
            "device002" -> {
                btnDevice002.setBackgroundColor(activeColor)
            }
        }
    }

    private fun loadHistory() {
        var query = db.collection("readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        if (currentFilter != "all") {
            query = query.whereEqualTo("deviceId", currentFilter)
        }

        query.get()
            .addOnSuccessListener { documents ->
                historyList.clear()
                for (doc in documents) {
                    historyList.add(
                        IotReading(
                            deviceId = doc.getString("deviceId") ?: "--",
                            temperature = doc.getDouble("temperature") ?: 0.0,
                            humidity = doc.getDouble("humidity") ?: 0.0,
                            moisture = doc.getDouble("moisture") ?: 0.0,
                            rain = doc.getLong("rain") ?: 0,
                            sound = doc.getLong("sound") ?: 0,
                            lightDigital = doc.getLong("lightDigital") ?: 0,
                            timestamp = doc.getString("timestamp") ?: "--"
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("IotHistoryActivity", "Error: ${exception.message}")
            }
    }
}