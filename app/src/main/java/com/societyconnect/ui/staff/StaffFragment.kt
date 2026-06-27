package com.societyconnect.ui.staff

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.societyconnect.data.models.StaffMember
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.BottomSheetAddStaffBinding
import com.societyconnect.databinding.FragmentStaffBinding
import com.societyconnect.databinding.ItemStaffBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getStaffIcon
import com.societyconnect.utils.makeCall
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Fragment ─────────────────────────────────────────────────────────
class StaffFragment : Fragment() {
    private var _binding: FragmentStaffBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: StaffViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: StaffAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStaffBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[StaffViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canManage = session.canManageContent()

        adapter = StaffAdapter(
            canManage = canManage,
            onDelete = { confirmDelete(it) }
        )

        binding.rvStaff.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStaff.adapter = adapter

        viewModel.allStaff.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.visibility = if (canManage) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddStaffBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        var selectedRole = "WATCHMAN"
        var joiningDate: Long? = null

        sheet.chipGroupRole.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            selectedRole = chip.tag.toString()
        }

        sheet.btnPickJoining.setOnClickListener {
            pickDate { joiningDate = it; sheet.tvJoiningDate.text = it.toDateString() }
        }

        sheet.btnSave.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            val phone = sheet.etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                requireContext().toast("Please enter name and phone")
                return@setOnClickListener
            }
            viewModel.addStaff(
                StaffMember(
                    name = name,
                    role = selectedRole,
                    phone = phone,
                    shiftTiming = sheet.etShiftTiming.text.toString().trim(),
                    joiningDate = joiningDate,
                    address = sheet.etAddress.text.toString().trim(),
                    addedBy = session.getName()
                )
            )
            requireContext().toast("Staff added")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun pickDate(onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            cal.set(y, m, d)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun confirmDelete(s: StaffMember) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Staff")
            .setMessage("Remove \"${s.name}\" from the staff directory?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteStaff(s); requireContext().toast("Staff removed") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class StaffViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allStaff get() = repo.allStaff
    fun addStaff(s: StaffMember) = viewModelScope.launch { repo.addStaff(s) }
    fun deleteStaff(s: StaffMember) = viewModelScope.launch { repo.deleteStaff(s) }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class StaffAdapter(
    private val canManage: Boolean,
    private val onDelete: (StaffMember) -> Unit
) : ListAdapter<StaffMember, StaffAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemStaffBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStaffBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvIcon.text = getStaffIcon(item.role)
            tvName.text = item.name
            tvPhone.text = item.phone
            tvRole.text = item.role.replace("_", " ")
            tvShift.text = item.shiftTiming
            tvShift.visibility = if (item.shiftTiming.isBlank()) View.GONE else View.VISIBLE
            btnCall.setOnClickListener { root.context.makeCall(item.phone) }
            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<StaffMember>() {
            override fun areItemsTheSame(a: StaffMember, b: StaffMember) = a.id == b.id
            override fun areContentsTheSame(a: StaffMember, b: StaffMember) = a == b
        }
    }
}
