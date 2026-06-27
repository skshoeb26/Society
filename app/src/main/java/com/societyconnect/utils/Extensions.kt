package com.societyconnect.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ─── View Extensions ─────────────────────────────────────────────────
fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun View.snack(msg: String, duration: Int = Snackbar.LENGTH_SHORT) =
    Snackbar.make(this, msg, duration).show()

// ─── Date Helpers ────────────────────────────────────────────────────
fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun getCurrentMonthYear(): String {
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return sdf.format(Date())
}

// ─── Currency ────────────────────────────────────────────────────────
fun Double.toRupees(): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(this)
}

// ─── Phone Call ──────────────────────────────────────────────────────
fun Context.makeCall(phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
    startActivity(intent)
}

// ─── Invite Code ─────────────────────────────────────────────────────
fun generateInviteCode(length: Int = 6): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I — avoids look-alike mistakes
    return (1..length).map { chars.random() }.joinToString("")
}

// ─── Greeting ────────────────────────────────────────────────────────
fun getGreeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..17 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

// ─── Notification Channel ────────────────────────────────────────────
fun Context.createNotificationChannels() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channels = listOf(
        NotificationChannel("maintenance", "Maintenance Reminders", NotificationManager.IMPORTANCE_HIGH)
            .also { it.description = "Reminders for pending maintenance dues" },
        NotificationChannel("visitors", "Visitor Alerts", NotificationManager.IMPORTANCE_HIGH)
            .also { it.description = "Alerts when a visitor arrives" },
        NotificationChannel("notices", "Society Notices", NotificationManager.IMPORTANCE_DEFAULT)
            .also { it.description = "New society notices" },
        NotificationChannel("complaints", "Complaint Updates", NotificationManager.IMPORTANCE_DEFAULT)
            .also { it.description = "Updates on your complaints" }
    )

    channels.forEach { manager.createNotificationChannel(it) }
}

// ─── UPI Payments ────────────────────────────────────────────────────
fun buildUpiPayUri(upiId: String, payeeName: String, amount: Double, note: String): Uri =
    Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("am", "%.2f".format(amount))
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note)
        .build()

fun Context.payViaUpi(upiId: String, payeeName: String, amount: Double, note: String) {
    startActivity(Intent(Intent.ACTION_VIEW, buildUpiPayUri(upiId, payeeName, amount, note)))
}

// ─── Status Color ────────────────────────────────────────────────────
fun getStatusColor(status: String, context: Context): Int {
    return when (status) {
        "OPEN" -> context.getColor(com.societyconnect.R.color.status_open)
        "IN_PROGRESS" -> context.getColor(com.societyconnect.R.color.status_in_progress)
        "RESOLVED" -> context.getColor(com.societyconnect.R.color.status_resolved)
        "PAID" -> context.getColor(com.societyconnect.R.color.status_paid)
        "PENDING" -> context.getColor(com.societyconnect.R.color.status_pending)
        else -> context.getColor(com.societyconnect.R.color.text_secondary)
    }
}

fun getStatusLabel(status: String): String {
    return when (status) {
        "OPEN" -> "Open"
        "IN_PROGRESS" -> "In Progress"
        "RESOLVED" -> "Resolved"
        else -> status
    }
}

fun getCategoryLabel(category: String): String {
    return when (category) {
        "WATER" -> "💧 Water"
        "LIFT" -> "🛗 Lift"
        "ELECTRICITY" -> "⚡ Electricity"
        "CLEANING" -> "🧹 Cleaning"
        "SECURITY" -> "🔒 Security"
        "PARKING" -> "🅿️ Parking"
        else -> "📋 Other"
    }
}

fun getLedgerCategoryLabel(category: String): String {
    return when (category) {
        "REPAIR" -> "🛠 Repair"
        "SALARY" -> "💵 Salary"
        "UTILITY" -> "⚡ Utility"
        "EVENT" -> "🎉 Event"
        "DONATION" -> "🎁 Donation"
        "MAINTENANCE" -> "💰 Maintenance"
        else -> "📋 Other"
    }
}

fun getAmenityIcon(category: String): String {
    return when (category) {
        "CLUBHOUSE" -> "🏛"
        "GYM" -> "🏋"
        "POOL" -> "🏊"
        "COURT" -> "🎾"
        "GARDEN" -> "🌳"
        "HALL" -> "🏢"
        else -> "📍"
    }
}

fun getAssetIcon(category: String): String {
    return when (category) {
        "LIFT" -> "🛗"
        "GENERATOR" -> "🔌"
        "PUMP" -> "🚰"
        "FIRE_SAFETY" -> "🧯"
        "CCTV" -> "📷"
        else -> "🛠"
    }
}

fun getStaffIcon(role: String): String {
    return when (role) {
        "WATCHMAN" -> "💂"
        "SWEEPER" -> "🧹"
        "ELECTRICIAN" -> "⚡"
        "PLUMBER" -> "🔧"
        "GARDENER" -> "🌳"
        "COOK" -> "🍳"
        "DRIVER" -> "🚗"
        else -> "👤"
    }
}

fun getRoleLabel(role: String): String {
    return when (role) {
        "ADMIN" -> "Secretary"
        "SECURITY" -> "Security Guard"
        "COMMITTEE" -> "Committee"
        else -> "Resident"
    }
}

fun getContactTypeIcon(type: String): String {
    return when (type) {
        "PLUMBER" -> "🔧"
        "ELECTRICIAN" -> "⚡"
        "SECURITY" -> "👮"
        "MEDICAL" -> "🏥"
        "FIRE" -> "🚒"
        "POLICE" -> "🚔"
        else -> "📞"
    }
}

fun getDocumentCategoryLabel(category: String): String {
    return when (category) {
        "LEGAL" -> "⚖️ Legal"
        "FINANCIAL" -> "💰 Financial"
        "MINUTES" -> "📝 Minutes"
        "INSURANCE" -> "🛡 Insurance"
        else -> "📄 Other"
    }
}

fun Long.toFileSizeString(): String {
    val kb = this / 1024.0
    return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024)
}
