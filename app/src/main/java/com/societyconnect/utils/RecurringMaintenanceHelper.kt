package com.societyconnect.utils

import android.content.Context
import com.societyconnect.data.repository.SocietyRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * RecurringMaintenanceHelper
 *
 * Jab secretary app khole, ye check karta hai:
 *  - Recurring ON hai kya?
 *  - Is mahine ka maintenance ban chuka hai kya?
 *  - Agar nahi → automatically sabhi flats ka maintenance generate kar deta hai
 *
 * Isse secretary ko har mahine manually bhejne ki zaroorat nahi.
 *
 * Call this from DashboardFragment.onViewCreated() — sirf admin/secretary ke liye.
 */
object RecurringMaintenanceHelper {

    suspend fun checkAndGenerate(context: Context): String? {
        val repo = SocietyRepository(context)
        val config = repo.getRecurringConfig() ?: return null

        if (!config.isEnabled) return null

        val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            .format(Calendar.getInstance().time)

        // Is mahine ka pehle se ban gaya?
        if (config.lastGeneratedMonth == currentMonth) return null
        if (repo.isMonthAlreadyGenerated(currentMonth)) return null

        // Due date = is mahine ka config.dueDay (e.g. 10th)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, config.dueDay.coerceIn(1, 28))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        val dueDate = cal.timeInMillis

        val sizeAmounts = mapOf(
            "1BHK" to config.amount1BHK,
            "2BHK" to config.amount2BHK,
            "3BHK" to config.amount3BHK,
            "SHOP" to config.amountShop
        )

        val (generated, _) = repo.generateBulkMaintenance(
            month = currentMonth,
            dueDate = dueDate,
            mode = config.mode,
            sameAmount = config.sameAmount,
            sizeAmounts = sizeAmounts
        )

        // Config update karo taaki dobara na chale
        if (generated > 0) {
            repo.saveRecurringConfig(config.copy(lastGeneratedMonth = currentMonth))
            return "✅ $currentMonth ka maintenance auto-generate ho gaya ($generated flats)"
        }
        return null
    }
}
