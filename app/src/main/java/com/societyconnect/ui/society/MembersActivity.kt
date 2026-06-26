package com.societyconnect.ui.society

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.societyconnect.data.models.User
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.ActivityMembersBinding
import com.societyconnect.databinding.ItemMemberBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getRoleLabel
import com.societyconnect.utils.makeCall
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

class MembersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMembersBinding
    private lateinit var repo: SocietyRepository
    private lateinit var session: SessionManager
    private lateinit var adapter: MembersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMembersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        repo = SocietyRepository(this)

        if (!session.isAdmin()) {
            toast("Only the secretary can view the members directory")
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        adapter = MembersAdapter(
            currentUserId = session.getUserId(),
            onCall = { makeCall(it.phone) },
            onRemove = { confirmRemove(it) }
        )
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = adapter

        repo.getMembersBySociety(session.getSociety()).observe(this) { list ->
            adapter.submitList(list)
            binding.tvCount.text = "${list.size} member${if (list.size == 1) "" else "s"}"
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmRemove(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Remove Member")
            .setMessage("Remove ${user.name} (Flat ${user.flatNo}) from ${session.getSociety()}? They'll need a new invite code to rejoin.")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    repo.removeMember(user)
                    runOnUiThread { toast("${user.name} removed") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class MembersAdapter(
    private val currentUserId: Int,
    private val onCall: (User) -> Unit,
    private val onRemove: (User) -> Unit
) : ListAdapter<User, MembersAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvInitial.text = item.name.trim().take(1).uppercase().ifEmpty { "?" }
            tvName.text = item.name
            tvFlatRole.text = "Flat ${item.flatNo} · ${getRoleLabel(item.role)}"
            btnCall.setOnClickListener { onCall(item) }
            btnRemove.visibility = if (item.id != currentUserId) View.VISIBLE else View.GONE
            btnRemove.setOnClickListener { onRemove(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(a: User, b: User) = a.id == b.id
            override fun areContentsTheSame(a: User, b: User) = a == b
        }
    }
}
