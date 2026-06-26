package com.societyconnect.ui.society

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.data.firebase.SocietyProfile
import com.societyconnect.databinding.ActivitySubscriptionBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var authRepo: AuthRepository
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        authRepo = AuthRepository()

        if (!session.isAdmin()) {
            toast("Only the secretary can manage the subscription")
            finish()
            return
        }

        binding.tvSocietyName.text = session.getSociety()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseMonthly.setOnClickListener { confirmActivate("MONTHLY", 499.0) }
        binding.btnChooseYearly.setOnClickListener { confirmActivate("YEARLY", 4999.0) }
        binding.btnCancelSubscription.setOnClickListener { confirmCancel() }
        binding.btnSaveUpiId.setOnClickListener { saveUpiId() }

        authRepo.getSocietyLive(session.getSocietyId()).observe(this) { society ->
            society?.let { render(it) }
        }
    }

    private fun saveUpiId() {
        val upiId = binding.etUpiId.text.toString().trim()
        if (upiId.isEmpty()) {
            toast("Enter a UPI ID first")
            return
        }
        lifecycleScope.launch {
            try {
                authRepo.updateUpiId(session.getSocietyId(), upiId)
                runOnUiThread { toast("UPI ID saved") }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "Failed to save UPI ID") }
            }
        }
    }

    private fun render(society: SocietyProfile) {
        if (binding.etUpiId.text.toString() != (society.upiId ?: "")) {
            binding.etUpiId.setText(society.upiId ?: "")
        }
        if (society.subscriptionActive) {
            binding.tvStatusChip.text = "Subscription Active"
            val plan = if (society.subscriptionPlan == "YEARLY") "Yearly Plan" else "Monthly Plan"
            val started = society.subscriptionStartedAt?.toDate()?.time?.toDateString() ?: "-"
            val expires = society.subscriptionExpiresAt?.toDate()?.time?.toDateString() ?: "-"
            binding.tvPlanInfo.text = "$plan\nStarted: $started\nExpires: $expires"
            binding.btnCancelSubscription.visibility = android.view.View.VISIBLE
        } else {
            binding.tvStatusChip.text = "No Active Subscription"
            binding.tvPlanInfo.text =
                "No active subscription. Choose a plan below to unlock inviting members to your society."
            binding.btnCancelSubscription.visibility = android.view.View.GONE
        }
    }

    private fun confirmActivate(plan: String, price: Double) {
        val planLabel = if (plan == "YEARLY") "Yearly (₹${price.toInt()}/year)" else "Monthly (₹${price.toInt()}/month)"
        AlertDialog.Builder(this)
            .setTitle("Activate $planLabel?")
            .setMessage("This activates your society's subscription so you can invite members. Payments aren't wired up yet — this just flips the plan on for now.")
            .setPositiveButton("Activate") { _, _ -> activate(plan) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun activate(plan: String) {
        lifecycleScope.launch {
            try {
                authRepo.activateSubscription(plan)
                runOnUiThread { toast("Subscription activated!") }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "Failed to activate subscription") }
            }
        }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Subscription")
            .setMessage("Members already in your society stay, but you won't be able to invite new ones until you reactivate.")
            .setPositiveButton("Cancel Subscription") { _, _ ->
                lifecycleScope.launch {
                    try {
                        authRepo.cancelSubscription()
                        runOnUiThread { toast("Subscription cancelled") }
                    } catch (e: Exception) {
                        runOnUiThread { toast(e.message ?: "Failed to cancel subscription") }
                    }
                }
            }
            .setNegativeButton("Keep It", null)
            .show()
    }
}
