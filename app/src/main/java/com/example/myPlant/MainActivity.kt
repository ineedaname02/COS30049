package com.example.myPlant

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.Button
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
import com.example.myPlant.ui.IotDashboardActivity
import com.example.myPlant.data.local.UserPreferences


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User not logged in, send to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_admin_dashboard
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // 👇 Safely show admin group if user is admin
        val navMenu = navView.menu
        val userPrefs = UserPreferences(this)
        val role = userPrefs.userRole
        val isAdmin = role == "admin" // true if admin
        navMenu.setGroupVisible(R.id.admin_group, isAdmin)

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    val userPrefs = UserPreferences(this)
                    userPrefs.clear() // clear saved role
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> {
                    // Let NavigationUI handle all other items including admin dashboard
                    val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(
                        menuItem, navController
                    )
                    if (handled) drawerLayout.closeDrawers()
                    handled
                }
            }
        }
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
