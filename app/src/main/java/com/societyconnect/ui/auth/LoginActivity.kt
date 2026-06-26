package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.societyconnect.R
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.databinding.ActivityLoginBinding
import com.societyconnect.ui.dashboard.MainActivity
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepo: AuthRepository
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) completeSignIn(idToken) else onSignInFailed("Google sign-in failed")
        } catch (e: ApiException) {
            onSignInFailed("Google sign-in cancelled or failed (${e.statusCode})")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepo = AuthRepository()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogleSignIn.setOnClickListener {
            binding.btnGoogleSignIn.isEnabled = false
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun completeSignIn(idToken: String) {
        lifecycleScope.launch {
            try {
                authRepo.signInWithGoogle(idToken)
                val profile = authRepo.getMyProfile()
                val intent = if (profile == null) {
                    Intent(this@LoginActivity, RegisterActivity::class.java)
                } else {
                    Intent(this@LoginActivity, MainActivity::class.java)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                onSignInFailed(e.message ?: "Sign-in failed")
            }
        }
    }

    private fun onSignInFailed(message: String) {
        binding.btnGoogleSignIn.isEnabled = true
        toast(message)
    }
}
