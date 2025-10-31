package com.example.myPlant.ui.slideshow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R

data class IotMetric(val name: String, val value: String, val unit: String)

class IotSlideAdapter(private val metrics: List<IotMetric>) :
    RecyclerView.Adapter<IotSlideAdapter.IotViewHolder>() {

    inner class IotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvMetricName)
        val value: TextView = view.findViewById(R.id.tvMetricValue)
        val unit: TextView = view.findViewById(R.id.tvMetricUnit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_iot_slide, parent, false)
        return IotViewHolder(view)
    }

    override fun onBindViewHolder(holder: IotViewHolder, position: Int) {
        val metric = metrics[position]
        holder.name.text = metric.name
        holder.value.text = metric.value
        holder.unit.text = metric.unit
    }

    override fun getItemCount() = metrics.size
}