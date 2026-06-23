package com.societyconnect.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import com.societyconnect.data.repository.SocietyRepository

class DashboardViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository

    fun init(context: Context) {
        if (!::repo.isInitialized) repo = SocietyRepository(context)
    }

    val pendingCount get() = repo.pendingCount
    val openComplaintsCount get() = repo.openComplaintsCount
    val todayVisitors get() = repo.todayVisitorCount
    val totalPending get() = repo.totalPending
}
