package com.example.myPlant.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myPlant.R
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.text.isNotEmpty
import kotlin.text.trim
import kotlin.to

class AiHelperFragment : Fragment() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var scrollView: ScrollView

    // Initialize Firebase Functions with your region if needed
    // If you need a specific region, use:
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_helper, container, false)
        chatContainer = view.findViewById(R.id.chatContainer)
        inputField = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        scrollView = view.findViewById(R.id.chatScroll)

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

    private suspend fun callAiAgent(prompt: String): String {
        return try {
            val result = functions
                .getHttpsCallable("queryAgent")
                .call(mapOf("prompt" to prompt))
                .await()

            val data = result.data as Map<*, *>
            data["reply"] as? String ?: "No response."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun addMessage(text: String) {
        val msg = TextView(requireContext())
        msg.text = text
        msg.textSize = 16f
        msg.setPadding(12)
        chatContainer.addView(msg)

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}