package com.societyconnect.data.repository

import android.content.Context
import com.societyconnect.data.AppDatabase
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.data.models.*

class SocietyRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val authRepo = AuthRepository()

    // Maintenance
    val allMaintenance = db.maintenanceDao().getAll()
    val pendingMaintenance = db.maintenanceDao().getPending()
    val totalCollected = db.maintenanceDao().getTotalCollected()
    val totalPending = db.maintenanceDao().getTotalPending()
    val pendingCount = db.maintenanceDao().getPendingCount()
    fun getMaintenanceByFlat(flatNo: String) = db.maintenanceDao().getByFlat(flatNo)
    suspend fun addMaintenance(m: Maintenance) = db.maintenanceDao().insert(m)
    suspend fun updateMaintenance(m: Maintenance) = db.maintenanceDao().update(m)
    suspend fun deleteMaintenance(m: Maintenance) = db.maintenanceDao().delete(m)

    // ─── BULK MAINTENANCE GENERATION ──────────────────────────────────────
    // Sabhi flats ka ek saath maintenance banao.
    // Returns: Pair(kitne generate hue, kitne skip hue kyunki pehle se the)

    suspend fun generateBulkMaintenance(
        societyId: String,
        month: String,           // "June 2024"
        dueDate: Long,
        mode: String,            // "SAME" ya "BY_SIZE"
        sameAmount: Double,
        sizeAmounts: Map<String, Double>   // {"1BHK" to 1000.0, "2BHK" to 1500.0, ...}
    ): Pair<Int, Int> {
        val residents = authRepo.getResidents(societyId)
        var generated = 0
        var skipped = 0
        val toInsert = mutableListOf<Maintenance>()

        for (resident in residents) {
            // Pehle se is mahine ka hai? toh skip
            val exists = db.maintenanceDao()
                .countByFlatAndMonth(resident.flatNo, month) > 0
            if (exists) { skipped++; continue }

            val amount = if (mode == "SAME") sameAmount
                         else sizeAmounts[resident.flatType] ?: sameAmount

            if (amount <= 0.0) { skipped++; continue }

            toInsert.add(
                Maintenance(
                    flatNo = resident.flatNo,
                    residentName = resident.name,
                    amount = amount,
                    month = month,
                    dueDate = dueDate,
                    isPaid = false
                )
            )
            generated++
        }

        if (toInsert.isNotEmpty()) db.maintenanceDao().insertAll(toInsert)
        return Pair(generated, skipped)
    }

    suspend fun isMonthAlreadyGenerated(month: String): Boolean =
        db.maintenanceDao().countByMonth(month) > 0

    // ─── RECURRING CONFIG ─────────────────────────────────────────────────
    suspend fun saveRecurringConfig(config: RecurringConfig) =
        db.recurringConfigDao().save(config)
    suspend fun getRecurringConfig() = db.recurringConfigDao().get()
    fun getRecurringConfigLive() = db.recurringConfigDao().getLive()

    // Complaints
    val allComplaints = db.complaintDao().getAll()
    val openComplaintsCount = db.complaintDao().getOpenCount()
    fun getComplaintsByFlat(flatNo: String) = db.complaintDao().getByFlat(flatNo)
    fun getComplaintsByStatus(status: String) = db.complaintDao().getByStatus(status)
    suspend fun addComplaint(c: Complaint) = db.complaintDao().insert(c)
    suspend fun updateComplaint(c: Complaint) = db.complaintDao().update(c)
    suspend fun deleteComplaint(c: Complaint) = db.complaintDao().delete(c)
    suspend fun getComplaintById(id: Int) = db.complaintDao().getById(id)

    // Notices
    val allNotices = db.noticeDao().getAll()
    fun getNewNoticeCount(since: Long) = db.noticeDao().getNewCount(since)
    suspend fun addNotice(n: Notice) = db.noticeDao().insert(n)
    suspend fun updateNotice(n: Notice) = db.noticeDao().update(n)
    suspend fun deleteNotice(n: Notice) = db.noticeDao().delete(n)

    // Visitors
    val allVisitors = db.visitorDao().getAll()
    val activeVisitors = db.visitorDao().getActiveVisitors()
    val todayVisitorCount = db.visitorDao().getTodayCount()
    fun getVisitorsByFlat(flatNo: String) = db.visitorDao().getByFlat(flatNo)
    suspend fun addVisitor(v: Visitor) = db.visitorDao().insert(v)
    suspend fun updateVisitor(v: Visitor) = db.visitorDao().update(v)

    // Emergency
    val allEmergencyContacts = db.emergencyContactDao().getAll()
    suspend fun addEmergencyContact(e: EmergencyContact) = db.emergencyContactDao().insert(e)
    suspend fun updateEmergencyContact(e: EmergencyContact) = db.emergencyContactDao().update(e)
    suspend fun deleteEmergencyContact(e: EmergencyContact) = db.emergencyContactDao().delete(e)
    suspend fun getEmergencyContactCount() = db.emergencyContactDao().getCount()
}
