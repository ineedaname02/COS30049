package com.example.myPlant.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class IotDashboardActivity : AppCompatActivity() {

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var tvSensorReading: MaterialTextView
    private lateinit var btnRefreshData: MaterialButton
    private lateinit var btnViewHistory: MaterialButton

    private lateinit var lineChartTemp: LineChart
    private lateinit var lineChartHumidity: LineChart
    private lateinit var lineChartMoisture: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iot_dashboard)

        // Initialize views
        topAppBar = findViewById(R.id.topAppBar)
        tvSensorReading = findViewById(R.id.tvSensorReading)
        btnRefreshData = findViewById(R.id.btnRefreshData)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        lineChartTemp = findViewById(R.id.lineChartTemp)
        lineChartHumidity = findViewById(R.id.lineChartHumidity)
        lineChartMoisture = findViewById(R.id.lineChartMoisture)

        // Handle toolbar back navigation
        topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupChart(lineChartTemp, "Temperature (°C)")
        setupChart(lineChartHumidity, "Humidity (%)")
        setupChart(lineChartMoisture, "Soil Moisture (%)")

        updateAllCharts()

        btnRefreshData.setOnClickListener { updateAllCharts() }

        btnViewHistory.setOnClickListener {
            val intent = Intent(this, IotHistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupChart(chart: LineChart, label: String) {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisRight.isEnabled = false
            legend.isEnabled = true
            animateX(1000)
        }
    }

    private fun updateAllCharts() {
        val tempValues = listOf(29f, 30f, 32f, 31f)
        val humidityValues = listOf(55f, 58f, 60f, 57f)
        val moistureValues = listOf(62f, 60f, 65f, 63f)

        setChartData(lineChartTemp, tempValues, "Temperature (°C)", R.color.warning_orange)
        setChartData(lineChartHumidity, humidityValues, "Humidity (%)", R.color.accent_blue)
        setChartData(lineChartMoisture, moistureValues, "Soil Moisture (%)", R.color.primary_green)

        tvSensorReading.text = "Temp: ${tempValues.last().toInt()}°C | " +
                "Humidity: ${humidityValues.last().toInt()}% | " +
                "Moisture: ${moistureValues.last().toInt()}%"
    }

    private fun setChartData(chart: LineChart, values: List<Float>, label: String, colorRes: Int) {
        val entries = values.mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val dataSet = LineDataSet(entries, label).apply {
            color = getColor(colorRes)
            valueTextColor = getColor(R.color.black)
            lineWidth = 2f
            circleRadius = 4f
            setCircleColor(getColor(colorRes))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.data = LineData(dataSet)
        chart.invalidate()
    }
}
