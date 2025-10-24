package com.example.myPlant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R

data class IotReading(
    val deviceId: String,
    val temperature: Double,
    val humidity: Double,
    val moisture: Double,
    val rain: Long,
    val sound: Long,
    val timestamp: String
)

class HistoryAdapter(
    private val items: List<IotReading>
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDevice: TextView = view.findViewById(R.id.textDevice)
        val textTemp: TextView = view.findViewById(R.id.textTemp)
        val textHumidity: TextView = view.findViewById(R.id.textHumidity)
        val textTimestamp: TextView = view.findViewById(R.id.textTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = items[position]
        holder.textDevice.text = "Device: ${r.deviceId}"
        holder.textTemp.text = "Temp: ${r.temperature}°C"
        holder.textHumidity.text = "Humidity: ${r.humidity}%"
        holder.textTimestamp.text = r.timestamp
    }
}
