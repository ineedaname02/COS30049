package com.example.myPlant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R

data class IotReading(
    val deviceId: String = "",
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val moisture: Double = 0.0,
    val rain: Long = 0,
    val sound: Long = 0,
    val timestamp: String = ""
)

class HistoryAdapter(
    private val historyList: List<IotReading>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeviceId: TextView = itemView.findViewById(R.id.tvDeviceId)
        val tvTemperature: TextView = itemView.findViewById(R.id.tvTemperature)
        val tvHumidity: TextView = itemView.findViewById(R.id.tvHumidity)
        val tvMoisture: TextView = itemView.findViewById(R.id.tvMoisture)
        val tvRain: TextView = itemView.findViewById(R.id.tvRain)
        val tvSound: TextView = itemView.findViewById(R.id.tvSound)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_card, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]

        holder.tvDeviceId.text = "Device: ${item.deviceId}"
        holder.tvTemperature.text = "Temp: ${item.temperature}°C"
        holder.tvHumidity.text = "Humidity: ${item.humidity}%"
        holder.tvMoisture.text = "Moisture: ${item.moisture}%"
        holder.tvRain.text = "Rain: ${item.rain}"
        holder.tvSound.text = "Sound: ${item.sound}"
        holder.tvTimestamp.text = item.timestamp
    }

    override fun getItemCount(): Int = historyList.size
}
