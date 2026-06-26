package com.societyconnect.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.databinding.ActivityRegisterBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var authRepo: AuthRepository
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepo = AuthRepository()
        session = SessionManager(this)

        val account = authRepo.currentUser
        if (account == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        binding.tvAccountEmail.text = account.email ?: account.displayName ?: ""

        binding.actvFlatType.let { dropdown ->
            val flatTypes = listOf("1BHK", "2BHK", "3BHK", "SHOP")
            dropdown.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, flatTypes)
            )
            dropdown.setText("1BHK", false)
        }

        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val isCreateMode = checkedId == binding.btnModeCreate.id
            binding.layoutSociety.visibility = if (isCreateMode) View.VISIBLE else View.GONE
            binding.layoutInviteCode.visibility = if (isCreateMode) View.GONE else View.VISIBLE
        }

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvSignOut.setOnClickListener {
            authRepo.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun attemptRegister() {
        val isCreateMode = binding.toggleMode.checkedButtonId == binding.btnModeCreate.id

        val flat = binding.etFlat.text.toString().trim().uppercase()
        val societyInput = binding.etSociety.text.toString().trim()
        val inviteCodeInput = binding.etInviteCode.text.toString().trim().uppercase()
        val flatType = binding.actvFlatType.text.toString().ifEmpty { "1BHK" }

        if (flat.isEmpty()) {
            toast("Enter your flat number")
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
            try {
                if (isCreateMode) {
                    val result = authRepo.createSociety(societyInput, flat, flatType)
                    finishOnboarding(result["societyId"] as String, societyInput, SessionManager.ROLE_ADMIN, flat)
                } else {
                    val result = authRepo.redeemInviteCode(inviteCodeInput, flat, flatType)
                    finishOnboarding(
                        result["societyId"] as String,
                        result["societyName"] as String,
                        result["role"] as String,
                        flat
                    )
                }
            } catch (e: Exception) {
                binding.btnRegister.isEnabled = true
                toast(describeError(e))
            }
        }
    }

    private suspend fun finishOnboarding(societyId: String, societyName: String, role: String, flat: String) {
        val account = authRepo.currentUser
        session.saveSession(
            userId = account?.uid ?: "",
            name = account?.displayName ?: "",
            flatNo = flat,
            role = role,
            societyId = societyId,
            societyName = societyName,
            phone = ""
        )
        runCatching { authRepo.registerFcmToken() }
        toast(
            if (role == SessionManager.ROLE_ADMIN) "Society created! Find your invite code under Invite Members."
            else "Welcome to $societyName!"
        )
        startActivity(Intent(this, com.societyconnect.ui.dashboard.MainActivity::class.java))
        finishAffinity()
    }

    private fun describeError(e: Exception): String {
        if (e is FirebaseFunctionsException) {
            return when (e.code) {
                FirebaseFunctionsException.Code.NOT_FOUND -> "Invalid invite code. Check with your secretary."
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> "You're already part of a society."
                else -> e.message ?: "Something went wrong. Try again."
            }
        }
        return e.message ?: "Something went wrong. Try again."
    }
}
