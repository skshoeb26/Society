package com.societyconnect.ui.notices

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
import com.societyconnect.databinding.FragmentNoticesBinding
import com.societyconnect.databinding.BottomSheetAddNoticeBinding
import com.societyconnect.databinding.ItemNoticeBinding
import com.societyconnect.data.models.Notice
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toast
import com.societyconnect.utils.toDateString
import kotlinx.coroutines.launch

// ─── Fragment ─────────────────────────────────────────────────────────
class NoticesFragment : Fragment() {
    private var _binding: FragmentNoticesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NoticesViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: NoticesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNoticesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[NoticesViewModel::class.java]
        viewModel.init(session.getSocietyId())

        adapter = NoticesAdapter(
            isAdmin = session.canManageContent(),
            onPin = { viewModel.togglePin(it) },
            onDelete = { confirmDelete(it) }
        )

        binding.rvNotices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotices.adapter = adapter

        viewModel.allNotices.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.visibility = if (session.canManageContent()) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddNoticeBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.btnPost.setOnClickListener {
            val title = sheet.etTitle.text.toString().trim()
            val content = sheet.etContent.text.toString().trim()
            if (title.isEmpty() || content.isEmpty()) {
                requireContext().toast("Please fill all fields")
                return@setOnClickListener
            }
            viewModel.addNotice(
                Notice(
                    title = title,
                    content = content,
                    postedBy = session.getName(),
                    isPinned = sheet.switchPin.isChecked
                )
            )
            requireContext().toast("Notice posted")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmDelete(n: Notice) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Notice")
            .setMessage("Delete \"${n.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteNotice(n) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class NoticesViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allNotices get() = repo.allNotices
    fun addNotice(n: Notice) = viewModelScope.launch { repo.addNotice(n) }
    fun togglePin(n: Notice) = viewModelScope.launch { repo.updateNotice(n.copy(isPinned = !n.isPinned)) }
    fun deleteNotice(n: Notice) = viewModelScope.launch { repo.deleteNotice(n) }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class NoticesAdapter(
    private val isAdmin: Boolean,
    private val onPin: (Notice) -> Unit,
    private val onDelete: (Notice) -> Unit
) : ListAdapter<Notice, NoticesAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemNoticeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNoticeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.title
            tvContent.text = item.content
            tvPostedBy.text = "Posted by ${item.postedBy}"
            tvDate.text = item.createdAt.toDateString()
            ivPin.visibility = if (item.isPinned) View.VISIBLE else View.GONE
            btnPin.visibility = if (isAdmin) View.VISIBLE else View.GONE
            btnDelete.visibility = if (isAdmin) View.VISIBLE else View.GONE
            btnPin.text = if (item.isPinned) "Unpin" else "Pin"
            btnPin.setOnClickListener { onPin(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Notice>() {
            override fun areItemsTheSame(a: Notice, b: Notice) = a.id == b.id
            override fun areContentsTheSame(a: Notice, b: Notice) = a == b
        }
    }
}
