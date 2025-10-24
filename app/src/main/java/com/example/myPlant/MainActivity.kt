package com.example.myPlant

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.myPlant.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.example.myPlant.ui.home.HomeFragment
import com.example.myPlant.data.local.UserPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    // ---------------------------
    // CAMERA PERMISSION HANDLING
    // ---------------------------
    private fun checkCameraPermissionAndLaunch() {
        val cameraPermission = android.Manifest.permission.CAMERA

        if (checkSelfPermission(cameraPermission) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissions(arrayOf(cameraPermission), 1001)
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivity(cameraIntent)
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------
    // MAIN ACTIVITY SETUP
    // ---------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User not logged in → go to Login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setSupportActionBar(binding.appBarMain.toolbar)

        // Default FAB message when not overridden by HomeFragment
        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Identify a plant from the Home screen.", Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab)
                .setAction("Action", null).show()
        }

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // ---------------------------
        // FAB Visibility by Destination
        // ---------------------------
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_home) {
                binding.appBarMain.fab.show()
                binding.appBarMain.fab.setOnClickListener {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                    val homeFragment = navHostFragment?.childFragmentManager?.fragments
                        ?.firstOrNull { it is HomeFragment } as? HomeFragment
                    homeFragment?.launchCamera()
                }
            } else {
                binding.appBarMain.fab.hide()
            }
            // ✅ HANDLE LOGOUT HERE
            if (destination.id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }

        // ---------------------------
        // NAVIGATION SETUP
        // ---------------------------
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_gallery,
                R.id.nav_slideshow,
                R.id.nav_history,
                R.id.nav_admin_dashboard,
                R.id.nav_plant_location_map  // ✅ Added plant location map
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // ---------------------------
        // USER EMAIL DISPLAY IN SIDEBAR
        // ---------------------------
        val headerView = navView.getHeaderView(0)
        val userEmailTextView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_user_email)
        val userNameTextView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_user_name)
        
        // Set user email from Firebase Auth
        currentUser?.email?.let { email ->
            userEmailTextView?.text = email
        }
        
        // Set user name (you can customize this based on your user data)
        currentUser?.displayName?.let { displayName ->
            userNameTextView?.text = "Welcome, $displayName"
        } ?: run {
            userNameTextView?.text = "Welcome Back"
        }

        // ---------------------------
        // ADMIN MENU VISIBILITY
        // ---------------------------
        val navMenu = navView.menu
        val userPrefs = UserPreferences(this)
        val role = userPrefs.userRole
        val isAdmin = role == "admin"
        navMenu.setGroupVisible(R.id.admin_group, isAdmin)
    }

    // ---------------------------
    // MENU HANDLING
    // ---------------------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
