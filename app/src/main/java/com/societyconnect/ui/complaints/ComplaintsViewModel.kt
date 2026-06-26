package com.societyconnect.ui.complaints

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.societyconnect.R
import com.societyconnect.databinding.ItemComplaintBinding
import com.societyconnect.data.models.Complaint
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.utils.getCategoryLabel
import com.societyconnect.utils.getStatusColor
import com.societyconnect.utils.getStatusLabel
import com.societyconnect.utils.toDateString
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────
class ComplaintsViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository

    fun init(societyId: String) {
        if (!::repo.isInitialized) repo = SocietyRepository(societyId)
    }

    val allComplaints get() = repo.allComplaints
    fun getByFlat(flatNo: String) = repo.getComplaintsByFlat(flatNo)
    fun getByStatus(status: String) = repo.getComplaintsByStatus(status)

    fun addComplaint(c: Complaint) = viewModelScope.launch { repo.addComplaint(c) }
    fun updateComplaint(c: Complaint) = viewModelScope.launch { repo.updateComplaint(c) }
    fun deleteComplaint(c: Complaint) = viewModelScope.launch { repo.deleteComplaint(c) }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class ComplaintsAdapter(
    private val isAdmin: Boolean,
    private val onUpdateStatus: (Complaint) -> Unit,
    private val onDelete: (Complaint) -> Unit
) : ListAdapter<Complaint, ComplaintsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemComplaintBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemComplaintBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.binding.root.context
        with(holder.binding) {
            tvTitle.text = item.title
            tvCategory.text = getCategoryLabel(item.category)
            tvFlat.text = "Flat ${item.flatNo} · ${item.raisedBy}"
            tvDescription.text = item.description
            tvDate.text = item.createdAt.toDateString()
            tvStatus.text = getStatusLabel(item.status)
            tvStatus.setTextColor(getStatusColor(item.status, ctx))

            if (item.adminComment.isNotEmpty()) {
                tvAdminComment.visibility = View.VISIBLE
                tvAdminComment.text = "Admin: ${item.adminComment}"
            } else {
                tvAdminComment.visibility = View.GONE
            }

            btnUpdateStatus.visibility = if (isAdmin) View.VISIBLE else View.GONE
            btnDelete.visibility = if (isAdmin) View.VISIBLE else View.GONE

            btnUpdateStatus.setOnClickListener { onUpdateStatus(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Complaint>() {
            override fun areItemsTheSame(a: Complaint, b: Complaint) = a.id == b.id
            override fun areContentsTheSame(a: Complaint, b: Complaint) = a == b
        }
    }
}
