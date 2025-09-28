    package com.example.myPlant

    import android.content.Intent
    import android.os.Bundle
    import android.widget.*
    import androidx.appcompat.app.AppCompatActivity
    import com.example.myPlant.ui.home.HomeFragment


    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.firestore.FirebaseFirestore


    class LoginActivity : AppCompatActivity() {

        private lateinit var auth: FirebaseAuth
        private lateinit var db: FirebaseFirestore

        private lateinit var emailEditText: EditText
        private lateinit var passwordEditText: EditText
        private lateinit var loginButton: Button
        private lateinit var registerButton: Button

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_auth)

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            emailEditText = findViewById(R.id.emailEditText)
            passwordEditText = findViewById(R.id.passwordEditText)
            loginButton = findViewById(R.id.loginButton)
            registerButton = findViewById(R.id.registerButton)

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

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { document ->
                                val role = document.getString("role") ?: "user"
                                goToRoleScreen(role)
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        private fun registerUser() {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        val user = hashMapOf(
                            "email" to email,
                            "role" to "user" // Default role
                        )
                        db.collection("users").document(uid).set(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registered successfully", Toast.LENGTH_SHORT).show()
                                goToRoleScreen("user")
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Registration failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        private fun goToRoleScreen(role: String) {
            when (role) {
                "admin" -> startActivity(Intent(this, HomeFragment::class.java))
                "expert" -> startActivity(Intent(this, HomeFragment::class.java))
                "user" -> startActivity(Intent(this, HomeFragment::class.java))
                else -> Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
