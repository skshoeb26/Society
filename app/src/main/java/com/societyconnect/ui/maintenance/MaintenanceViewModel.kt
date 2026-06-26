package com.societyconnect.ui.maintenance

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.societyconnect.data.models.Maintenance
import com.societyconnect.data.models.RecurringConfig
import com.societyconnect.data.repository.SocietyRepository
import kotlinx.coroutines.launch

class MaintenanceViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository

    fun init(societyId: String) {
        if (!::repo.isInitialized) repo = SocietyRepository(societyId)
    }

    val allMaintenance get() = repo.allMaintenance
    val pendingMaintenance get() = repo.pendingMaintenance
    val totalCollected get() = repo.totalCollected
    val totalPending get() = repo.totalPending

    fun getByFlat(flatNo: String) = repo.getMaintenanceByFlat(flatNo)

    fun addMaintenance(m: Maintenance) = viewModelScope.launch { repo.addMaintenance(m) }

    fun togglePaid(m: Maintenance) = viewModelScope.launch {
        repo.updateMaintenance(
            m.copy(
                isPaid = !m.isPaid,
                paidOn = if (!m.isPaid) System.currentTimeMillis() else null
            )
        )
    }

    fun deleteMaintenance(m: Maintenance) = viewModelScope.launch { repo.deleteMaintenance(m) }

    // ─── BULK GENERATION ──────────────────────────────────────────────────
    // Result: "✅ 24 flats ka maintenance ban gaya (3 already the)"
    val bulkResult = MutableLiveData<String>()

    fun generateBulk(
        month: String,
        dueDate: Long,
        mode: String,
        sameAmount: Double,
        sizeAmounts: Map<String, Double>
    ) = viewModelScope.launch {
        val (generated, skipped) = repo.generateBulkMaintenance(
            month, dueDate, mode, sameAmount, sizeAmounts
        )
        bulkResult.postValue(
            when {
                generated == 0 && skipped > 0 ->
                    "ℹ️ Is mahine ka maintenance pehle se hai ($skipped flats)"
                generated == 0 ->
                    "⚠️ Koi resident nahi mila. Pehle residents add karo."
                skipped > 0 ->
                    "✅ $generated flats ka maintenance ban gaya ($skipped already the)"
                else ->
                    "✅ Sabhi $generated flats ka maintenance ban gaya!"
            }
        )
    }

    // ─── RECURRING CONFIG ─────────────────────────────────────────────────
    val recurringConfig get() = repo.getRecurringConfigLive()

    fun saveRecurring(config: RecurringConfig) = viewModelScope.launch {
        repo.saveRecurringConfig(config)
    }
}
