package com.societyconnect.ui.profile

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.societyconnect.data.firebase.AuthRepository
import com.societyconnect.data.firebase.UserProfile
import com.societyconnect.utils.SessionManager
import kotlinx.coroutines.launch

class EditProfileViewModel : ViewModel() {
    private val authRepo = AuthRepository()
    private var session: SessionManager? = null

    fun init(context: Context) {
        if (session == null) session = SessionManager(context)
    }

    val profile = MutableLiveData<UserProfile?>()
    val result = MutableLiveData<String>()

    fun loadProfile() = viewModelScope.launch {
        profile.postValue(authRepo.getMyProfile())
    }

    fun updateProfile(name: String, phone: String, flatType: String) = viewModelScope.launch {
        try {
            authRepo.updateProfile(name, phone, flatType)
            session?.updateProfile(name, phone)
            result.postValue("✅ Profile updated successfully")
        } catch (e: Exception) {
            result.postValue("❌ ${e.message ?: "Update failed"}")
        }
    }
}
