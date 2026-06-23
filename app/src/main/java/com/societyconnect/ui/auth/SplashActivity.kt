package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.societyconnect.R
import com.societyconnect.ui.dashboard.MainActivity
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.createNotificationChannels

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        createNotificationChannels()

        Handler(Looper.getMainLooper()).postDelayed({
            val session = SessionManager(this)
            val intent = if (session.isLoggedIn()) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(intent)
            finish()
        }, 2000)
    }
}
