package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.databinding.ActivityLoginBinding
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.ui.dashboard.MainActivity
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var repo: SocietyRepository
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SocietyRepository(this)
        session = SessionManager(this)

        binding.btnLogin.setOnClickListener { attemptLogin() }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (phone.isEmpty() || password.isEmpty()) {
            toast("Please fill all fields")
            return
        }

        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Logging in…"

        lifecycleScope.launch {
            val user = repo.loginUser(phone, password)
            runOnUiThread {
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Login"
                if (user != null) {
                    session.saveSession(
                        userId = user.id,
                        name = user.name,
                        flatNo = user.flatNo,
                        role = user.role,
                        society = user.societyName,
                        phone = user.phone
                    )
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    toast("Invalid phone or password")
                }
            }
        }
    }
}
