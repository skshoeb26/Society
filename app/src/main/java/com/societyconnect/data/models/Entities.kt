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
