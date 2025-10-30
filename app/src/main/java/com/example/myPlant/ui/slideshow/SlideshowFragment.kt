package com.example.myPlant.ui.slideshow

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.myPlant.databinding.FragmentSlideshowBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SlideshowFragment : Fragment() {

    private var _binding: FragmentSlideshowBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: IotSlideAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var currentPage = 0
    private var autoScrollRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)
        db = FirebaseFirestore.getInstance()
        loadLatestReading()
        return binding.root
    }

    private fun loadLatestReading() {
        db.collection("readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val metrics = listOf(
                        IotMetric("Temperature", "${doc.getDouble("temperature") ?: 0.0}", "°C"),
                        IotMetric("Humidity", "${doc.getDouble("humidity") ?: 0.0}", "%"),
                        IotMetric("Soil Moisture", "${doc.getDouble("moisture") ?: 0.0}", ""),
                        IotMetric("Rain Level", "${doc.getLong("rain") ?: 0}", ""),
                        IotMetric("Sound", "${doc.getLong("sound") ?: 0}", "")
                    )

                    binding.tvUpdatedTime.text = "Last updated: ${doc.getString("timestamp") ?: "--"}"

                    adapter = IotSlideAdapter(metrics)
                    binding.viewPager.adapter = adapter

                    startAutoSlide()
                } else {
                    binding.tvUpdatedTime.text = "No IoT data available"
                }
            }
            .addOnFailureListener {
                binding.tvUpdatedTime.text = "Error loading data"
            }
    }

    private fun startAutoSlide() {
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (::adapter.isInitialized && adapter.itemCount > 0) {
                    currentPage = (currentPage + 1) % adapter.itemCount
                    binding.viewPager.setCurrentItem(currentPage, true)
                    handler.postDelayed(this, 4000)
                }
            }
        }
        handler.postDelayed(autoScrollRunnable!!, 4000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoScrollRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}