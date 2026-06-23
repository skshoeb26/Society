package com.societyconnect.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── USER ────────────────────────────────────────────────────────────
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val flatNo: String,
    val phone: String,
    val password: String,
    val role: String,          // ADMIN, RESIDENT, SECURITY
    val societyName: String,
    val flatType: String = "1BHK",   // 1BHK, 2BHK, 3BHK, SHOP — bulk maintenance ke liye
    val createdAt: Long = System.currentTimeMillis()
)

// ─── MAINTENANCE ─────────────────────────────────────────────────────
@Entity(tableName = "maintenance")
data class Maintenance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val flatNo: String,
    val residentName: String,
    val amount: Double,
    val month: String,          // e.g. "June 2024"
    val dueDate: Long,
    val isPaid: Boolean = false,
    val paidOn: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── COMPLAINT ────────────────────────────────────────────────────────
@Entity(tableName = "complaints")
data class Complaint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String,       // WATER, LIFT, ELECTRICITY, CLEANING, OTHER
    val flatNo: String,
    val raisedBy: String,
    val status: String = "OPEN",  // OPEN, IN_PROGRESS, RESOLVED
    val adminComment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── NOTICE ──────────────────────────────────────────────────────────
@Entity(tableName = "notices")
data class Notice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val postedBy: String,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── VISITOR ─────────────────────────────────────────────────────────
@Entity(tableName = "visitors")
data class Visitor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val visitorName: String,
    val visitingFlat: String,
    val purpose: String,
    val vehicleNo: String = "",
    val loggedBy: String,
    val checkIn: Long = System.currentTimeMillis(),
    val checkOut: Long? = null
)

// ─── EMERGENCY CONTACT ────────────────────────────────────────────────
@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val type: String,           // PLUMBER, ELECTRICIAN, SECURITY, MEDICAL, FIRE, POLICE
    val isDefault: Boolean = false
)

// ─── RECURRING MAINTENANCE CONFIG ─────────────────────────────────────
// Secretary ek baar set karta hai, har mahine auto-generate hota hai
@Entity(tableName = "recurring_config")
data class RecurringConfig(
    @PrimaryKey val id: Int = 1,           // Hamesha single row (id=1)
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
