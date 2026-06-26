package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.R
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.ui.dashboard.MainActivity
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.createNotificationChannels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        createNotificationChannels()

        lifecycleScope.launch {
            delay(1200)
            val authRepo = AuthRepository()
            val firebaseUser = authRepo.currentUser
            val intent = if (firebaseUser == null) {
                Intent(this@SplashActivity, LoginActivity::class.java)
            } else {
                val profile = authRepo.getMyProfile()
                if (profile == null) {
                    Intent(this@SplashActivity, RegisterActivity::class.java)
                } else {
                    val society = authRepo.getSociety(profile.societyId)
                    SessionManager(this@SplashActivity).saveSession(
                        userId = profile.uid,
                        name = profile.name,
                        flatNo = profile.flatNo,
                        role = profile.role,
                        societyId = profile.societyId,
                        societyName = society?.name ?: "",
                        phone = profile.phone ?: ""
                    )
                    runCatching { authRepo.registerFcmToken() }
                    Intent(this@SplashActivity, MainActivity::class.java)
                }
            }
            startActivity(intent)
            finish()
        }
    }
}
