package com.societyconnect.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.data.firebase.FirestoreDocumentLiveData
import com.societyconnect.data.firebase.FirestoreQueryLiveData
import com.societyconnect.data.firebase.toEntities
import com.societyconnect.data.models.*
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class SocietyRepository(private val societyId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()
    private val societyRef = db.collection("societies").document(societyId)

    // Maintenance
    val allMaintenance: LiveData<List<Maintenance>> =
        FirestoreQueryLiveData(
            societyRef.collection("maintenanceBills").orderBy("dueDate", Query.Direction.DESCENDING)
        ) { it.toEntities(Maintenance::class.java) }

    val pendingMaintenance: LiveData<List<Maintenance>> =
        FirestoreQueryLiveData(
            societyRef.collection("maintenanceBills")
                .whereEqualTo("isPaid", false)
                .orderBy("dueDate", Query.Direction.ASCENDING)
        ) { it.toEntities(Maintenance::class.java) }

    val totalCollected: LiveData<Double> = allMaintenance.map { list ->
        list.filter { it.isPaid }.sumOf { it.amount }
    }
    val totalPending: LiveData<Double> = allMaintenance.map { list ->
        list.filter { !it.isPaid }.sumOf { it.amount }
    }
    val pendingCount: LiveData<Int> = pendingMaintenance.map { it.size }

    val defaulters: LiveData<List<Maintenance>> = pendingMaintenance.map { list ->
        val now = System.currentTimeMillis()
        list.filter { it.dueDate < now }
    }

    fun getMaintenanceByFlat(flatNo: String): LiveData<List<Maintenance>> =
        FirestoreQueryLiveData(
            societyRef.collection("maintenanceBills")
                .whereEqualTo("flatNo", flatNo)
                .orderBy("dueDate", Query.Direction.DESCENDING)
        ) { it.toEntities(Maintenance::class.java) }

    suspend fun addMaintenance(m: Maintenance) {
        societyRef.collection("maintenanceBills").add(m).await()
    }
    suspend fun updateMaintenance(m: Maintenance) {
        societyRef.collection("maintenanceBills").document(m.id).set(m).await()
    }
    suspend fun deleteMaintenance(m: Maintenance) {
        societyRef.collection("maintenanceBills").document(m.id).delete().await()
    }
    suspend fun submitPaymentReference(m: Maintenance, utr: String) {
        societyRef.collection("maintenanceBills").document(m.id).update(
            mapOf("utrReference" to utr, "paymentSubmittedAt" to System.currentTimeMillis())
        ).await()
    }

    // ─── BULK MAINTENANCE GENERATION ──────────────────────────────────────
    // Sabhi flats ka ek saath maintenance banao.
    // Returns: Pair(kitne generate hue, kitne skip hue kyunki pehle se the)

    suspend fun generateBulkMaintenance(
        month: String,           // "June 2024"
        dueDate: Long,
        mode: String,            // "SAME" ya "BY_SIZE"
        sameAmount: Double,
        sizeAmounts: Map<String, Double>   // {"1BHK" to 1000.0, "2BHK" to 1500.0, ...}
    ): Pair<Int, Int> {
        val residents = authRepo.getResidents(societyId)
        var generated = 0
        var skipped = 0
        val billsCollection = societyRef.collection("maintenanceBills")

        for (resident in residents) {
            // Pehle se is mahine ka hai? toh skip
            val exists = billsCollection
                .whereEqualTo("flatNo", resident.flatNo)
                .whereEqualTo("month", month)
                .get().await().documents.isNotEmpty()
            if (exists) { skipped++; continue }

            val amount = if (mode == "SAME") sameAmount
                         else sizeAmounts[resident.flatType] ?: sameAmount

            if (amount <= 0.0) { skipped++; continue }

            billsCollection.add(
                Maintenance(
                    flatNo = resident.flatNo,
                    residentName = resident.name,
                    residentUid = resident.uid,
                    amount = amount,
                    month = month,
                    dueDate = dueDate,
                    isPaid = false
                )
            ).await()
            generated++
        }

        return Pair(generated, skipped)
    }

    suspend fun isMonthAlreadyGenerated(month: String): Boolean =
        societyRef.collection("maintenanceBills")
            .whereEqualTo("month", month)
            .limit(1).get().await().documents.isNotEmpty()

    // ─── RECURRING CONFIG ─────────────────────────────────────────────────
    // Stored as a single fixed document — not a collection.
    private val recurringConfigRef = societyRef.collection("recurringConfig").document("default")

    suspend fun saveRecurringConfig(config: RecurringConfig) {
        recurringConfigRef.set(config).await()
    }
    suspend fun getRecurringConfig(): RecurringConfig? =
        recurringConfigRef.get().await().toObject(RecurringConfig::class.java)
    fun getRecurringConfigLive(): LiveData<RecurringConfig?> =
        FirestoreDocumentLiveData(recurringConfigRef) { it.toObject(RecurringConfig::class.java) }

    // Complaints
    val allComplaints: LiveData<List<Complaint>> =
        FirestoreQueryLiveData(
            societyRef.collection("complaints").orderBy("createdAt", Query.Direction.DESCENDING)
        ) { it.toEntities(Complaint::class.java) }

    val openComplaintsCount: LiveData<Int> = allComplaints.map { list ->
        list.count { it.status == "OPEN" }
    }

    fun getComplaintsByFlat(flatNo: String): LiveData<List<Complaint>> =
        FirestoreQueryLiveData(
            societyRef.collection("complaints")
                .whereEqualTo("flatNo", flatNo)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        ) { it.toEntities(Complaint::class.java) }

    fun getComplaintsByStatus(status: String): LiveData<List<Complaint>> =
        FirestoreQueryLiveData(
            societyRef.collection("complaints")
                .whereEqualTo("status", status)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        ) { it.toEntities(Complaint::class.java) }

    suspend fun addComplaint(c: Complaint) {
        societyRef.collection("complaints").add(c).await()
    }
    suspend fun updateComplaint(c: Complaint) {
        societyRef.collection("complaints").document(c.id).set(c).await()
    }
    suspend fun deleteComplaint(c: Complaint) {
        societyRef.collection("complaints").document(c.id).delete().await()
    }

    // Notices
    val allNotices: LiveData<List<Notice>> =
        FirestoreQueryLiveData(
            societyRef.collection("notices")
                .orderBy("isPinned", Query.Direction.DESCENDING)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        ) { it.toEntities(Notice::class.java) }

    suspend fun addNotice(n: Notice) {
        societyRef.collection("notices").add(n).await()
    }
    suspend fun updateNotice(n: Notice) {
        societyRef.collection("notices").document(n.id).set(n).await()
    }
    suspend fun deleteNotice(n: Notice) {
        societyRef.collection("notices").document(n.id).delete().await()
    }

    // Visitors
    val allVisitors: LiveData<List<Visitor>> =
        FirestoreQueryLiveData(
            societyRef.collection("visitors").orderBy("checkIn", Query.Direction.DESCENDING)
        ) { it.toEntities(Visitor::class.java) }

    val activeVisitors: LiveData<List<Visitor>> = allVisitors.map { list ->
        list.filter { it.status == "APPROVED" && it.checkIn != null && it.checkOut == null }
    }

    val pendingVisitors: LiveData<List<Visitor>> = allVisitors.map { list ->
        list.filter { it.status == "PENDING" }
    }

    val todayVisitorCount: LiveData<Int> = allVisitors.map { list ->
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        list.count { it.checkIn != null && it.checkIn >= startOfDay }
    }

    fun getVisitorsByFlat(flatNo: String): LiveData<List<Visitor>> =
        FirestoreQueryLiveData(
            societyRef.collection("visitors")
                .whereEqualTo("visitingFlat", flatNo)
                .orderBy("checkIn", Query.Direction.DESCENDING)
        ) { it.toEntities(Visitor::class.java) }

    suspend fun addVisitor(v: Visitor): String {
        val ref = societyRef.collection("visitors").add(v).await()
        return ref.id
    }
    suspend fun updateVisitor(v: Visitor) {
        societyRef.collection("visitors").document(v.id).set(v).await()
    }
    suspend fun getVisitor(visitorId: String): Visitor? {
        val snap = societyRef.collection("visitors").document(visitorId).get().await()
        if (!snap.exists()) return null
        return snap.toObject(Visitor::class.java)?.also { it.id = snap.id }
    }

    // Emergency
    private val emergencyContactsLive: LiveData<List<EmergencyContact>> =
        FirestoreQueryLiveData(societyRef.collection("emergencyContacts")) {
            it.toEntities(EmergencyContact::class.java)
        }

    val allEmergencyContacts: LiveData<List<EmergencyContact>> = emergencyContactsLive.map { list ->
        list.sortedWith(compareByDescending<EmergencyContact> { it.isDefault }.thenBy { it.type })
    }

    suspend fun addEmergencyContact(e: EmergencyContact) {
        societyRef.collection("emergencyContacts").add(e).await()
    }
    suspend fun updateEmergencyContact(e: EmergencyContact) {
        societyRef.collection("emergencyContacts").document(e.id).set(e).await()
    }
    suspend fun deleteEmergencyContact(e: EmergencyContact) {
        societyRef.collection("emergencyContacts").document(e.id).delete().await()
    }

    // ─── LEDGER ────────────────────────────────────────────────────────────
    val allLedgerEntries: LiveData<List<LedgerEntry>> =
        FirestoreQueryLiveData(
            societyRef.collection("ledger").orderBy("date", Query.Direction.DESCENDING)
        ) { it.toEntities(LedgerEntry::class.java) }

    val totalIncome: LiveData<Double> = allLedgerEntries.map { list ->
        list.filter { it.type == "INCOME" }.sumOf { it.amount }
    }
    val totalExpense: LiveData<Double> = allLedgerEntries.map { list ->
        list.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    }
    val ledgerBalance: LiveData<Double> = allLedgerEntries.map { list ->
        list.sumOf { if (it.type == "INCOME") it.amount else -it.amount }
    }

    suspend fun addLedgerEntry(e: LedgerEntry) {
        societyRef.collection("ledger").add(e).await()
    }
    suspend fun updateLedgerEntry(e: LedgerEntry) {
        societyRef.collection("ledger").document(e.id).set(e).await()
    }
    suspend fun deleteLedgerEntry(e: LedgerEntry) {
        societyRef.collection("ledger").document(e.id).delete().await()
    }

    // ─── AMENITIES ───────────────────────────────────────────────────────────
    val allAmenities: LiveData<List<Amenity>> =
        FirestoreQueryLiveData(
            societyRef.collection("amenities").orderBy("name", Query.Direction.ASCENDING)
        ) { it.toEntities(Amenity::class.java) }

    suspend fun addAmenity(a: Amenity) {
        societyRef.collection("amenities").add(a).await()
    }
    suspend fun deleteAmenity(a: Amenity) {
        societyRef.collection("amenities").document(a.id).delete().await()
    }

    // ─── BOOKINGS ────────────────────────────────────────────────────────────
    val allBookings: LiveData<List<Booking>> =
        FirestoreQueryLiveData(
            societyRef.collection("bookings").orderBy("date", Query.Direction.DESCENDING)
        ) { it.toEntities(Booking::class.java) }

    fun getBookingsByUid(uid: String): LiveData<List<Booking>> =
        FirestoreQueryLiveData(
            societyRef.collection("bookings")
                .whereEqualTo("bookedByUid", uid)
                .orderBy("date", Query.Direction.DESCENDING)
        ) { it.toEntities(Booking::class.java) }

    suspend fun addBooking(b: Booking) {
        societyRef.collection("bookings").add(b).await()
    }
    suspend fun updateBooking(b: Booking) {
        societyRef.collection("bookings").document(b.id).set(b).await()
    }

    // ─── ASSETS ──────────────────────────────────────────────────────────────
    val allAssets: LiveData<List<Asset>> =
        FirestoreQueryLiveData(
            societyRef.collection("assets").orderBy("name", Query.Direction.ASCENDING)
        ) { it.toEntities(Asset::class.java) }

    suspend fun addAsset(a: Asset) {
        societyRef.collection("assets").add(a).await()
    }
    suspend fun deleteAsset(a: Asset) {
        societyRef.collection("assets").document(a.id).delete().await()
    }

    // ─── SERVICE RECORDS ─────────────────────────────────────────────────────
    val allServiceRecords: LiveData<List<ServiceRecord>> =
        FirestoreQueryLiveData(
            societyRef.collection("serviceRecords").orderBy("date", Query.Direction.DESCENDING)
        ) { it.toEntities(ServiceRecord::class.java) }

    fun getServiceRecordsByAsset(assetId: String): LiveData<List<ServiceRecord>> =
        FirestoreQueryLiveData(
            societyRef.collection("serviceRecords")
                .whereEqualTo("assetId", assetId)
                .orderBy("date", Query.Direction.DESCENDING)
        ) { it.toEntities(ServiceRecord::class.java) }

    suspend fun addServiceRecord(s: ServiceRecord) {
        societyRef.collection("serviceRecords").add(s).await()
    }
    suspend fun deleteServiceRecord(s: ServiceRecord) {
        societyRef.collection("serviceRecords").document(s.id).delete().await()
    }

    // ─── STAFF ───────────────────────────────────────────────────────────────
    val allStaff: LiveData<List<StaffMember>> =
        FirestoreQueryLiveData(
            societyRef.collection("staff").orderBy("name", Query.Direction.ASCENDING)
        ) { it.toEntities(StaffMember::class.java) }

    suspend fun addStaff(s: StaffMember) {
        societyRef.collection("staff").add(s).await()
    }
    suspend fun deleteStaff(s: StaffMember) {
        societyRef.collection("staff").document(s.id).delete().await()
    }

    // ─── EVENTS ──────────────────────────────────────────────────────────────
    val allEvents: LiveData<List<Event>> =
        FirestoreQueryLiveData(
            societyRef.collection("events").orderBy("date", Query.Direction.ASCENDING)
        ) { it.toEntities(Event::class.java) }

    suspend fun addEvent(e: Event) {
        societyRef.collection("events").add(e).await()
    }
    suspend fun deleteEvent(e: Event) {
        societyRef.collection("events").document(e.id).delete().await()
    }

    // ─── POLLS ───────────────────────────────────────────────────────────────
    val allPolls: LiveData<List<Poll>> =
        FirestoreQueryLiveData(
            societyRef.collection("polls").orderBy("createdAt", Query.Direction.DESCENDING)
        ) { it.toEntities(Poll::class.java) }

    suspend fun addPoll(p: Poll) {
        societyRef.collection("polls").add(p).await()
    }
    suspend fun closePoll(p: Poll) {
        societyRef.collection("polls").document(p.id).update("isClosed", true).await()
    }
    suspend fun deletePoll(p: Poll) {
        societyRef.collection("polls").document(p.id).delete().await()
    }
    suspend fun getPollVotes(pollId: String): List<PollVote> =
        societyRef.collection("polls").document(pollId).collection("votes")
            .get().await().toEntities(PollVote::class.java)

    suspend fun castVote(pollId: String, uid: String, optionIndex: Int) {
        societyRef.collection("polls").document(pollId).collection("votes")
            .document(uid).set(PollVote(optionIndex = optionIndex)).await()
    }

    // ─── AGM ─────────────────────────────────────────────────────────────────
    val allAgmMeetings: LiveData<List<AgmMeeting>> =
        FirestoreQueryLiveData(
            societyRef.collection("agm").orderBy("date", Query.Direction.DESCENDING)
        ) { it.toEntities(AgmMeeting::class.java) }

    suspend fun addAgmMeeting(m: AgmMeeting) {
        societyRef.collection("agm").add(m).await()
    }
    suspend fun updateAgmMeeting(m: AgmMeeting) {
        societyRef.collection("agm").document(m.id).set(m).await()
    }
    suspend fun deleteAgmMeeting(m: AgmMeeting) {
        societyRef.collection("agm").document(m.id).delete().await()
    }
    suspend fun getAgmRsvps(agmId: String): List<AgmRsvp> =
        societyRef.collection("agm").document(agmId).collection("rsvps")
            .get().await().toEntities(AgmRsvp::class.java)

    suspend fun setAgmRsvp(agmId: String, uid: String, rsvp: AgmRsvp) {
        societyRef.collection("agm").document(agmId).collection("rsvps")
            .document(uid).set(rsvp).await()
    }
}
