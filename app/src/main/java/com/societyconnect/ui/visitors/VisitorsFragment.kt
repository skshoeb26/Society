package com.societyconnect.ui.visitors

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.societyconnect.databinding.FragmentVisitorsBinding
import com.societyconnect.databinding.BottomSheetAddVisitorBinding
import com.societyconnect.databinding.ItemVisitorBinding
import com.societyconnect.data.models.Visitor
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toast
import com.societyconnect.utils.toTimeString
import com.societyconnect.utils.toDateTimeString
import kotlinx.coroutines.launch

// ─── Fragment ─────────────────────────────────────────────────────────
class VisitorsFragment : Fragment() {
    private var _binding: FragmentVisitorsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VisitorsViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: VisitorsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVisitorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[VisitorsViewModel::class.java]
        viewModel.init(session.getSocietyId())

        adapter = VisitorsAdapter(
            canCheckOut = session.isSecurity() || session.isAdmin(),
            onCheckOut = { viewModel.checkOut(it) }
        )

        binding.rvVisitors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVisitors.adapter = adapter

        val liveData = if (session.isResident()) viewModel.getByFlat(session.getFlatNo())
                       else viewModel.allVisitors

        liveData.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.todayCount.observe(viewLifecycleOwner) {
            binding.tvTodayCount.text = "Today: $it visitors"
        }

        // Only security/admin can log visitors
        binding.fabAdd.visibility =
            if (session.isSecurity() || session.isAdmin()) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddVisitorBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.btnLog.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            val flat = sheet.etFlat.text.toString().trim().uppercase()
            val purpose = sheet.etPurpose.text.toString().trim()
            val vehicle = sheet.etVehicle.text.toString().trim()

            if (name.isEmpty() || flat.isEmpty() || purpose.isEmpty()) {
                requireContext().toast("Please fill required fields")
                return@setOnClickListener
            }

            viewModel.addVisitor(
                Visitor(
                    visitorName = name,
                    visitingFlat = flat,
                    purpose = purpose,
                    vehicleNo = vehicle,
                    loggedBy = session.getName()
                )
            )
            requireContext().toast("Visitor logged for Flat $flat")
            dialog.dismiss()
        }

        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class VisitorsViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allVisitors get() = repo.allVisitors
    val todayCount get() = repo.todayVisitorCount
    fun getByFlat(flat: String) = repo.getVisitorsByFlat(flat)
    fun addVisitor(v: Visitor) = viewModelScope.launch { repo.addVisitor(v) }
    fun checkOut(v: Visitor) = viewModelScope.launch {
        repo.updateVisitor(v.copy(checkOut = System.currentTimeMillis()))
    }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class VisitorsAdapter(
    private val canCheckOut: Boolean,
    private val onCheckOut: (Visitor) -> Unit
) : ListAdapter<Visitor, VisitorsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemVisitorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVisitorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvVisitorName.text = item.visitorName
            tvFlat.text = "Flat ${item.visitingFlat}"
            tvPurpose.text = item.purpose
            tvCheckIn.text = "In: ${item.checkIn.toDateTimeString()}"

            if (item.vehicleNo.isNotEmpty()) {
                tvVehicle.visibility = View.VISIBLE
                tvVehicle.text = "🚗 ${item.vehicleNo}"
            } else {
                tvVehicle.visibility = View.GONE
            }

            if (item.checkOut != null) {
                tvCheckOut.visibility = View.VISIBLE
                tvCheckOut.text = "Out: ${item.checkOut.toDateTimeString()}"
                btnCheckOut.visibility = View.GONE
                tvStatusDot.setBackgroundResource(com.societyconnect.R.drawable.dot_grey)
            } else {
                tvCheckOut.visibility = View.GONE
                btnCheckOut.visibility = if (canCheckOut) View.VISIBLE else View.GONE
                tvStatusDot.setBackgroundResource(com.societyconnect.R.drawable.dot_green)
            }

            btnCheckOut.setOnClickListener { onCheckOut(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Visitor>() {
            override fun areItemsTheSame(a: Visitor, b: Visitor) = a.id == b.id
            override fun areContentsTheSame(a: Visitor, b: Visitor) = a == b
        }
    }
}
