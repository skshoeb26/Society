package com.societyconnect.ui.emergency

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.societyconnect.databinding.FragmentEmergencyBinding
import com.societyconnect.databinding.BottomSheetAddContactBinding
import com.societyconnect.databinding.ItemEmergencyContactBinding
import com.societyconnect.data.models.EmergencyContact
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getContactTypeIcon
import com.societyconnect.utils.makeCall
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

// ─── Fragment ─────────────────────────────────────────────────────────
class EmergencyFragment : Fragment() {
    private var _binding: FragmentEmergencyBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: EmergencyViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: EmergencyAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmergencyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[EmergencyViewModel::class.java]
        viewModel.init(requireContext())

        adapter = EmergencyAdapter(
            isAdmin = session.isAdmin(),
            onCall = { requireContext().makeCall(it.phone) },
            onDelete = { confirmDelete(it) }
        )

        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter

        viewModel.allContacts.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabAdd.visibility = if (session.isAdmin()) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddContactBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        val types = listOf("PLUMBER", "ELECTRICIAN", "SECURITY", "MEDICAL", "FIRE", "POLICE", "OTHER")
        var selectedType = types[0]
        val typeAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        sheet.actvType.setAdapter(typeAdapter)
        sheet.actvType.setText(selectedType, false)
        sheet.actvType.setOnItemClickListener { _, _, pos, _ -> selectedType = types[pos] }

        sheet.btnSave.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            val phone = sheet.etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                requireContext().toast("Please fill all fields")
                return@setOnClickListener
            }
            viewModel.addContact(EmergencyContact(name = name, phone = phone, type = selectedType))
            requireContext().toast("Contact added")
            dialog.dismiss()
        }

        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmDelete(c: EmergencyContact) {
        if (c.isDefault) {
            requireContext().toast("Default contacts cannot be deleted")
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Contact")
            .setMessage("Delete ${c.name}?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteContact(c) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class EmergencyViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(context: Context) { if (!::repo.isInitialized) repo = SocietyRepository(context) }

    val allContacts get() = repo.allEmergencyContacts
    fun addContact(c: EmergencyContact) = viewModelScope.launch { repo.addEmergencyContact(c) }
    fun deleteContact(c: EmergencyContact) = viewModelScope.launch { repo.deleteEmergencyContact(c) }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class EmergencyAdapter(
    private val isAdmin: Boolean,
    private val onCall: (EmergencyContact) -> Unit,
    private val onDelete: (EmergencyContact) -> Unit
) : ListAdapter<EmergencyContact, EmergencyAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemEmergencyContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEmergencyContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvIcon.text = getContactTypeIcon(item.type)
            tvName.text = item.name
            tvPhone.text = item.phone
            tvType.text = item.type.replace("_", " ")
            btnCall.setOnClickListener { onCall(item) }
            btnDelete.visibility = if (isAdmin && !item.isDefault) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<EmergencyContact>() {
            override fun areItemsTheSame(a: EmergencyContact, b: EmergencyContact) = a.id == b.id
            override fun areContentsTheSame(a: EmergencyContact, b: EmergencyContact) = a == b
        }
    }
}
