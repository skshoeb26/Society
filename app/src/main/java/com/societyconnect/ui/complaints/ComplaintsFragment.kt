package com.societyconnect.ui.complaints

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.societyconnect.databinding.FragmentComplaintsBinding
import com.societyconnect.databinding.BottomSheetAddComplaintBinding
import com.societyconnect.databinding.BottomSheetUpdateStatusBinding
import com.societyconnect.data.models.Complaint
import com.societyconnect.data.models.withId
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toast

class ComplaintsFragment : Fragment() {

    private var _binding: FragmentComplaintsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ComplaintsViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: ComplaintsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComplaintsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[ComplaintsViewModel::class.java]
        viewModel.init(session.getSocietyId())

        adapter = ComplaintsAdapter(
            isAdmin = session.isAdmin(),
            onUpdateStatus = { showUpdateStatusSheet(it) },
            onDelete = { confirmDelete(it) }
        )

        binding.rvComplaints.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComplaints.adapter = adapter

        // Load data based on role
        val liveData = if (session.isAdmin()) viewModel.allComplaints
                       else viewModel.getByFlat(session.getFlatNo())

        liveData.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        // Chips filter
        binding.chipAll.setOnClickListener { observeAll() }
        binding.chipOpen.setOnClickListener {
            viewModel.getByStatus("OPEN").observe(viewLifecycleOwner) { adapter.submitList(it) }
        }
        binding.chipInProgress.setOnClickListener {
            viewModel.getByStatus("IN_PROGRESS").observe(viewLifecycleOwner) { adapter.submitList(it) }
        }
        binding.chipResolved.setOnClickListener {
            viewModel.getByStatus("RESOLVED").observe(viewLifecycleOwner) { adapter.submitList(it) }
        }

        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun observeAll() {
        val liveData = if (session.isAdmin()) viewModel.allComplaints
                       else viewModel.getByFlat(session.getFlatNo())
        liveData.observe(viewLifecycleOwner) { adapter.submitList(it) }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddComplaintBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        val categories = listOf("WATER", "LIFT", "ELECTRICITY", "CLEANING", "SECURITY", "PARKING", "OTHER")
        var selectedCategory = categories[0]

        sheet.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            selectedCategory = chip.tag.toString()
        }

        sheet.btnSubmit.setOnClickListener {
            val title = sheet.etTitle.text.toString().trim()
            val desc = sheet.etDesc.text.toString().trim()
            if (title.isEmpty() || desc.isEmpty()) {
                requireContext().toast("Please fill all fields")
                return@setOnClickListener
            }
            viewModel.addComplaint(
                Complaint(
                    title = title,
                    description = desc,
                    category = selectedCategory,
                    flatNo = session.getFlatNo(),
                    raisedBy = session.getName(),
                    raisedByUid = session.getUserId()
                )
            )
            requireContext().toast("Complaint submitted")
            dialog.dismiss()
        }

        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showUpdateStatusSheet(complaint: Complaint) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetUpdateStatusBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.tvComplaintTitle.text = complaint.title
        sheet.etAdminComment.setText(complaint.adminComment)

        sheet.btnOpen.setOnClickListener { updateStatus(complaint, "OPEN", sheet.etAdminComment.text.toString(), dialog) }
        sheet.btnInProgress.setOnClickListener { updateStatus(complaint, "IN_PROGRESS", sheet.etAdminComment.text.toString(), dialog) }
        sheet.btnResolved.setOnClickListener { updateStatus(complaint, "RESOLVED", sheet.etAdminComment.text.toString(), dialog) }

        dialog.show()
    }

    private fun updateStatus(complaint: Complaint, status: String, comment: String, dialog: BottomSheetDialog) {
        viewModel.updateComplaint(complaint.copy(status = status, adminComment = comment, updatedAt = System.currentTimeMillis()).withId(complaint.id))
        requireContext().toast("Status updated to $status")
        dialog.dismiss()
    }

    private fun confirmDelete(c: Complaint) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Complaint")
            .setMessage("Delete \"${c.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteComplaint(c); requireContext().toast("Complaint deleted") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
