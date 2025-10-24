package com.example.myPlant.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class IotDashboardActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var textTemperature: TextView
    private lateinit var textHumidity: TextView
    private lateinit var textMoisture: TextView
    private lateinit var textRain: TextView
    private lateinit var textSound: TextView
    private lateinit var textTimestamp: TextView
    private lateinit var btnViewHistory: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iot_dashboard)

        db = FirebaseFirestore.getInstance()

        textTemperature = findViewById(R.id.textTemperature)
        textHumidity = findViewById(R.id.textHumidity)
        textMoisture = findViewById(R.id.textMoisture)
        textRain = findViewById(R.id.textRain)
        textSound = findViewById(R.id.textSound)
        textTimestamp = findViewById(R.id.textTimestamp)
        btnViewHistory = findViewById(R.id.btnViewHistory)

        listenToRealtimeData()

        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun listenToRealtimeData() {
        db.collection("readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    textTimestamp.text = "Error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val data = snapshot.documents[0]
                    val temp = data.getDouble("temperature") ?: 0.0
                    val humidity = data.getDouble("humidity") ?: 0.0
                    val moisture = data.getDouble("moisture") ?: 0.0
                    val rain = data.getLong("rain") ?: 0
                    val sound = data.getLong("sound") ?: 0
                    val timestamp = data.getString("timestamp") ?: "--"

                    textTemperature.text = "Temperature: $temp °C"
                    textHumidity.text = "Humidity: $humidity %"
                    textMoisture.text = "Moisture: $moisture"
                    textRain.text = "Rain: $rain"
                    textSound.text = "Sound: $sound"
                    textTimestamp.text = "Last updated: $timestamp"
                }
            }
    }
}
