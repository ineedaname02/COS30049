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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.material.color.MaterialColors

class IotDashboardActivity : AppCompatActivity() {

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var tvTemperature: MaterialTextView
    private lateinit var tvHumidity: MaterialTextView
    private lateinit var tvMoisture: MaterialTextView
    private lateinit var tvRain: MaterialTextView
    private lateinit var tvSound: MaterialTextView
    private lateinit var tvLight: MaterialTextView
    private lateinit var btnRefreshData: MaterialButton
    private lateinit var btnViewHistory: MaterialButton

    private lateinit var lineChartTemp: LineChart
    private lateinit var lineChartHumidity: LineChart
    private lateinit var lineChartMoisture: LineChart
    private lateinit var lineChartRain: LineChart
    private lateinit var lineChartSound: LineChart
    private lateinit var lineChartLight: LineChart

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iot_dashboard)

        topAppBar = findViewById(R.id.topAppBar)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvMoisture = findViewById(R.id.tvMoisture)
        tvRain = findViewById(R.id.tvRain)
        tvSound = findViewById(R.id.tvSound)
        tvLight = findViewById(R.id.tvLight)
        btnRefreshData = findViewById(R.id.btnRefreshData)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        lineChartTemp = findViewById(R.id.lineChartTemp)
        lineChartHumidity = findViewById(R.id.lineChartHumidity)
        lineChartMoisture = findViewById(R.id.lineChartMoisture)
        lineChartRain = findViewById(R.id.lineChartRain)
        lineChartSound = findViewById(R.id.lineChartSound)
        lineChartLight = findViewById(R.id.lineChartLight)

        topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupChart(lineChartTemp, "Temperature (°C)")
        setupChart(lineChartHumidity, "Humidity (%)")
        setupChart(lineChartMoisture, "Soil Moisture (%)")
        setupChart(lineChartRain, "Rain Detection")
        setupChart(lineChartSound, "Sound Levels")
        setupChart(lineChartLight, "Light Levels")

        updateAllCharts()

        btnRefreshData.setOnClickListener { updateAllCharts() }

        btnViewHistory.setOnClickListener {
            val intent = Intent(this, IotHistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupChart(chart: LineChart, label: String) {
        val themeTextColor = MaterialColors.getColor(chart, com.google.android.material.R.attr.colorOnSurface)
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = themeTextColor
            }
            axisLeft.textColor = themeTextColor
            axisRight.isEnabled = false
            legend.apply {
                isEnabled = true
                textColor = themeTextColor
            }
            animateX(1000)
        }
    }

    private fun updateAllCharts() {
        loadFirestoreData()
    }

    private fun loadFirestoreData() {
        db.collection("readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                val readings = documents.documents.reversed()

                val tempValues = mutableListOf<Float>()
                val humidityValues = mutableListOf<Float>()
                val moistureValues = mutableListOf<Float>()
                val rainValues = mutableListOf<Float>()
                val soundValues = mutableListOf<Float>()
                val lightValues = mutableListOf<Float>()

                readings.forEach { doc ->
                    tempValues.add(doc.getDouble("temperature")?.toFloat() ?: 0f)
                    humidityValues.add(doc.getDouble("humidity")?.toFloat() ?: 0f)
                    moistureValues.add(doc.getDouble("moisture")?.toFloat() ?: 0f)
                    rainValues.add(doc.getLong("rain")?.toFloat() ?: 0f)
                    soundValues.add(doc.getLong("sound")?.toFloat() ?: 0f)
                    lightValues.add(doc.getLong("lightDigital")?.toFloat() ?: 0f)
                }

                setChartData(lineChartTemp, tempValues, "Temperature (°C)", R.color.warning_orange)
                setChartData(lineChartHumidity, humidityValues, "Humidity (%)", R.color.accent_blue)
                setChartData(lineChartMoisture, moistureValues, "Soil Moisture (%)", R.color.primary_green)
                setChartData(lineChartRain, rainValues, "Rain Detection", R.color.accent_blue)
                setChartData(lineChartSound, soundValues, "Sound Levels", R.color.surface_dark)
                setChartData(lineChartLight, lightValues, "Light Levels", R.color.warning_orange)

                if (readings.isNotEmpty()) {
                    val latest = readings.last()
                    tvTemperature.text = "Temp: ${latest.getDouble("temperature")?.toInt() ?: 0}°C"
                    tvHumidity.text = "Humidity: ${latest.getDouble("humidity")?.toInt() ?: 0}%"
                    tvMoisture.text = "Moisture: ${latest.getDouble("moisture")?.toInt() ?: 0}%"
                    tvRain.text = "Rain: ${latest.getLong("rain") ?: 0}"
                    tvSound.text = "Sound: ${latest.getLong("sound") ?: 0}"
                    tvLight.text = "Light: ${getLightText(latest.getLong("lightDigital") ?: 0)}"
                }
            }
            .addOnFailureListener { exception ->
                setDemoData()
            }
    }

    private fun setDemoData() {
        // Demo data as fallback
        val tempValues = listOf(29f, 30f, 32f, 31f, 33f, 34f, 32f)
        val humidityValues = listOf(55f, 58f, 60f, 57f, 59f, 61f, 58f)
        val moistureValues = listOf(62f, 60f, 65f, 63f, 64f, 62f, 63f)
        val rainValues = listOf(0f, 0f, 1f, 1f, 0f, 0f, 0f)
        val soundValues = listOf(5f, 8f, 3f, 2f, 7f, 4f, 6f)
        val lightValues = listOf(1f, 1f, 2f, 1f, 0f, 1f, 2f)

        setChartData(lineChartTemp, tempValues, "Temperature (°C)", R.color.warning_orange)
        setChartData(lineChartHumidity, humidityValues, "Humidity (%)", R.color.accent_blue)
        setChartData(lineChartMoisture, moistureValues, "Soil Moisture (%)", R.color.primary_green)
        setChartData(lineChartRain, rainValues, "Rain Detection", R.color.accent_blue)
        setChartData(lineChartSound, soundValues, "Sound Levels", R.color.surface_dark)
        setChartData(lineChartLight, lightValues, "Light Levels", R.color.warning_orange)

        tvTemperature.text = "Temp: ${tempValues.last().toInt()}°C"
        tvHumidity.text = "Humidity: ${humidityValues.last().toInt()}%"
        tvMoisture.text = "Moisture: ${moistureValues.last().toInt()}%"
        tvRain.text = "Rain: ${rainValues.last().toInt()}"
        tvSound.text = "Sound: ${soundValues.last().toInt()}"
        tvLight.text = "Light: ${getLightText(lightValues.last().toLong())}"
    }

    private fun getLightText(lightValue: Long): String {
        return when (lightValue) {
            0L -> "Low"
            1L -> "Medium"
            2L -> "High"
            else -> "Unknown"
        }
    }

    private fun setChartData(chart: LineChart, values: List<Float>, label: String, colorRes: Int) {
        val themeTextColor = MaterialColors.getColor(chart, com.google.android.material.R.attr.colorOnSurface)
        val entries = values.mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val dataSet = LineDataSet(entries, label).apply {
            color = getColor(colorRes)
            valueTextColor = themeTextColor
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
