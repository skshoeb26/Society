package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.databinding.ActivityRegisterBinding
import com.societyconnect.data.models.User
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.ui.dashboard.MainActivity
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var repo: SocietyRepository
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SocietyRepository(this)
        session = SessionManager(this)

        // Role dropdown
        val roles = listOf("Resident", "Admin / Secretary", "Security Guard")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        binding.actvRole.setAdapter(adapter)
        binding.actvRole.setText("Resident", false)

        // Flat type dropdown (bulk maintenance ke liye)
        binding.actvFlatType?.let { dropdown ->
            val flatTypes = listOf("1BHK", "2BHK", "3BHK", "SHOP")
            dropdown.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, flatTypes)
            )
            dropdown.setText("1BHK", false)
        }

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val name = binding.etName.text.toString().trim()
        val flat = binding.etFlat.text.toString().trim().uppercase()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val society = binding.etSociety.text.toString().trim()
        val roleText = binding.actvRole.text.toString()

        if (name.isEmpty() || flat.isEmpty() || phone.isEmpty() || password.isEmpty() || society.isEmpty()) {
            toast("Please fill all fields")
            return
        }
        if (phone.length != 10) {
            toast("Enter a valid 10-digit phone number")
            return
        }
        if (password.length < 4) {
            toast("Password must be at least 4 characters")
            return
        }

        val role = when (roleText) {
            "Admin / Secretary" -> SessionManager.ROLE_ADMIN
            "Security Guard" -> SessionManager.ROLE_SECURITY
            else -> SessionManager.ROLE_RESIDENT
        }

        val user = User(
            name = name,
            flatNo = flat,
            phone = phone,
            password = password,
            role = role,
            societyName = society,
            flatType = binding.actvFlatType?.text?.toString()?.ifEmpty { "1BHK" } ?: "1BHK"
        )

        binding.btnRegister.isEnabled = false

        lifecycleScope.launch {
            val id = repo.registerUser(user)
            runOnUiThread {
                if (id > 0) {
                    session.saveSession(id.toInt(), name, flat, role, society, phone)
                    toast("Welcome to Society Connect!")
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finishAffinity()
                } else {
                    binding.btnRegister.isEnabled = true
                    toast("Registration failed. Try again.")
                }
            }
        }
    }
}
