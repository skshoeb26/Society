package com.societyconnect.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.societyconnect.R
import com.societyconnect.databinding.ActivityMainBinding
import com.societyconnect.ui.auth.LoginActivity
import com.societyconnect.utils.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfig = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.maintenanceFragment,
                R.id.complaintsFragment,
                R.id.noticesFragment,
                R.id.visitorsFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNav.setupWithNavController(navController)

        // Hide visitors tab for non-security/admin
        if (session.isResident()) {
            binding.bottomNav.menu.findItem(R.id.visitorsFragment)?.isVisible = false
        }

        navController.addOnDestinationChangedListener { _, dest, _ ->
            supportActionBar?.title = when (dest.id) {
                R.id.dashboardFragment -> session.getSociety().ifEmpty { "Society Connect" }
                R.id.maintenanceFragment -> "Maintenance"
                R.id.complaintsFragment -> "Complaints"
                R.id.noticesFragment -> "Notice Board"
                R.id.visitorsFragment -> "Visitor Log"
                R.id.emergencyFragment -> "Emergency Contacts"
                else -> "Society Connect"
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_emergency -> {
                navController.navigate(R.id.emergencyFragment)
                true
            }
            R.id.action_logout -> {
                confirmLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                session.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
