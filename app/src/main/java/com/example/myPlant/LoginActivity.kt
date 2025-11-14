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
import com.google.firebase.auth.FirebaseAuth


class LoginActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    companion object {
        var pendingResolver: MultiFactorResolver? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved theme before inflating views (keep night mode behavior intact)
        val userPrefs = UserPreferences(this)
        when (userPrefs.themeMode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
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
            val user = FirebaseAuth.getInstance().currentUser

            user?.reload()?.addOnCompleteListener { task ->
                val reloadedUser = FirebaseAuth.getInstance().currentUser
                if (reloadedUser != null && reloadedUser.multiFactor.enrolledFactors.isEmpty()) {
                    // MFA not set — force them to finish setup
                    Toast.makeText(this, "Please complete MFA setup before accessing the app.", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, com.example.myPlant.ui.auth.EnableMfaActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    navigateToMainActivity()
                }
            }
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
                val user = FirebaseAuth.getInstance().currentUser

                if (user != null && user.multiFactor.enrolledFactors.isEmpty()) {
                    // MFA not enabled — block access and force setup
                    Toast.makeText(
                        this@LoginActivity,
                        "You must enable MFA before logging in.",
                        Toast.LENGTH_LONG
                    ).show()

                    val intent = Intent(
                        this@LoginActivity,
                        com.example.myPlant.ui.auth.EnableMfaActivity::class.java
                    )
                    startActivity(intent)
                    finish()
                } else if (user != null) {
                    // MFA enabled — fetch role and save in UserPreferences
                    try {
                        val profileResult = authRepository.getUserProfile(user.uid)
                        profileResult.onSuccess { profile ->
                            val userPrefs = UserPreferences(this@LoginActivity)
                            userPrefs.userRole = profile.role // Save role for this session
                            navigateToMainActivity()
                        }.onFailure { e ->
                            Toast.makeText(
                                this@LoginActivity,
                                "Failed to get user role: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            showLoading(false)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Error fetching user role: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        showLoading(false)
                    }
                }

            } else {
                val ex = loginResult.exceptionOrNull()
                if (ex is AuthRepository.MultiFactorAuthRequired) {
                    // Handle existing MFA challenge (SMS verification)
                    handleMultiFactorChallenge(ex.resolver)
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: ${ex?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                Toast.makeText(this@LoginActivity, "Registered successfully! Please set up MFA.", Toast.LENGTH_SHORT).show()

                // Launch the MFA enrollment screen
                val intent = Intent(this@LoginActivity, com.example.myPlant.ui.auth.EnableMfaActivity::class.java)
                startActivity(intent)
                finish()
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

    private fun handleMultiFactorChallenge(resolver: MultiFactorResolver) {
        // Get the enrolled phone factor
        val phoneFactor = resolver.hints
            .filterIsInstance<PhoneMultiFactorInfo>()
            .firstOrNull()

        if (phoneFactor == null) {
            Toast.makeText(this, "No phone factor found for MFA.", Toast.LENGTH_SHORT).show()
            return
        }

        val options = PhoneAuthOptions.newBuilder()
            .setMultiFactorSession(resolver.session)
            .setActivity(this)
            .setMultiFactorHint(phoneFactor)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    lifecycleScope.launch {
                        try {
                            val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
                            resolver.resolveSignIn(assertion).await()
                            navigateToMainActivity()
                        } catch (e: Exception) {
                            Toast.makeText(this@LoginActivity, "MFA failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@LoginActivity, "MFA verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    showMfaCodeDialog(verificationId, resolver)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }


}