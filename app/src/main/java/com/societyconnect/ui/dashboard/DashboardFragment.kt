package com.societyconnect.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.societyconnect.R
import com.societyconnect.databinding.FragmentDashboardBinding
import com.societyconnect.ui.society.InviteMembersActivity
import com.societyconnect.ui.society.MembersActivity
import com.societyconnect.ui.society.SubscriptionActivity
import com.societyconnect.utils.RecurringMaintenanceHelper
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getGreeting
import com.societyconnect.utils.toRupees
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private lateinit var viewModel: DashboardViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        viewModel.init(requireContext())

        // Greeting
        binding.tvGreeting.text = "${getGreeting()},"
        binding.tvName.text = session.getName()
        binding.tvSociety.text = session.getSociety()
        binding.tvRole.text = when (session.getRole()) {
            SessionManager.ROLE_ADMIN -> "Admin · ${session.getFlatNo()}"
            SessionManager.ROLE_SECURITY -> "Security Guard"
            else -> "Flat ${session.getFlatNo()}"
        }

        // Live stats
        viewModel.pendingCount.observe(viewLifecycleOwner) {
            binding.tvPendingCount.text = "$it Pending"
        }
        viewModel.openComplaintsCount.observe(viewLifecycleOwner) {
            binding.tvComplaintsCount.text = "$it Open"
        }
        viewModel.todayVisitors.observe(viewLifecycleOwner) {
            binding.tvVisitorsCount.text = "$it Today"
        }
        viewModel.totalPending.observe(viewLifecycleOwner) {
            binding.tvPendingAmount.text = (it ?: 0.0).toRupees()
        }

        // Card clicks
        binding.cardMaintenance.setOnClickListener {
            findNavController().navigate(R.id.maintenanceFragment)
        }
        binding.cardComplaints.setOnClickListener {
            findNavController().navigate(R.id.complaintsFragment)
        }
        binding.cardNotices.setOnClickListener {
            findNavController().navigate(R.id.noticesFragment)
        }
        binding.cardVisitors.setOnClickListener {
            findNavController().navigate(R.id.visitorsFragment)
        }
        binding.cardEmergency.setOnClickListener {
            findNavController().navigate(R.id.emergencyFragment)
        }

        // Hide admin-only stats from residents
        if (session.isResident()) {
            binding.tvPendingAmount.visibility = View.GONE
            binding.tvPendingLabel.visibility = View.GONE
        }

        // Secretary-only: quick access to subscription, invites, member directory
        if (session.isAdmin()) {
            binding.cardManageSociety.visibility = View.VISIBLE
            binding.btnGoSubscription.setOnClickListener {
                startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
            }
            binding.btnGoInvite.setOnClickListener {
                startActivity(Intent(requireContext(), InviteMembersActivity::class.java))
            }
            binding.btnGoMembers.setOnClickListener {
                startActivity(Intent(requireContext(), MembersActivity::class.java))
            }
        }

        // Auto recurring maintenance — sirf admin/secretary ke liye
        // Naya mahina aaya toh apne aap sab flats ka maintenance ban jaata hai
        if (session.isAdmin()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = RecurringMaintenanceHelper.checkAndGenerate(requireContext(), session.getSocietyId())
                result?.let { requireContext().toast(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
