package com.example.myPlant.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R
import com.example.myPlant.ui.admin.AdminDashboardActivity
import com.example.myPlant.ui.IotDashboardActivity

class AdminDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        val viewIotButton = findViewById<Button>(R.id.viewIotButton)
        viewIotButton.setOnClickListener {
            val intent = Intent(this, IotDashboardActivity::class.java)
            startActivity(intent)
        }
    }
}
