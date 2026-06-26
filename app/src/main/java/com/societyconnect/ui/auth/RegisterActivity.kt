package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.databinding.ActivityRegisterBinding
import com.societyconnect.data.models.Society
import com.societyconnect.data.models.User
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.ui.dashboard.MainActivity
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.generateInviteCode
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

        // Flat type dropdown (bulk maintenance ke liye)
        binding.actvFlatType?.let { dropdown ->
            val flatTypes = listOf("1BHK", "2BHK", "3BHK", "SHOP")
            dropdown.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, flatTypes)
            )
            dropdown.setText("1BHK", false)
        }

        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val isCreateMode = checkedId == binding.btnModeCreate.id
            binding.layoutSociety.visibility = if (isCreateMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.layoutInviteCode.visibility = if (isCreateMode) android.view.View.GONE else android.view.View.VISIBLE
        }

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val isCreateMode = binding.toggleMode.checkedButtonId == binding.btnModeCreate.id

        val name = binding.etName.text.toString().trim()
        val flat = binding.etFlat.text.toString().trim().uppercase()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val societyInput = binding.etSociety.text.toString().trim()
        val inviteCodeInput = binding.etInviteCode.text.toString().trim().uppercase()
        val flatType = binding.actvFlatType?.text?.toString()?.ifEmpty { "1BHK" } ?: "1BHK"

        if (name.isEmpty() || flat.isEmpty() || phone.isEmpty() || password.isEmpty()) {
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
        if (isCreateMode && societyInput.isEmpty()) {
            toast("Enter a name for your society")
            return
        }
        if (!isCreateMode && inviteCodeInput.isEmpty()) {
            toast("Enter the invite code from your secretary")
            return
        }

        binding.btnRegister.isEnabled = false

        lifecycleScope.launch {
            if (isCreateMode) {
                registerAsSecretary(name, flat, phone, password, societyInput, flatType)
            } else {
                registerWithInviteCode(name, flat, phone, password, inviteCodeInput, flatType)
            }
        }
    }

    private suspend fun registerAsSecretary(
        name: String, flat: String, phone: String, password: String,
        societyName: String, flatType: String
    ) {
        if (repo.getSocietyByName(societyName) != null) {
            runOnUiThread {
                binding.btnRegister.isEnabled = true
                toast("That society name is already taken. Ask your secretary for an invite code instead.")
            }
            return
        }

        val user = User(
            name = name,
            flatNo = flat,
            phone = phone,
            password = password,
            role = SessionManager.ROLE_ADMIN,
            societyName = societyName,
            flatType = flatType
        )
        val id = repo.registerUser(user)

        if (id > 0) {
            repo.createSociety(
                Society(
                    name = societyName,
                    secretaryUserId = id.toInt(),
                    inviteCode = generateInviteCode()
                )
            )
            runOnUiThread {
                session.saveSession(id.toInt(), name, flat, SessionManager.ROLE_ADMIN, societyName, phone)
                toast("Society created! Find your invite code under Invite Members.")
                startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                finishAffinity()
            }
        } else {
            runOnUiThread {
                binding.btnRegister.isEnabled = true
                toast("Registration failed. Try again.")
            }
        }
    }

    private suspend fun registerWithInviteCode(
        name: String, flat: String, phone: String, password: String,
        inviteCode: String, flatType: String
    ) {
        val society = repo.getSocietyByInviteCode(inviteCode)
        if (society == null) {
            runOnUiThread {
                binding.btnRegister.isEnabled = true
                toast("Invalid invite code. Check with your secretary.")
            }
            return
        }

        val user = User(
            name = name,
            flatNo = flat,
            phone = phone,
            password = password,
            role = society.inviteRole,
            societyName = society.name,
            flatType = flatType
        )
        val id = repo.registerUser(user)

        if (id > 0) {
            runOnUiThread {
                session.saveSession(id.toInt(), name, flat, society.inviteRole, society.name, phone)
                toast("Welcome to ${society.name}!")
                startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                finishAffinity()
            }
        } else {
            runOnUiThread {
                binding.btnRegister.isEnabled = true
                toast("Registration failed. Try again.")
            }
        }
    }
}
