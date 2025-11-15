package com.example.myPlant.ui.admin

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myPlant.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AiHelperFragment : Fragment() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var scrollView: ScrollView
    private lateinit var deviceChipGroup: ChipGroup
    private lateinit var timeRangeChipGroup: ChipGroup

    private val functions = FirebaseFunctions.getInstance("asia-southeast1")
    private var currentDeviceId: String? = null
    private var currentTimeRange: String = "latest"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_helper, container, false)

        chatContainer = view.findViewById(R.id.chatContainer)
        inputField = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        scrollView = view.findViewById(R.id.chatScroll)
        deviceChipGroup = view.findViewById(R.id.deviceChipGroup)
        timeRangeChipGroup = view.findViewById(R.id.timeRangeChipGroup)

        setupFilterChips()
        addWelcomeMessage()

        sendButton.setOnClickListener {
            val prompt = inputField.text.toString().trim()
            if (prompt.isNotEmpty()) {
                addMessage("You: $prompt")
                inputField.setText("")
                lifecycleScope.launch {
                    val reply = callAiAgent(prompt)
                    addMessage("AI: $reply")
                }
            }
        }

        return view
    }

    private fun setupFilterChips() {
        val devices = listOf("All Devices", "device001", "device002")

        devices.forEach { device ->
            val chip = Chip(requireContext()).apply {
                text = device
                isCheckable = true
                isChecked = device == "All Devices"
            }
            deviceChipGroup.addView(chip)
        }

        deviceChipGroup.setOnCheckedChangeListener { group, checkedId ->
            val chip = group.findViewById<Chip>(checkedId)
            if (chip != null) {
                val device = chip.text.toString()
                currentDeviceId = if (device == "All Devices") null else device
                addMessage("System: Now analyzing $device")
            }
        }

        val timeRanges = mapOf("Latest" to "latest", "Today" to "today", "This Week" to "week", "This Month" to "month")

        timeRanges.forEach { (key, value) ->
            val chip = Chip(requireContext()).apply {
                text = key
                isCheckable = true
                isChecked = value == "latest"
            }
            timeRangeChipGroup.addView(chip)
        }

        timeRangeChipGroup.setOnCheckedChangeListener { group, checkedId ->
            val chip = group.findViewById<Chip>(checkedId)
            if (chip != null) {
                val timeRange = chip.text.toString()
                currentTimeRange = timeRanges[timeRange] ?: "latest"
                addMessage("System: Time range set to $timeRange")
            }
        }
    }

    private fun addWelcomeMessage() {
        addMessage("AI: Hello! I'm your myPlant assistant. Select a device and time range above, or just ask me anything about your plants!")
    }

    private suspend fun callAiAgent(prompt: String): String {
        return try {
            // Use manual selection or auto-detect
            val (detectedDeviceId, detectedTimeRange) = detectContextFromPrompt(prompt)

            val finalDeviceId = currentDeviceId ?: detectedDeviceId
            val finalTimeRange = if (currentTimeRange == "latest") detectedTimeRange else currentTimeRange

            val data = mapOf(
                "prompt" to prompt,
                "deviceId" to finalDeviceId,
                "timeRange" to finalTimeRange
            )

            val result = functions
                .getHttpsCallable("queryAgent")
                .call(data)
                .await()

            val response = result.data as Map<*, *>
            response["reply"] as? String ?: "No response received."
        } catch (e: Exception) {
            "Error: ${e.message ?: "Unknown error occurred"}"
        }
    }

    private fun detectContextFromPrompt(prompt: String): Pair<String?, String> {
        val lowerPrompt = prompt.lowercase()

        val detectedDeviceId = when {
            lowerPrompt.contains("device001") || lowerPrompt.contains("device 001") -> "device001"
            lowerPrompt.contains("device002") || lowerPrompt.contains("device 002") -> "device002"
            lowerPrompt.contains("device1") || lowerPrompt.contains("device 1") -> "device001"
            lowerPrompt.contains("device2") || lowerPrompt.contains("device 2") -> "device002"
            else -> null
        }

        val detectedTimeRange = when {
            lowerPrompt.contains("today") -> "today"
            lowerPrompt.contains("this week") || lowerPrompt.contains("weekly") -> "week"
            lowerPrompt.contains("this month") || lowerPrompt.contains("monthly") -> "month"
            lowerPrompt.contains("history") || lowerPrompt.contains("historical") -> "week"
            else -> "latest"
        }

        return Pair(detectedDeviceId, detectedTimeRange)
    }

    private fun addMessage(text: String) {
        val msg = TextView(requireContext())
        msg.text = text
        msg.textSize = 16f

        // Convert dp to px
        fun dpToPx(dp: Int): Int {
            val density = resources.displayMetrics.density
            return (dp * density).toInt()
        }

        val horizontalPadding = dpToPx(16)
        val verticalPadding = dpToPx(8)
        msg.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        // Determine background and alignment
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        when {
            text.startsWith("AI:") -> {
                msg.setBackgroundResource(R.drawable.ai_message_bubble)
                msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                layoutParams.gravity = Gravity.START  // left
            }
            text.startsWith("System:") -> {
                msg.setBackgroundResource(R.drawable.system_message_bubble)
                msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                layoutParams.gravity = Gravity.START  // left
            }
            else -> {
                msg.setBackgroundResource(R.drawable.user_message_bubble)
                msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                layoutParams.gravity = Gravity.END    // right
            }
        }

        // Add margin between bubbles
        val margin = dpToPx(4)
        layoutParams.setMargins(margin, margin, margin, margin)
        msg.layoutParams = layoutParams

        // Add to container and scroll
        chatContainer.addView(msg)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

}
