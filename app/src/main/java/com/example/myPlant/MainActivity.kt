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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.navigation.ui.NavigationUI
import com.example.myPlant.databinding.ActivityMainBinding
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.example.myPlant.ui.home.HomeFragment
import com.example.myPlant.data.local.UserPreferences
import com.example.myPlant.data.repository.Graph

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    // ---------------------------
    // CAMERA + LOCATION PERMISSION HANDLING
    // ---------------------------

    private fun checkPermissionsAndLocationEnabled(): Boolean {
        val cameraPermission = android.Manifest.permission.CAMERA
        val locationPermission = android.Manifest.permission.ACCESS_FINE_LOCATION

        val hasCameraPermission = checkSelfPermission(cameraPermission) == PackageManager.PERMISSION_GRANTED
        val hasLocationPermission = checkSelfPermission(locationPermission) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission || !hasLocationPermission) {
            requestPermissions(arrayOf(cameraPermission, locationPermission), 1002)
            return false
        }

        val locationManager = getSystemService(android.location.LocationManager::class.java)
        val isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!isLocationEnabled) {
            Toast.makeText(this, "Please turn on location to continue", Toast.LENGTH_SHORT).show()
            // Optional: automatically open location settings
            // startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return false
        }

        return true
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

        if (requestCode == 1002) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera and location permissions are required.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------------------
    // MAIN ACTIVITY SETUP
    // ---------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved theme before inflating views so UI uses correct colors
        val userPrefs = UserPreferences(this)
        when (userPrefs.themeMode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

    // Force status bar icons to be light (white) and status bar background to black for the whole app
    WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = false

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
                    if (checkPermissionsAndLocationEnabled()) {
                        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                        val homeFragment = navHostFragment?.childFragmentManager?.fragments
                            ?.firstOrNull { it is HomeFragment } as? HomeFragment
                        homeFragment?.launchCamera()
                    } else {
                        Toast.makeText(this, "Please turn on location to continue", Toast.LENGTH_SHORT).show()
                    }
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
                R.id.nav_training_data_map  // ✅ Added plant location map
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Intercept the Theme menu item to show a selection dialog. Other items
        // are forwarded to NavigationUI so normal navigation still works.
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_theme -> {
                    // Close drawer and show theme selector
                    drawerLayout.closeDrawer(GravityCompat.START)
                    showThemeSelectionDialog(userPrefs)
                    true
                }
                else -> {
                    // Let NavigationUI handle navigation item selections
                    val handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
                    if (handled) drawerLayout.closeDrawer(GravityCompat.START)
                    handled
                }
            }
        }

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
    val role = userPrefs.userRole
    val isAdmin = role == "admin"
    navMenu.setGroupVisible(R.id.admin_group, isAdmin)
    }

    // ---------------------------
    // MENU HANDLING
    // ---------------------------

    private fun showThemeSelectionDialog(userPrefs: UserPreferences) {
        val options = arrayOf("Default", "Dark")
        val currentIndex = if (userPrefs.themeMode == "dark") 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Select Theme")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                when (which) {
                    0 -> {
                        userPrefs.themeMode = "default"
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                    1 -> {
                        userPrefs.themeMode = "dark"
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                }
                dialog.dismiss()
                // Recreate activity to ensure UI updates to the new theme
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
