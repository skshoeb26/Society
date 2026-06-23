package com.societyconnect.ui.maintenance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.societyconnect.R
import com.societyconnect.databinding.ItemMaintenanceBinding
import com.societyconnect.data.models.Maintenance
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toRupees

class MaintenanceAdapter(
    private val isAdmin: Boolean,
    private val onTogglePaid: (Maintenance) -> Unit,
    private val onDelete: (Maintenance) -> Unit
) : ListAdapter<Maintenance, MaintenanceAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemMaintenanceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMaintenanceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.binding.root.context
        with(holder.binding) {
            tvFlat.text = "Flat ${item.flatNo}"
            tvName.text = item.residentName
            tvMonth.text = item.month
            tvAmount.text = item.amount.toRupees()
            tvDueDate.text = "Due: ${item.dueDate.toDateString()}"

            // Status badge
            if (item.isPaid) {
                tvStatus.text = "PAID"
                tvStatus.setTextColor(ctx.getColor(R.color.status_paid))
                tvStatus.setBackgroundResource(R.drawable.bg_status_paid)
                tvPaidOn.visibility = View.VISIBLE
                tvPaidOn.text = "Paid on ${item.paidOn?.toDateString()}"
            } else {
                tvStatus.text = "PENDING"
                tvStatus.setTextColor(ctx.getColor(R.color.status_pending))
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
                tvPaidOn.visibility = View.GONE
            }

            // Admin controls
            btnToggle.visibility = if (isAdmin) View.VISIBLE else View.GONE
            btnDelete.visibility = if (isAdmin) View.VISIBLE else View.GONE

            btnToggle.text = if (item.isPaid) "Mark Pending" else "Mark Paid"
            btnToggle.setOnClickListener { onTogglePaid(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Maintenance>() {
            override fun areItemsTheSame(a: Maintenance, b: Maintenance) = a.id == b.id
            override fun areContentsTheSame(a: Maintenance, b: Maintenance) = a == b
        }
    }
}
