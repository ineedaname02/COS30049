package com.example.myPlant.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.R
import com.example.myPlant.databinding.ActivityIotHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.myPlant.ui.iot.IotReading
import com.example.myPlant.ui.iot.IotHistoryAdapter

class IotHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIotHistoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val historyList = mutableListOf<IotReading>()
    private lateinit var adapter: IotHistoryAdapter

    private var currentFilter: String = "All"
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIotHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = IotHistoryAdapter(historyList)
        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = adapter

        setupDeviceFilter()
        setupSearch()

        loadHistory()
    }

    private fun setupDeviceFilter() {
        val devices = arrayOf("All", "Device 001", "Device 002")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, devices)
        binding.spinnerDeviceFilter.setAdapter(spinnerAdapter)

        binding.spinnerDeviceFilter.setOnItemClickListener { _, _, position, _ ->
            currentFilter = devices[position]
            loadHistory()
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString()
                loadHistory()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadHistory() {
        var query: Query = db.collection("readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        if (currentFilter != "All") {
            query = query.whereEqualTo("deviceId", currentFilter.replace(" ", "").toLowerCase())
        }

        query.get()
            .addOnSuccessListener { documents ->
                historyList.clear()
                val filteredList = documents.mapNotNull { doc ->
                    val reading = IotReading(
                        deviceId = doc.getString("deviceId") ?: "--",
                        temperature = doc.getDouble("temperature") ?: 0.0,
                        humidity = doc.getDouble("humidity") ?: 0.0,
                        moisture = doc.getDouble("moisture") ?: 0.0,
                        rain = doc.getLong("rain") ?: 0,
                        sound = doc.getLong("sound") ?: 0,
                        lightDigital = doc.getLong("lightDigital") ?: 0,
                        timestamp = doc.getString("timestamp") ?: "--"
                    )

                    if (searchQuery.isBlank() || reading.deviceId.contains(searchQuery, true)) {
                        reading
                    } else {
                        null
                    }
                }
                historyList.addAll(filteredList)
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("IotHistoryActivity", "Error: ${exception.message}")
            }
    }
}