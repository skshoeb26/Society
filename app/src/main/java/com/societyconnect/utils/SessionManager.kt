package com.societyconnect.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("society_session", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_NAME = "name"
        const val KEY_FLAT_NO = "flat_no"
        const val KEY_ROLE = "role"
        const val KEY_SOCIETY_ID = "society_id"
        const val KEY_SOCIETY = "society"
        const val KEY_PHONE = "phone"
        const val KEY_IS_LOGGED_IN = "is_logged_in"

        const val ROLE_ADMIN = "ADMIN"          // Secretary — owns the subscription, can invite members
        const val ROLE_RESIDENT = "RESIDENT"
        const val ROLE_SECURITY = "SECURITY"
        const val ROLE_COMMITTEE = "COMMITTEE"
        const val ROLE_TREASURER = "TREASURER"
    }

    fun saveSession(
        userId: String,
        name: String,
        flatNo: String,
        role: String,
        societyId: String,
        societyName: String,
        phone: String
    ) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_NAME, name)
            putString(KEY_FLAT_NO, flatNo)
            putString(KEY_ROLE, role)
            putString(KEY_SOCIETY_ID, societyId)
            putString(KEY_SOCIETY, societyName)
            putString(KEY_PHONE, phone)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun isLoggedIn() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun getUserId() = prefs.getString(KEY_USER_ID, "") ?: ""
    fun getName() = prefs.getString(KEY_NAME, "") ?: ""
    fun getFlatNo() = prefs.getString(KEY_FLAT_NO, "") ?: ""
    fun getRole() = prefs.getString(KEY_ROLE, ROLE_RESIDENT) ?: ROLE_RESIDENT
    fun getSocietyId() = prefs.getString(KEY_SOCIETY_ID, "") ?: ""
    fun getSociety() = prefs.getString(KEY_SOCIETY, "") ?: ""
    fun getPhone() = prefs.getString(KEY_PHONE, "") ?: ""

    fun isAdmin() = getRole() == ROLE_ADMIN
    fun isResident() = getRole() == ROLE_RESIDENT
    fun isSecurity() = getRole() == ROLE_SECURITY
    fun isCommittee() = getRole() == ROLE_COMMITTEE
    fun isTreasurer() = getRole() == ROLE_TREASURER

    // Secretary + Committee can author content (notices etc); Secretary alone owns billing/invites.
    fun canManageContent() = isAdmin() || isCommittee()

    fun updateProfile(name: String, phone: String) {
        prefs.edit().apply {
            putString(KEY_NAME, name)
            putString(KEY_PHONE, phone)
            apply()
        }
    }

    fun logout() = prefs.edit().clear().apply()
}
