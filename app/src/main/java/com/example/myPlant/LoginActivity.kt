package com.example.myPlant

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.myPlant.data.repository.AuthRepository
import com.example.myPlant.data.model.UserProfile
import com.example.myPlant.data.model.ContributionStats
import com.example.myPlant.data.model.NotificationPreferences
import com.example.myPlant.data.model.PrivacyPreferences
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import com.example.myPlant.data.local.UserPreferences
import android.app.AlertDialog
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.PhoneMultiFactorInfo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await



class LoginActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved theme before inflating views (keep night mode behavior intact)
        val userPrefs = UserPreferences(this)
        when (userPrefs.themeMode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        when (userPrefs.themeMode) {
            "dark" -> {
                window.decorView.systemUiVisibility = 0 // Light status bar icons for dark theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR // Dark status bar icons for light theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        setContentView(R.layout.activity_auth)

        // Set up toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Login"

        // Initialize repository
        authRepository = AuthRepository()

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        progressBar = findViewById(R.id.progressBar)

        // Check if user is already logged in using repository
        if (authRepository.isUserLoggedIn) {
            navigateToMainActivity()
            return
        }

        loginButton.setOnClickListener {
            loginUser()
        }

        registerButton.setOnClickListener {
            registerUser()
        }
    }

    private fun loginUser() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            val loginResult = authRepository.loginUser(email, password)

            if (loginResult.isSuccess) {
                // normal login flow (already implemented)
            } else {
                val ex = loginResult.exceptionOrNull()
                if (ex is AuthRepository.MultiFactorAuthRequired) {
                    // Trigger SMS second factor
                    val resolver = ex.resolver
                    val phoneFactor = resolver.hints[0] as PhoneMultiFactorInfo

                    val options = PhoneAuthOptions.newBuilder()
                        .setMultiFactorHint(phoneFactor)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this@LoginActivity)
                        .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                lifecycleScope.launch {
                                    try {
                                        resolver.resolveSignIn(
                                            PhoneMultiFactorGenerator.getAssertion(credential)
                                        ).await()
                                        navigateToMainActivity()
                                    } catch (ex: Exception) {
                                        Toast.makeText(this@LoginActivity, "MFA failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            override fun onVerificationFailed(e: FirebaseException) {
                                Toast.makeText(this@LoginActivity, "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }

                            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                // show dialog for user to input SMS code
                                showMfaCodeDialog(verificationId, resolver)
                            }
                        })
                        .build()

                    PhoneAuthProvider.verifyPhoneNumber(options)
                } else {
                    Toast.makeText(this@LoginActivity, "Login failed: ${ex?.message}", Toast.LENGTH_SHORT).show()
                }
            }


            showLoading(false)
        }
    }



    private fun registerUser() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            // Create display name from email
            val displayName = email.substringBefore("@")

            val result = authRepository.registerUser(email, password, displayName)

            if (result.isSuccess) {
                Toast.makeText(this@LoginActivity, "Registered successfully!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            } else {
                Toast.makeText(this@LoginActivity, "Registration failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
            showLoading(false)
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        loginButton.isEnabled = !show
        registerButton.isEnabled = !show
    }

    private fun showMfaCodeDialog(verificationId: String, resolver: MultiFactorResolver) {
        val input = EditText(this)
        input.hint = "Enter 6-digit code"

        AlertDialog.Builder(this)
            .setTitle("Two-Factor Authentication")
            .setMessage("Enter the code sent to your phone")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val code = input.text.toString().trim()
                lifecycleScope.launch {
                    try {
                        val credential = PhoneAuthProvider.getCredential(verificationId, code)
                        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
                        resolver.resolveSignIn(assertion).await()
                        navigateToMainActivity()
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "MFA failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}