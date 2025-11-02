package com.example.myPlant.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.MainActivity
import com.example.myPlant.R
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.PhoneMultiFactorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.myPlant.LoginActivity

class MfaVerificationActivity : AppCompatActivity() {

    private lateinit var resolver: MultiFactorResolver
    private lateinit var verificationId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mfa_verification)

        // Get the resolver and verificationId passed from LoginActivity
        resolver = LoginActivity.pendingResolver ?: run {
            Toast.makeText(this, "No resolver found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        verificationId = intent.getStringExtra("verificationId") ?: ""
        if (verificationId.isEmpty()) {
            Toast.makeText(this, "Missing verification ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val phoneInfo = resolver.hints[0] as PhoneMultiFactorInfo
        val codeField = findViewById<EditText>(R.id.codeField)
        val verifyBtn = findViewById<Button>(R.id.verifyBtn)

        Toast.makeText(this, "Code sent to: ${phoneInfo.phoneNumber}", Toast.LENGTH_SHORT).show()

        verifyBtn.setOnClickListener {
            val smsCode = codeField.text.toString().trim()
            if (smsCode.isEmpty()) {
                Toast.makeText(this, "Please enter the code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Create a credential with the SMS code and verification ID
                    val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
                    val assertion = PhoneMultiFactorGenerator.getAssertion(credential)

                    // Complete the sign-in with MFA
                    resolver.resolveSignIn(assertion).await()

                    Toast.makeText(this@MfaVerificationActivity, "MFA success!", Toast.LENGTH_LONG).show()

                    // Navigate to MainActivity after success
                    val intent = Intent(this@MfaVerificationActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(this@MfaVerificationActivity, "MFA failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
