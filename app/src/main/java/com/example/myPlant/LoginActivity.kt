package com.example.myPlant

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myPlant.data.repository.AuthRepository
import com.example.myPlant.data.model.UserProfile
import com.example.myPlant.data.model.ContributionStats
import com.example.myPlant.data.model.UserPreferences
import com.example.myPlant.data.model.NotificationPreferences
import com.example.myPlant.data.model.PrivacyPreferences
import com.example.myPlant.ui.admin.AdminDashboardActivity
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

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
                val user = loginResult.getOrNull()
                user?.let {
                    // Fetch Firestore user profile to check role
                    val profileResult = authRepository.getUserProfile(it.uid)
                    if (profileResult.isSuccess) {
                        val profile = profileResult.getOrNull()
                        when (profile?.role) {
                            "admin" -> {
                                Toast.makeText(this@LoginActivity, "Welcome, admin!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                intent.putExtra("isAdmin", true)
                                startActivity(intent)
                            }
                            "public" -> {
                                Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                intent.putExtra("isAdmin", false)
                                startActivity(intent)
                            }
                            else -> {
                                Toast.makeText(this@LoginActivity, "Invalid user role.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Failed to load user profile", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(
                    this@LoginActivity,
                    "Login failed: ${loginResult.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT
                ).show()
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
}