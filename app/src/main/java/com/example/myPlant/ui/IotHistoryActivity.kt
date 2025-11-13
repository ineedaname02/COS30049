package com.example.myPlant.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.databinding.ActivityHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.myPlant.ui.iot.IotReading
import com.example.myPlant.ui.iot.IotHistoryAdapter


class IotHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val historyList = mutableListOf<IotReading>()
    private val adapter = IotHistoryAdapter(historyList)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        db.collection("readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
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
                            timestamp = doc.getString("timestamp") ?: "--"
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            }
    }
}
