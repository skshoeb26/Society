package com.societyconnect.ui.profile

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.societyconnect.data.models.User
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.utils.SessionManager
import kotlinx.coroutines.launch

class EditProfileViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    private lateinit var session: SessionManager

    fun init(context: Context) {
        if (!::repo.isInitialized) repo = SocietyRepository(context)
        if (!::session.isInitialized) session = SessionManager(context)
    }

    val user = MutableLiveData<User?>()
    val result = MutableLiveData<String>()

    fun loadUser(userId: Int) = viewModelScope.launch {
        user.postValue(repo.getUserById(userId))
    }

    fun updateProfile(existing: User, name: String, phone: String, flatType: String) = viewModelScope.launch {
        repo.updateProfile(existing.copy(name = name, phone = phone, flatType = flatType))
        session.saveSession(existing.id, name, existing.flatNo, existing.role, existing.societyName, phone)
        result.postValue("✅ Profile updated successfully")
    }
}
