package com.societyconnect.ui.maintenance

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.societyconnect.databinding.BottomSheetBulkMaintenanceBinding
import com.societyconnect.data.models.RecurringConfig
import com.societyconnect.utils.toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * BulkMaintenanceSheet
 *
 * Secretary isse:
 *  1. Month + due date set karta hai
 *  2. "Sabka Same" ya "Size ke hisaab se" amount choose karta hai
 *  3. (Optional) "Har mahine auto" toggle on karta hai
 *  4. "Sabko Bhejo" → ek click mein sabhi flats ka maintenance ban jaata hai
 *
 * Duplicate protection: agar is mahine ka pehle se hai toh skip ho jaata hai.
 */
class BulkMaintenanceSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBulkMaintenanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MaintenanceViewModel

    private var selectedDueDate: Long = 0L
    private var mode = "SAME"   // ya "BY_SIZE"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBulkMaintenanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Apna khud ka ViewModel (fragment-scoped) — repo wahi DB use karta hai
        viewModel = ViewModelProvider(this)[MaintenanceViewModel::class.java]
        viewModel.init(requireContext())

        // Default month = current month
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        binding.etMonth.setText(monthFmt.format(Calendar.getInstance().time))

        // Default mode = SAME selected
        binding.toggleMode.check(binding.btnModeSame.id)

        // Mode toggle listener
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.btnModeSame.id -> {
                    mode = "SAME"
                    binding.tilSameAmount.visibility = View.VISIBLE
                    binding.layoutSizeAmounts.visibility = View.GONE
                }
                binding.btnModeSize.id -> {
                    mode = "BY_SIZE"
                    binding.tilSameAmount.visibility = View.GONE
                    binding.layoutSizeAmounts.visibility = View.VISIBLE
                }
            }
        }

        // Date picker
        binding.btnPickDate.setOnClickListener { showDatePicker() }

        // Pre-load existing recurring config (agar pehle set kiya tha)
        viewModel.recurringConfig.observe(viewLifecycleOwner) { config ->
            config?.let {
                binding.switchRecurring.isChecked = it.isEnabled
                if (it.mode == "BY_SIZE") {
                    binding.toggleMode.check(binding.btnModeSize.id)
                    binding.etAmount1BHK.setText(it.amount1BHK.takeIf { a -> a > 0 }?.toInt()?.toString() ?: "")
                    binding.etAmount2BHK.setText(it.amount2BHK.takeIf { a -> a > 0 }?.toInt()?.toString() ?: "")
                    binding.etAmount3BHK.setText(it.amount3BHK.takeIf { a -> a > 0 }?.toInt()?.toString() ?: "")
                    binding.etAmountShop.setText(it.amountShop.takeIf { a -> a > 0 }?.toInt()?.toString() ?: "")
                } else if (it.sameAmount > 0) {
                    binding.etSameAmount.setText(it.sameAmount.toInt().toString())
                }
            }
        }

        // Result observer
        viewModel.bulkResult.observe(viewLifecycleOwner) { msg ->
            toast(msg)
            if (msg.startsWith("✅")) dismiss()
        }

        // Generate
        binding.btnGenerate.setOnClickListener { generate() }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                cal.set(y, m, d, 23, 59, 59)
                selectedDueDate = cal.timeInMillis
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                binding.tvSelectedDate.text = fmt.format(cal.time)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun generate() {
        val month = binding.etMonth.text.toString().trim()
        if (month.isEmpty()) { toast("Month likho"); return }
        if (selectedDueDate == 0L) {
            // Default: is mahine ki 10th
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 10)
            selectedDueDate = cal.timeInMillis
        }

        var sameAmount = 0.0
        val sizeAmounts = mutableMapOf<String, Double>()

        if (mode == "SAME") {
            sameAmount = binding.etSameAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (sameAmount <= 0) { toast("Sahi amount daalo"); return }
        } else {
            sizeAmounts["1BHK"] = binding.etAmount1BHK.text.toString().toDoubleOrNull() ?: 0.0
            sizeAmounts["2BHK"] = binding.etAmount2BHK.text.toString().toDoubleOrNull() ?: 0.0
            sizeAmounts["3BHK"] = binding.etAmount3BHK.text.toString().toDoubleOrNull() ?: 0.0
            sizeAmounts["SHOP"] = binding.etAmountShop.text.toString().toDoubleOrNull() ?: 0.0
            if (sizeAmounts.values.all { it <= 0 }) {
                toast("Kam se kam ek size ka amount daalo"); return
            }
        }

        // Generate maintenance for all flats
        viewModel.generateBulk(month, selectedDueDate, mode, sameAmount, sizeAmounts)

        // Recurring config save karo
        if (binding.switchRecurring.isChecked) {
            viewModel.saveRecurring(
                RecurringConfig(
                    id = 1,
                    isEnabled = true,
                    mode = mode,
                    sameAmount = sameAmount,
                    amount1BHK = sizeAmounts["1BHK"] ?: 0.0,
                    amount2BHK = sizeAmounts["2BHK"] ?: 0.0,
                    amount3BHK = sizeAmounts["3BHK"] ?: 0.0,
                    amountShop = sizeAmounts["SHOP"] ?: 0.0,
                    dueDay = 10,
                    lastGeneratedMonth = month
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
