package com.example.myPlant.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class IotDashboardActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvSensorReading: TextView
    private lateinit var btnViewHistory: Button
    private lateinit var btnRefreshData: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iot_dashboard)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        db = FirebaseFirestore.getInstance()

        tvSensorReading = findViewById(R.id.tvSensorReading)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        btnRefreshData = findViewById(R.id.btnRefreshData)

        loadLatestReading()

        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnRefreshData.setOnClickListener {
            loadLatestReading()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadLatestReading() {
        db.collection("readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]

                    val temp = doc.getDouble("temperature") ?: 0.0
                    val humidity = doc.getDouble("humidity") ?: 0.0
                    val moisture = doc.getDouble("moisture") ?: 0.0
                    val rain = doc.getLong("rain") ?: 0
                    val sound = doc.getLong("sound") ?: 0
                    val timestamp = doc.getString("timestamp") ?: "--"

                    tvSensorReading.text = """
                        Temperature: $temp °C
                        Humidity: $humidity %
                        Moisture: $moisture
                        Rain: $rain
                        Sound: $sound
                        Updated: $timestamp
                    """.trimIndent()
                } else {
                    tvSensorReading.text = "No data available"
                }
            }
            .addOnFailureListener {
                tvSensorReading.text = "Error loading data"
            }
    }
}
