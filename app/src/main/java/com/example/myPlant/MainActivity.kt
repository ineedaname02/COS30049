package com.example.myPlant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myPlant.data.local.UserPreferences
import com.example.myPlant.databinding.ActivityMainBinding
import com.example.myPlant.ui.home.HomeFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    // Location & Camera
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

    companion object {
        private const val CAMERA_REQUEST_CODE = 1001
        private const val LOCATION_REQUEST_CODE = 1002
    }

    // ---------------------------
    // CAMERA + LOCATION HANDLING
    // ---------------------------
    private fun checkPermissionsAndLaunchCamera() {
        val cameraPermission = Manifest.permission.CAMERA
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION

        when {
            checkSelfPermission(cameraPermission) != PackageManager.PERMISSION_GRANTED -> {
                requestPermissions(arrayOf(cameraPermission), CAMERA_REQUEST_CODE)
            }
            checkSelfPermission(locationPermission) != PackageManager.PERMISSION_GRANTED -> {
                requestPermissions(arrayOf(locationPermission), LOCATION_REQUEST_CODE)
            }
            else -> {
                checkLocationEnabledAndLaunchCamera()
            }
        }
    }

    private fun checkLocationEnabledAndLaunchCamera() {
        val locationManager = getSystemService(android.location.LocationManager::class.java)
        val isLocationEnabled =
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!isLocationEnabled) {
            Toast.makeText(this, "Enable location before using the camera.", Toast.LENGTH_LONG).show()
        } else {
            getCurrentLocationAndLaunchCamera()
        }
    }

    private fun getCurrentLocationAndLaunchCamera() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lastKnownLocation = location
                openCamera()
            } else {
                Toast.makeText(this, "Unable to get location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val squareImage = cropToSquare(it)

                // You can now pass this cropped image and location metadata to your HomeFragment or ViewModel
                val lat = lastKnownLocation?.latitude
                val lon = lastKnownLocation?.longitude
                Toast.makeText(
                    this,
                    "Photo captured (1:1) at location: $lat, $lon",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val dimension = min(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - dimension) / 2
        val yOffset = (bitmap.height - dimension) / 2
        return Bitmap.createBitmap(bitmap, xOffset, yOffset, dimension, dimension)
    }

    // ---------------------------
    // PERMISSIONS RESULT
    // ---------------------------
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            return
        }

        when (requestCode) {
            CAMERA_REQUEST_CODE -> checkPermissionsAndLaunchCamera()
            LOCATION_REQUEST_CODE -> checkPermissionsAndLaunchCamera()
        }
    }

    // ---------------------------
    // MAIN ACTIVITY SETUP
    // ---------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setSupportActionBar(binding.appBarMain.toolbar)

        // Default FAB message
        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Identify a plant from the Home screen.", Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab)
                .show()
        }

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // FAB behavior by screen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_home) {
                binding.appBarMain.fab.show()
                binding.appBarMain.fab.setOnClickListener {
                    checkPermissionsAndLaunchCamera()
                }
            } else {
                binding.appBarMain.fab.hide()
            }

            // Logout
            if (destination.id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }

        // Navigation setup
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_gallery,
                R.id.nav_slideshow,
                R.id.nav_history,
                R.id.nav_admin_dashboard,
                R.id.nav_plant_location_map
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Sidebar user info
        val headerView = navView.getHeaderView(0)
        val userEmailTextView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_user_email)
        val userNameTextView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_user_name)

        currentUser?.email?.let { email -> userEmailTextView?.text = email }
        currentUser?.displayName?.let { displayName ->
            userNameTextView?.text = "Welcome, $displayName"
        } ?: run {
            userNameTextView?.text = "Welcome Back"
        }

        // Admin-only menu
        val navMenu = navView.menu
        val userPrefs = UserPreferences(this)
        val isAdmin = userPrefs.userRole == "admin"
        navMenu.setGroupVisible(R.id.admin_group, isAdmin)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
