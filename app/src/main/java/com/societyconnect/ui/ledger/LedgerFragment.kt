package com.societyconnect.ui.ledger

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
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.societyconnect.R
import com.societyconnect.data.models.LedgerEntry
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.BottomSheetAddLedgerEntryBinding
import com.societyconnect.databinding.FragmentLedgerBinding
import com.societyconnect.databinding.ItemLedgerBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getLedgerCategoryLabel
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toRupees
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

// ─── Fragment ─────────────────────────────────────────────────────────
class LedgerFragment : Fragment() {
    private var _binding: FragmentLedgerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LedgerViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: LedgerAdapter
    private var fullList: List<LedgerEntry> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLedgerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[LedgerViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canManage = session.isAdmin() || session.isTreasurer()
        adapter = LedgerAdapter(canManage = canManage, onDelete = { confirmDelete(it) })

        binding.rvLedger.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLedger.adapter = adapter

        viewModel.allEntries.observe(viewLifecycleOwner) { list ->
            fullList = list
            applyFilter()
        }
        viewModel.totalIncome.observe(viewLifecycleOwner) { binding.tvTotalIncome.text = (it ?: 0.0).toRupees() }
        viewModel.totalExpense.observe(viewLifecycleOwner) { binding.tvTotalExpense.text = (it ?: 0.0).toRupees() }
        viewModel.balance.observe(viewLifecycleOwner) { binding.tvBalance.text = (it ?: 0.0).toRupees() }

        binding.chipAll.setOnClickListener { applyFilter() }
        binding.chipIncome.setOnClickListener { applyFilter() }
        binding.chipExpense.setOnClickListener { applyFilter() }

        binding.btnViewReports.setOnClickListener {
            findNavController().navigate(R.id.reportsFragment)
        }

        binding.fabAdd.visibility = if (canManage) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun applyFilter() {
        val filtered = when {
            binding.chipIncome.isChecked -> fullList.filter { it.type == "INCOME" }
            binding.chipExpense.isChecked -> fullList.filter { it.type == "EXPENSE" }
            else -> fullList
        }
        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddLedgerEntryBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        var selectedType = "EXPENSE"
        var selectedCategory = "REPAIR"

        sheet.chipGroupType.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            selectedType = chip.tag.toString()
        }
        sheet.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            selectedCategory = chip.tag.toString()
        }

        sheet.btnSave.setOnClickListener {
            val description = sheet.etDescription.text.toString().trim()
            val amount = sheet.etAmount.text.toString().toDoubleOrNull()
            if (description.isEmpty() || amount == null || amount <= 0.0) {
                requireContext().toast("Please fill all fields")
                return@setOnClickListener
            }
            viewModel.addEntry(
                LedgerEntry(
                    type = selectedType,
                    category = selectedCategory,
                    description = description,
                    amount = amount,
                    addedBy = session.getName(),
                    addedByUid = session.getUserId()
                )
            )
            requireContext().toast("Entry added")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmDelete(e: LedgerEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Entry")
            .setMessage("Delete \"${e.description}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteEntry(e); requireContext().toast("Entry deleted") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class LedgerViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allEntries get() = repo.allLedgerEntries
    val totalIncome get() = repo.totalIncome
    val totalExpense get() = repo.totalExpense
    val balance get() = repo.ledgerBalance

    fun addEntry(e: LedgerEntry) = viewModelScope.launch { repo.addLedgerEntry(e) }
    fun deleteEntry(e: LedgerEntry) = viewModelScope.launch { repo.deleteLedgerEntry(e) }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class LedgerAdapter(
    private val canManage: Boolean,
    private val onDelete: (LedgerEntry) -> Unit
) : ListAdapter<LedgerEntry, LedgerAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemLedgerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLedgerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvCategory.text = getLedgerCategoryLabel(item.category)
            tvDescription.text = item.description
            tvAddedBy.text = "Added by ${item.addedBy}"
            tvDate.text = item.date.toDateString()

            val isIncome = item.type == "INCOME"
            tvAmount.text = if (isIncome) "+${item.amount.toRupees()}" else "-${item.amount.toRupees()}"
            tvAmount.setTextColor(root.context.getColor(
                if (isIncome) com.societyconnect.R.color.status_paid else com.societyconnect.R.color.status_pending
            ))
            dotType.setBackgroundResource(
                if (isIncome) com.societyconnect.R.drawable.dot_green else com.societyconnect.R.drawable.dot_red
            )

            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<LedgerEntry>() {
            override fun areItemsTheSame(a: LedgerEntry, b: LedgerEntry) = a.id == b.id
            override fun areContentsTheSame(a: LedgerEntry, b: LedgerEntry) = a == b
        }
    }
}
