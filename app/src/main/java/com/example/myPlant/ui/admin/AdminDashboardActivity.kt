package com.example.myPlant.ui.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R

class AdminDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iot_dashboard)

        // Example: You can load admin stats or management options here
        supportActionBar?.title = "Admin Dashboard"
    }
}