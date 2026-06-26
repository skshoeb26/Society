package com.societyconnect.ui.society

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.societyconnect.data.models.Society
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.ActivitySubscriptionBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var repo: SocietyRepository
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        repo = SocietyRepository(this)

        if (!session.isAdmin()) {
            toast("Only the secretary can manage the subscription")
            finish()
            return
        }

        binding.tvSocietyName.text = session.getSociety()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseMonthly.setOnClickListener { confirmActivate("MONTHLY", 499.0, 30) }
        binding.btnChooseYearly.setOnClickListener { confirmActivate("YEARLY", 4999.0, 365) }
        binding.btnCancelSubscription.setOnClickListener { confirmCancel() }

        repo.getSocietyByNameLive(session.getSociety()).observe(this) { society ->
            society?.let { render(it) }
        }
    }

    private fun render(society: Society) {
        if (society.subscriptionActive) {
            binding.tvStatusChip.text = "Subscription Active"
            val plan = if (society.subscriptionPlan == "YEARLY") "Yearly Plan" else "Monthly Plan"
            val started = society.subscriptionStartedAt?.toDateString() ?: "-"
            val expires = society.subscriptionExpiresAt?.toDateString() ?: "-"
            binding.tvPlanInfo.text = "$plan\nStarted: $started\nExpires: $expires"
            binding.btnCancelSubscription.visibility = android.view.View.VISIBLE
        } else {
            binding.tvStatusChip.text = "No Active Subscription"
            binding.tvPlanInfo.text =
                "No active subscription. Choose a plan below to unlock inviting members to your society."
            binding.btnCancelSubscription.visibility = android.view.View.GONE
        }
    }

    private fun confirmActivate(plan: String, price: Double, days: Int) {
        val planLabel = if (plan == "YEARLY") "Yearly (₹${price.toInt()}/year)" else "Monthly (₹${price.toInt()}/month)"
        AlertDialog.Builder(this)
            .setTitle("Activate $planLabel?")
            .setMessage("This activates your society's subscription so you can invite members. Payments aren't wired up yet — this just flips the plan on for now.")
            .setPositiveButton("Activate") { _, _ -> activate(plan, days) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun activate(plan: String, days: Int) {
        lifecycleScope.launch {
            val society = repo.getSocietyByName(session.getSociety()) ?: return@launch
            val now = System.currentTimeMillis()
            repo.updateSociety(
                society.copy(
                    subscriptionActive = true,
                    subscriptionPlan = plan,
                    subscriptionStartedAt = now,
                    subscriptionExpiresAt = now + TimeUnit.DAYS.toMillis(days.toLong())
                )
            )
            runOnUiThread { toast("Subscription activated!") }
        }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Subscription")
            .setMessage("Members already in your society stay, but you won't be able to invite new ones until you reactivate.")
            .setPositiveButton("Cancel Subscription") { _, _ ->
                lifecycleScope.launch {
                    val society = repo.getSocietyByName(session.getSociety()) ?: return@launch
                    repo.updateSociety(society.copy(subscriptionActive = false))
                    runOnUiThread { toast("Subscription cancelled") }
                }
            }
            .setNegativeButton("Keep It", null)
            .show()
    }
}
