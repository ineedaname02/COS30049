package com.example.myPlant.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.R
import com.example.myPlant.data.repository.AuthRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EnableMfaActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()
    private lateinit var verificationId: String
    private var storedResendToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enable_mfa)
        supportActionBar?.hide()


        val phoneField = findViewById<EditText>(R.id.phoneField)
        val codeField = findViewById<EditText>(R.id.codeField)
        val sendBtn = findViewById<Button>(R.id.sendCodeBtn)
        val resendBtn = findViewById<Button>(R.id.resendCodeBtn)
        val verifyBtn = findViewById<Button>(R.id.verifyBtn)

        // Start MFA enrollment (send SMS)
        sendBtn.setOnClickListener {
            val phone = phoneField.text.toString().trim()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authRepository.enrollSecondFactor(
                phoneNumber = phone,
                activity = this,
                callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                        Toast.makeText(this@EnableMfaActivity, "Verification auto-completed", Toast.LENGTH_SHORT).show()
                    }

                    override fun onVerificationFailed(e: FirebaseException) {  // ✅ FIXED TYPE
                        Toast.makeText(this@EnableMfaActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                    override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                        verificationId = vid
                        storedResendToken = token

                        Toast.makeText(this@EnableMfaActivity, "Code sent to $phone", Toast.LENGTH_SHORT).show()

                        codeField.visibility = EditText.VISIBLE
                        verifyBtn.visibility = Button.VISIBLE
                        resendBtn.visibility = Button.VISIBLE
                    }
                }
            )
        }

        // Resend code logic
        resendBtn.setOnClickListener {
            val phone = phoneField.text.toString().trim()
            if (phone.isEmpty() || storedResendToken == null) {
                Toast.makeText(this, "Cannot resend yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authRepository.enrollSecondFactor(
                phoneNumber = phone,
                activity = this,
                callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                        // Optional auto-complete logic
                    }

                    override fun onVerificationFailed(e: FirebaseException) {  // ✅ FIXED TYPE
                        Toast.makeText(this@EnableMfaActivity, "Resend failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                    override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                        verificationId = vid
                        storedResendToken = token
                        Toast.makeText(this@EnableMfaActivity, "Code resent to $phone", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Final verification
        verifyBtn.setOnClickListener {
            val smsCode = codeField.text.toString()
            if (smsCode.isEmpty()) return@setOnClickListener

            CoroutineScope(Dispatchers.Main).launch {
                val result = authRepository.finalizeEnrollment(verificationId, smsCode)
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@EnableMfaActivity, "MFA enabled successfully!", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@EnableMfaActivity, com.example.myPlant.MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    },
                    onFailure = {
                        Toast.makeText(this@EnableMfaActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}
