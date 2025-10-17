package com.example.myPlant.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R
import com.example.myPlant.ui.IotDashboardActivity

class AdminDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_admin_dashboard)

        supportActionBar?.title = "Admin Dashboard"

        // Find the button and set click listener
        val iotButton = findViewById<Button>(R.id.IoTdashboard)
        iotButton.setOnClickListener {
            // Start the IoT Dashboard activity
            val intent = Intent(this, IotDashboardActivity::class.java)
            startActivity(intent)
        }
    }
}
