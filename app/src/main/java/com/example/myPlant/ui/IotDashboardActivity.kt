package com.example.myPlant.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R
import com.google.firebase.firestore.FirebaseFirestore

class IotDashboardActivity : AppCompatActivity() {

    private lateinit var tempText: TextView
    private lateinit var humidText: TextView
    private lateinit var soilText: TextView
    private lateinit var refreshButton: Button
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iot_dashboard)

        tempText = findViewById(R.id.tempText)
        humidText = findViewById(R.id.humidText)
        soilText = findViewById(R.id.soilText)
        refreshButton = findViewById(R.id.refreshButton)

        loadData()

        refreshButton.setOnClickListener {
            loadData()
        }
    }

    private fun loadData() {
        val docRef = db.collection("readings").document("104108")

        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val temp = document.getDouble("temperature") ?: 0.0
                    val humid = document.getDouble("humidity") ?: 0.0
                    val soil = document.getDouble("moisture") ?: 0.0

                    tempText.text = "Temperature: %.1f °C".format(temp)
                    humidText.text = "Humidity: %.1f %%".format(humid)
                    soilText.text = "Soil Moisture: %.1f".format(soil)
                } else {
                    tempText.text = "No data found"
                    humidText.text = ""
                    soilText.text = ""
                }
            }
            .addOnFailureListener {
                tempText.text = "Error loading data"
                humidText.text = ""
                soilText.text = ""
            }
    }
}
