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
    val lightDigital: Long = 0,  // Add this field
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

            val timestampParts = item.timestamp.split("T")
            val date = timestampParts.getOrNull(0) ?: "--"
            val time = timestampParts.getOrNull(1) ?: "--"

            tvDate.text = date
            tvTime.text = time

            tvTemperature.text = "${item.temperature}°C"
            tvHumidity.text = "${item.humidity}%"

            val lightText = when (item.lightDigital) {
                0L -> "Low"
                1L -> "Medium"
                2L -> "High"
                else -> "Unknown"
            }
            lightDigital.text = lightText

            tvMoisture.text = "${item.moisture}%"
            tvRain.text = item.rain.toString()
            tvSound.text = item.sound.toString()
        }
    }

    override fun getItemCount() = items.size
}