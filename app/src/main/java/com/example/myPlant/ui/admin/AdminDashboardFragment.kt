package com.example.myPlant.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.myPlant.R
import com.example.myPlant.ui.IotDashboardActivity

class AdminDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_admin_dashboard, container, false)

        val iotButton = view.findViewById<Button>(R.id.IoTdashboard)
        iotButton.setOnClickListener {
            val intent = Intent(requireContext(), IotDashboardActivity::class.java)
            startActivity(intent)
        }

        return view
    }
}
