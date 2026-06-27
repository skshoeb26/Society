package com.societyconnect.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.societyconnect.data.models.LedgerEntry
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.FragmentReportsBinding
import com.societyconnect.databinding.ItemReportCategoryBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getLedgerCategoryLabel
import com.societyconnect.utils.toRupees
import java.util.Calendar

// ─── Fragment ─────────────────────────────────────────────────────────
class ReportsFragment : Fragment() {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ReportsViewModel
    private lateinit var session: SessionManager
    private var fullList: List<LedgerEntry> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[ReportsViewModel::class.java]
        viewModel.init(session.getSocietyId())

        viewModel.allEntries.observe(viewLifecycleOwner) { list ->
            fullList = list
            applyPeriod()
        }

        binding.chipThisMonth.setOnClickListener { applyPeriod() }
        binding.chipLast3Months.setOnClickListener { applyPeriod() }
        binding.chipThisYear.setOnClickListener { applyPeriod() }
        binding.chipAllTime.setOnClickListener { applyPeriod() }
    }

    private fun applyPeriod() {
        val startMillis = when {
            binding.chipLast3Months.isChecked -> Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
            binding.chipThisYear.isChecked -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            binding.chipAllTime.isChecked -> 0L
            else -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        render(fullList.filter { it.date >= startMillis })
    }

    private fun render(entries: List<LedgerEntry>) {
        val income = entries.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expense = entries.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        binding.tvIncome.text = income.toRupees()
        binding.tvExpense.text = expense.toRupees()
        binding.tvNet.text = (income - expense).toRupees()
        binding.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        binding.categoryContainer.removeAllViews()
        if (expense <= 0.0) return

        entries.filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .forEach { (category, amount) ->
                val percent = ((amount / expense) * 100).toInt()
                val row = ItemReportCategoryBinding.inflate(layoutInflater, binding.categoryContainer, false)
                row.tvCategory.text = getLedgerCategoryLabel(category)
                row.tvAmount.text = amount.toRupees()
                row.tvPercent.text = "$percent%"
                val params = row.barFill.layoutParams as LinearLayout.LayoutParams
                params.weight = percent.toFloat()
                row.barFill.layoutParams = params
                binding.categoryContainer.addView(row.root)
            }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class ReportsViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allEntries get() = repo.allLedgerEntries
}
