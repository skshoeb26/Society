package com.societyconnect.ui.society

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.billing.BillingManager
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
    private lateinit var billing: BillingManager
    private var expirySynced = false

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

        billing = BillingManager(this) { plan -> onPurchased(plan) }
        billing.start { updatePrices() }

        binding.tvSocietyName.text = session.getSociety()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseMonthly.setOnClickListener { launchPurchase("MONTHLY") }
        binding.btnChooseYearly.setOnClickListener { launchPurchase("YEARLY") }
        binding.btnCancelSubscription.setOnClickListener { confirmCancel() }
        binding.btnSaveUpiId.setOnClickListener { saveUpiId() }

        authRepo.getSocietyLive(session.getSocietyId()).observe(this) { society ->
            society?.let { render(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billing.endConnection()
    }

    private fun launchPurchase(plan: String) {
        if (!billing.isReady) {
            toast("Still loading plans — try again in a moment")
            return
        }
        billing.launchPurchase(plan)
    }

    private fun updatePrices() {
        val monthlyPrice = billing.priceFor("MONTHLY")
        val monthlyMicros = billing.priceMicrosFor("MONTHLY")
        val yearlyPrice = billing.priceFor("YEARLY")
        val yearlyMicros = billing.priceMicrosFor("YEARLY")

        if (monthlyPrice != null) binding.tvMonthlyPrice.text = "$monthlyPrice / month"
        if (yearlyPrice != null) {
            val savings = if (monthlyMicros != null && monthlyMicros > 0 && yearlyMicros != null) {
                val percent = ((1.0 - yearlyMicros.toDouble() / (monthlyMicros.toDouble() * 12)) * 100).toInt()
                if (percent > 0) " · Save ~$percent%" else ""
            } else ""
            binding.tvYearlyPrice.text = "$yearlyPrice / year$savings"
        }
    }

    private fun onPurchased(plan: String) {
        lifecycleScope.launch {
            try {
                authRepo.activateSubscription(plan)
                runOnUiThread { toast("Subscription activated!") }
            } catch (e: Exception) {
                runOnUiThread { toast(e.message ?: "Purchase succeeded but activation failed — contact support") }
            }
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

        val expiresAtMs = society.subscriptionExpiresAt?.toDate()?.time
        val expired = society.subscriptionActive && expiresAtMs != null && expiresAtMs < System.currentTimeMillis()
        if (expired && !expirySynced) {
            // No Play webhook wired up, so a lapsed/cancelled Play subscription is
            // only caught the next time the secretary opens this screen.
            expirySynced = true
            lifecycleScope.launch { runCatching { authRepo.cancelSubscription() } }
        }

        if (society.subscriptionActive && !expired) {
            binding.tvStatusChip.text = "Subscription Active"
            val plan = if (society.subscriptionPlan == "YEARLY") "Yearly Plan" else "Monthly Plan"
            val started = society.subscriptionStartedAt?.toDate()?.time?.toDateString() ?: "-"
            val expires = expiresAtMs?.toDateString() ?: "-"
            binding.tvPlanInfo.text = "$plan\nStarted: $started\nExpires: $expires"
            binding.btnCancelSubscription.visibility = android.view.View.VISIBLE
        } else {
            binding.tvStatusChip.text = "No Active Subscription"
            binding.tvPlanInfo.text =
                "No active subscription. Choose a plan below to unlock inviting members to your society."
            binding.btnCancelSubscription.visibility = android.view.View.GONE
        }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Subscription")
            .setMessage("To stop being charged, cancel the recurring subscription in Google Play. You'll keep access until the current billing period ends.")
            .setPositiveButton("Open Play Store") { _, _ -> billing.openManageSubscriptions() }
            .setNegativeButton("Close", null)
            .show()
    }
}
