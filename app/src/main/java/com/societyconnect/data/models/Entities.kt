package com.societyconnect.data.models

import com.google.firebase.firestore.Exclude

// Firestore document ID, set after deserialization — excluded from (de)serialization
// itself since the ID lives outside the document body.
interface FirestoreEntity {
    var id: String
}

// data class copy() only forwards primary-constructor parameters, so it can't preserve
// id (declared in the class body to keep it out of equals/copy/serialization) — every
// copy() silently resets id back to "". Chain this after copy() to carry it over.
fun <T : FirestoreEntity> T.withId(id: String): T {
    this.id = id
    return this
}

// ─── MAINTENANCE BILL ─────────────────────────────────────────────────
data class Maintenance(
    val flatNo: String = "",
    val residentName: String = "",
    val residentUid: String = "",
    val amount: Double = 0.0,
    val month: String = "",          // e.g. "June 2024"
    val dueDate: Long = 0L,
    val isPaid: Boolean = false,
    val paidOn: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val utrReference: String? = null,        // resident-submitted UPI transaction ref, pending admin confirmation
    val paymentSubmittedAt: Long? = null
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── COMPLAINT ────────────────────────────────────────────────────────
data class Complaint(
    val title: String = "",
    val description: String = "",
    val category: String = "OTHER",       // WATER, LIFT, ELECTRICITY, CLEANING, SECURITY, PARKING, OTHER
    val flatNo: String = "",
    val raisedBy: String = "",
    val raisedByUid: String = "",
    val status: String = "OPEN",          // OPEN, IN_PROGRESS, RESOLVED
    val adminComment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── NOTICE ──────────────────────────────────────────────────────────
data class Notice(
    val title: String = "",
    val content: String = "",
    val postedBy: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── VISITOR ─────────────────────────────────────────────────────────
data class Visitor(
    val visitorName: String = "",
    val visitingFlat: String = "",
    val purpose: String = "",
    val vehicleNo: String = "",
    val loggedBy: String = "",
    val status: String = "PENDING",       // PENDING, APPROVED, DENIED
    val preApproved: Boolean = false,     // true = resident pre-invited via QR, auto-approved
    val createdAt: Long = System.currentTimeMillis(),
    val checkIn: Long? = null,            // set when the visitor actually arrives at the gate
    val checkOut: Long? = null
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── EMERGENCY CONTACT ────────────────────────────────────────────────
data class EmergencyContact(
    val name: String = "",
    val phone: String = "",
    val type: String = "OTHER",          // PLUMBER, ELECTRICIAN, SECURITY, MEDICAL, FIRE, POLICE
    val isDefault: Boolean = false
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── LEDGER ENTRY (society income / expense) ──────────────────────────
data class LedgerEntry(
    val type: String = "EXPENSE",        // INCOME, EXPENSE
    val category: String = "OTHER",      // MAINTENANCE, REPAIR, SALARY, UTILITY, EVENT, DONATION, OTHER
    val description: String = "",
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val addedBy: String = "",
    val addedByUid: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── AMENITY (bookable society facility) ──────────────────────────────
data class Amenity(
    val name: String = "",
    val category: String = "OTHER",      // CLUBHOUSE, GYM, POOL, COURT, GARDEN, HALL, OTHER
    val description: String = "",
    val addedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── AMENITY BOOKING ───────────────────────────────────────────────────
data class Booking(
    val amenityId: String = "",
    val amenityName: String = "",
    val flatNo: String = "",
    val bookedBy: String = "",
    val bookedByUid: String = "",
    val date: Long = System.currentTimeMillis(),
    val slot: String = "",
    val status: String = "PENDING",      // PENDING, APPROVED, DENIED, CANCELLED
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── EVENT ──────────────────────────────────────────────────────────
data class Event(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val date: Long = System.currentTimeMillis(),
    val postedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── POLL ───────────────────────────────────────────────────────────
data class Poll(
    val question: String = "",
    val options: List<String> = emptyList(),
    val postedBy: String = "",
    val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── POLL VOTE (one doc per voter, doc ID == voter's uid) ─────────────
data class PollVote(
    val optionIndex: Int = 0,
    val votedAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── ASSET (society infrastructure: lifts, pumps, generators, etc.) ──
data class Asset(
    val name: String = "",
    val category: String = "OTHER",      // LIFT, GENERATOR, PUMP, FIRE_SAFETY, CCTV, OTHER
    val location: String = "",
    val vendorName: String = "",
    val vendorPhone: String = "",
    val purchaseDate: Long? = null,
    val warrantyExpiry: Long? = null,
    val amcExpiry: Long? = null,
    val notes: String = "",
    val addedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── SERVICE RECORD (maintenance/service history entry for an asset) ──
data class ServiceRecord(
    val assetId: String = "",
    val assetName: String = "",
    val description: String = "",
    val cost: Double = 0.0,
    val performedBy: String = "",
    val date: Long = System.currentTimeMillis(),
    val loggedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : FirestoreEntity {
    @get:Exclude @set:Exclude
    override var id: String = ""
}

// ─── RECURRING MAINTENANCE CONFIG ─────────────────────────────────────
// Secretary ek baar set karta hai, har mahine auto-generate hota hai.
// Stored as a single fixed document (societies/{societyId}/recurringConfig/default).
data class RecurringConfig(
    val isEnabled: Boolean = false,
    val mode: String = "SAME",             // SAME (sabka ek) ya BY_SIZE (size ke hisaab se)
    val sameAmount: Double = 0.0,          // SAME mode ke liye
    val amount1BHK: Double = 0.0,          // BY_SIZE mode ke liye
    val amount2BHK: Double = 0.0,
    val amount3BHK: Double = 0.0,
    val amountShop: Double = 0.0,
    val dueDay: Int = 10,                  // Mahine ka konsa din due (e.g. 10th)
    val lastGeneratedMonth: String = ""    // "June 2024" — duplicate rokne ke liye
)
