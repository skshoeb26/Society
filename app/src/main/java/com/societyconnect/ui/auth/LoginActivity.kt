package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
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
            if (idToken != null) signInWithGoogleToken(idToken) else onAuthFailed("Google sign-in failed")
        } catch (e: ApiException) {
            onAuthFailed("Google sign-in cancelled or failed (${e.statusCode})")
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

        binding.toggleAuthMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val isSignIn = checkedId == binding.btnModeSignIn.id
            binding.btnEmailAction.text = getString(if (isSignIn) R.string.sign_in else R.string.create_account)
            binding.tvForgotPassword.visibility = if (isSignIn) View.VISIBLE else View.GONE
        }

        binding.btnEmailAction.setOnClickListener { attemptEmailAuth() }
        binding.tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }
    }

    private fun attemptEmailAuth() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val isSignIn = binding.toggleAuthMode.checkedButtonId == binding.btnModeSignIn.id

        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter your email and password")
            return
        }
        if (!isSignIn && password.length < 6) {
            toast("Password must be at least 6 characters")
            return
        }

        binding.btnEmailAction.isEnabled = false
        lifecycleScope.launch {
            try {
                if (isSignIn) authRepo.signInWithEmail(email, password) else authRepo.registerWithEmail(email, password)
                proceedAfterAuth()
            } catch (e: Exception) {
                onAuthFailed(describeAuthError(e))
            }
        }
    }

    private fun signInWithGoogleToken(idToken: String) {
        lifecycleScope.launch {
            try {
                authRepo.signInWithGoogle(idToken)
                proceedAfterAuth()
            } catch (e: Exception) {
                onAuthFailed(e.message ?: "Sign-in failed")
            }
        }
    }

    private suspend fun proceedAfterAuth() {
        val profile = authRepo.getMyProfile()
        val intent = if (profile == null) {
            Intent(this@LoginActivity, RegisterActivity::class.java)
        } else {
            Intent(this@LoginActivity, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.email)
            setText(binding.etEmail.text.toString().trim())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_password)
            .setMessage(R.string.reset_password_message)
            .setView(input)
            .setPositiveButton(R.string.send_reset_link) { _, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty()) {
                    toast("Enter your email")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        authRepo.sendPasswordResetEmail(email)
                        toast(getString(R.string.reset_link_sent))
                    } catch (e: Exception) {
                        toast(describeAuthError(e))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onAuthFailed(message: String) {
        binding.btnGoogleSignIn.isEnabled = true
        binding.btnEmailAction.isEnabled = true
        toast(message)
    }

    private fun describeAuthError(e: Exception): String = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
        is FirebaseAuthInvalidUserException -> "No account found with that email"
        is FirebaseAuthUserCollisionException -> "An account already exists with that email"
        is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters"
        else -> e.message ?: "Something went wrong. Try again."
    }
}
