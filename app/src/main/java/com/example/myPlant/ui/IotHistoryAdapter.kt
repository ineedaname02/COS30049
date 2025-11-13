package com.example.myPlant.ui.iot

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.databinding.ItemIotHistoryCardBinding

data class IotReading(
    val deviceId: String = "",
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val moisture: Double = 0.0,
    val rain: Long = 0,
    val sound: Long = 0,
    val timestamp: String = ""
)

class IotHistoryAdapter(private val items: List<IotReading>) :
    RecyclerView.Adapter<IotHistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(val binding: ItemIotHistoryCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemIotHistoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvDeviceId.text = "Device: ${item.deviceId}"
            tvTemperature.text = "Temperature: ${item.temperature}°C"
            tvHumidity.text = " Humidity: ${item.humidity}%"
            tvMoisture.text = "Moisture: ${item.moisture}%"
            tvRain.text = " Rain: ${item.rain}"
            tvSound.text = " Sound: ${item.sound}"
            tvTimestamp.text = item.timestamp
        }
    }

    override fun getItemCount() = items.size
}
