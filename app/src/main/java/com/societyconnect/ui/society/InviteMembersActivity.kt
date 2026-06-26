package com.societyconnect.ui.society

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.data.firebase.SocietyProfile
import com.societyconnect.databinding.ActivityInviteMembersBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.generateInviteCode
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InviteMembersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInviteMembersBinding
    private lateinit var authRepo: AuthRepository
    private lateinit var session: SessionManager
    private var currentSociety: SocietyProfile? = null
    private var isApplyingFromData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInviteMembersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        authRepo = AuthRepository()

        if (!session.isAdmin()) {
            toast("Only the secretary can invite members")
            finish()
            return
        }

        binding.tvSocietyName.text = session.getSociety()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnGoToSubscription.setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        binding.btnShareCode.setOnClickListener { shareCode() }
        binding.btnRegenerateCode.setOnClickListener { confirmRegenerate() }

        binding.toggleInviteRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isApplyingFromData) return@addOnButtonCheckedListener
            val role = when (checkedId) {
                binding.btnRoleCommittee.id -> SessionManager.ROLE_COMMITTEE
                binding.btnRoleSecurity.id -> SessionManager.ROLE_SECURITY
                else -> SessionManager.ROLE_RESIDENT
            }
            applyInviteRole(role)
        }

        authRepo.getSocietyLive(session.getSocietyId()).observe(this) { society ->
            society?.let { render(it) }
        }
    }

    private fun render(society: SocietyProfile) {
        currentSociety = society
        if (!society.subscriptionActive) {
            binding.cardLocked.visibility = android.view.View.VISIBLE
            binding.groupUnlocked.visibility = android.view.View.GONE
            return
        }
        binding.cardLocked.visibility = android.view.View.GONE
        binding.groupUnlocked.visibility = android.view.View.VISIBLE
        binding.tvInviteCode.text = society.inviteCode

        isApplyingFromData = true
        val roleButtonId = when (society.inviteRole) {
            SessionManager.ROLE_COMMITTEE -> binding.btnRoleCommittee.id
            SessionManager.ROLE_SECURITY -> binding.btnRoleSecurity.id
            else -> binding.btnRoleResident.id
        }
        binding.toggleInviteRole.check(roleButtonId)
        isApplyingFromData = false
    }

    private fun applyInviteRole(role: String) {
        val society = currentSociety ?: return
        if (society.inviteRole == role) return
        lifecycleScope.launch {
            authRepo.societiesCollection().document(society.id).update("inviteRole", role).await()
        }
    }

    private fun shareCode() {
        val society = currentSociety ?: return
        val message = "Join ${society.name} on Society Connect! " +
            "Download the app, tap \"Join with Code\" and enter invite code: ${society.inviteCode}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, "Share invite code"))
    }

    private fun confirmRegenerate() {
        AlertDialog.Builder(this)
            .setTitle("Generate New Code?")
            .setMessage("The old invite code will stop working immediately. Anyone you haven't shared the new code with won't be able to join.")
            .setPositiveButton("Generate") { _, _ -> regenerate() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun regenerate() {
        val society = currentSociety ?: return
        lifecycleScope.launch {
            authRepo.societiesCollection().document(society.id)
                .update("inviteCode", generateInviteCode()).await()
            runOnUiThread { toast("New invite code generated") }
        }
    }
}
