package com.societyconnect.ui.dashboard

import androidx.lifecycle.ViewModel
import com.societyconnect.data.repository.SocietyRepository

class DashboardViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository

    fun init(societyId: String) {
        if (!::repo.isInitialized) repo = SocietyRepository(societyId)
    }

    val pendingCount get() = repo.pendingCount
    val openComplaintsCount get() = repo.openComplaintsCount
    val todayVisitors get() = repo.todayVisitorCount
    val totalPending get() = repo.totalPending
}
