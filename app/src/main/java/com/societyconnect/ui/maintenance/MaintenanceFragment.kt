package com.societyconnect.ui.maintenance

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.societyconnect.databinding.FragmentMaintenanceBinding
import com.societyconnect.databinding.BottomSheetAddMaintenanceBinding
import com.societyconnect.data.models.Maintenance
import com.societyconnect.utils.*
import java.util.*

class MaintenanceFragment : Fragment() {

    private var _binding: FragmentMaintenanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MaintenanceViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: MaintenanceAdapter
    private var selectedDueDate = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMaintenanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[MaintenanceViewModel::class.java]
        viewModel.init(requireContext())

        adapter = MaintenanceAdapter(
            isAdmin = session.isAdmin(),
            onTogglePaid = { viewModel.togglePaid(it) },
            onDelete = { confirmDelete(it) }
        )

        binding.rvMaintenance.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMaintenance.adapter = adapter

        // Observe data
        val liveData = if (session.isAdmin()) viewModel.allMaintenance
                       else viewModel.getByFlat(session.getFlatNo())

        liveData.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.totalCollected.observe(viewLifecycleOwner) {
            binding.tvCollected.text = (it ?: 0.0).toRupees()
        }
        viewModel.totalPending.observe(viewLifecycleOwner) {
            binding.tvPending.text = (it ?: 0.0).toRupees()
        }

        // Show summary only for admin
        binding.cardSummary.visibility = if (session.isAdmin()) View.VISIBLE else View.GONE
        binding.fabAdd.visibility = if (session.isAdmin()) View.VISIBLE else View.GONE

        binding.fabAdd.setOnClickListener { showAddChoice() }

        // Filter chips
        binding.chipAll.setOnClickListener { observeAll() }
        binding.chipPending.setOnClickListener {
            viewModel.pendingMaintenance.observe(viewLifecycleOwner) { list ->
                adapter.submitList(list)
            }
        }
    }

    private fun observeAll() {
        val liveData = if (session.isAdmin()) viewModel.allMaintenance
                       else viewModel.getByFlat(session.getFlatNo())
        liveData.observe(viewLifecycleOwner) { adapter.submitList(it) }
    }

    private fun showAddChoice() {
        AlertDialog.Builder(requireContext())
            .setTitle("Maintenance Add Karo")
            .setItems(
                arrayOf(
                    "📤 Sabko Bhejo (Bulk - sab flats)",
                    "➕ Ek Flat ka (Single)"
                )
            ) { _, which ->
                when (which) {
                    0 -> BulkMaintenanceSheet().show(childFragmentManager, "bulk")
                    1 -> showAddSheet()
                }
            }
            .show()
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetAddMaintenanceBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.etMonth.setText(getCurrentMonthYear())

        sheetBinding.btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                selectedDueDate = cal.timeInMillis
                sheetBinding.tvSelectedDate.text = selectedDueDate.toDateString()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        sheetBinding.btnSave.setOnClickListener {
            val flat = sheetBinding.etFlat.text.toString().trim().uppercase()
            val name = sheetBinding.etResidentName.text.toString().trim()
            val amount = sheetBinding.etAmount.text.toString().toDoubleOrNull()
            val month = sheetBinding.etMonth.text.toString().trim()

            if (flat.isEmpty() || name.isEmpty() || amount == null || month.isEmpty()) {
                requireContext().toast("Please fill all fields")
                return@setOnClickListener
            }

            viewModel.addMaintenance(
                Maintenance(
                    flatNo = flat,
                    residentName = name,
                    amount = amount,
                    month = month,
                    dueDate = selectedDueDate
                )
            )
            requireContext().toast("Due added for Flat $flat")
            dialog.dismiss()
        }

        sheetBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmDelete(m: Maintenance) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Due")
            .setMessage("Remove due for Flat ${m.flatNo}?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteMaintenance(m) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
