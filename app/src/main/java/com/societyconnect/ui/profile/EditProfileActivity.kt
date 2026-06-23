package com.societyconnect.ui.profile

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.societyconnect.databinding.ActivityEditProfileBinding
import com.societyconnect.data.models.User
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toast

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var viewModel: EditProfileViewModel
    private lateinit var session: SessionManager
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        viewModel = ViewModelProvider(this)[EditProfileViewModel::class.java]
        viewModel.init(this)

        val flatTypes = listOf("1BHK", "2BHK", "3BHK", "SHOP")
        binding.actvFlatType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, flatTypes)
        )

        viewModel.user.observe(this) { user ->
            user?.let {
                currentUser = it
                binding.etName.setText(it.name)
                binding.etPhone.setText(it.phone)
                binding.etFlatNo.setText(it.flatNo)
                binding.actvFlatType.setText(it.flatType, false)
            }
        }

        viewModel.result.observe(this) { msg ->
            toast(msg)
            if (msg.startsWith("✅")) finish()
        }

        viewModel.loadUser(session.getUserId())

        binding.btnSave.setOnClickListener { attemptSave() }
        binding.tvCancel.setOnClickListener { finish() }
    }

    private fun attemptSave() {
        val existing = currentUser
        if (existing == null) {
            toast("Profile abhi load nahi hua, thodi der mein try karo")
            return
        }

        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val flatType = binding.actvFlatType.text.toString().trim()

        if (name.isEmpty() || phone.isEmpty() || flatType.isEmpty()) {
            toast("Please fill all fields")
            return
        }
        if (phone.length != 10) {
            toast("Enter a valid 10-digit phone number")
            return
        }

        binding.btnSave.isEnabled = false
        viewModel.updateProfile(existing, name, phone, flatType)
    }
}
