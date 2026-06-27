package com.societyconnect.ui.events

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.societyconnect.R
import com.societyconnect.data.models.Event
import com.societyconnect.data.models.Poll
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.BottomSheetAddEventBinding
import com.societyconnect.databinding.BottomSheetAddPollBinding
import com.societyconnect.databinding.BottomSheetPollVoteBinding
import com.societyconnect.databinding.FragmentEventsPollsBinding
import com.societyconnect.databinding.ItemEventBinding
import com.societyconnect.databinding.ItemPollBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Fragment ─────────────────────────────────────────────────────────
class EventsPollsFragment : Fragment() {
    private var _binding: FragmentEventsPollsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: EventsPollsViewModel
    private lateinit var session: SessionManager
    private lateinit var eventAdapter: EventAdapter
    private lateinit var pollAdapter: PollAdapter
    private var selectedEventDate = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventsPollsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[EventsPollsViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canManage = session.canManageContent()

        eventAdapter = EventAdapter(canManage = canManage, onDelete = { confirmDeleteEvent(it) })
        pollAdapter = PollAdapter(
            canManage = canManage,
            onClick = { showPollSheet(it) },
            onDelete = { confirmDeletePoll(it) }
        )

        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEvents.adapter = eventAdapter
        binding.rvPolls.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPolls.adapter = pollAdapter

        viewModel.allEvents.observe(viewLifecycleOwner) { list ->
            eventAdapter.submitList(list)
            binding.emptyEvents.visibility = if (list.isEmpty() && binding.chipEvents.isChecked) View.VISIBLE else View.GONE
        }
        viewModel.allPolls.observe(viewLifecycleOwner) { list ->
            pollAdapter.submitList(list)
            binding.emptyPolls.visibility = if (list.isEmpty() && binding.chipPolls.isChecked) View.VISIBLE else View.GONE
        }

        binding.chipGroupTab.setOnCheckedStateChangeListener { _, _ -> applyTab() }
        applyTab()

        binding.fabAdd.setOnClickListener {
            if (binding.chipEvents.isChecked) showAddEventSheet() else showAddPollSheet()
        }
    }

    private fun applyTab() {
        val showEvents = binding.chipEvents.isChecked
        binding.rvEvents.visibility = if (showEvents) View.VISIBLE else View.GONE
        binding.rvPolls.visibility = if (showEvents) View.GONE else View.VISIBLE
        binding.emptyEvents.visibility = if (showEvents && eventAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyPolls.visibility = if (!showEvents && pollAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.fabAdd.text = if (showEvents) getString(R.string.add_event) else getString(R.string.create_poll)
        binding.fabAdd.visibility = if (session.canManageContent()) View.VISIBLE else View.GONE
    }

    private fun showAddEventSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddEventBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        selectedEventDate = System.currentTimeMillis()
        sheet.tvSelectedDate.text = selectedEventDate.toDateString()

        sheet.btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                selectedEventDate = cal.timeInMillis
                sheet.tvSelectedDate.text = selectedEventDate.toDateString()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        sheet.btnSave.setOnClickListener {
            val title = sheet.etTitle.text.toString().trim()
            if (title.isEmpty()) {
                requireContext().toast("Please enter a title")
                return@setOnClickListener
            }
            viewModel.addEvent(
                Event(
                    title = title,
                    description = sheet.etDescription.text.toString().trim(),
                    location = sheet.etLocation.text.toString().trim(),
                    date = selectedEventDate,
                    postedBy = session.getName()
                )
            )
            requireContext().toast("Event added")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showAddPollSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddPollBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.btnSave.setOnClickListener {
            val question = sheet.etQuestion.text.toString().trim()
            val options = listOf(sheet.etOption1, sheet.etOption2, sheet.etOption3, sheet.etOption4)
                .map { it.text.toString().trim() }
                .filter { it.isNotEmpty() }

            if (question.isEmpty()) {
                requireContext().toast("Please enter a question")
                return@setOnClickListener
            }
            if (options.size < 2) {
                requireContext().toast("Please enter at least 2 options")
                return@setOnClickListener
            }
            viewModel.addPoll(
                Poll(
                    question = question,
                    options = options,
                    postedBy = session.getName()
                )
            )
            requireContext().toast("Poll created")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPollSheet(poll: Poll) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetPollVoteBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.tvQuestion.text = poll.question
        val canManage = session.canManageContent()
        val currentUid = session.getUserId()

        viewLifecycleOwner.lifecycleScope.launch {
            val votes = viewModel.getVotes(poll.id)
            sheet.tvVoteCount.text = "${votes.size} votes"
            val myVote = votes.find { it.id == currentUid }
            sheet.containerOptions.removeAllViews()

            if (poll.isClosed || myVote != null) {
                val counts = IntArray(poll.options.size)
                votes.forEach { if (it.optionIndex in counts.indices) counts[it.optionIndex]++ }
                poll.options.forEachIndexed { index, option ->
                    val row = android.widget.TextView(requireContext()).apply {
                        text = "$option — ${counts[index]} vote(s)"
                        textSize = 14f
                        setTextColor(requireContext().getColor(R.color.text_primary))
                        setPadding(0, 8, 0, 8)
                    }
                    sheet.containerOptions.addView(row)
                }
                sheet.btnVote.visibility = View.GONE
            } else {
                val radioGroup = RadioGroup(requireContext()).apply { orientation = RadioGroup.VERTICAL }
                poll.options.forEachIndexed { index, option ->
                    val rb = RadioButton(requireContext()).apply {
                        text = option
                        id = index
                    }
                    radioGroup.addView(rb)
                }
                sheet.containerOptions.addView(radioGroup)
                sheet.btnVote.visibility = View.VISIBLE
                sheet.btnVote.setOnClickListener {
                    val selectedIndex = radioGroup.checkedRadioButtonId
                    if (selectedIndex == -1) {
                        requireContext().toast("Please select an option")
                        return@setOnClickListener
                    }
                    viewModel.castVote(poll.id, currentUid, selectedIndex)
                    requireContext().toast("Vote recorded")
                    dialog.dismiss()
                }
            }

            sheet.btnClosePoll.visibility = if (canManage && !poll.isClosed) View.VISIBLE else View.GONE
            sheet.btnClosePoll.setOnClickListener { confirmClosePoll(poll) { dialog.dismiss() } }
        }

        dialog.show()
    }

    private fun confirmDeleteEvent(e: Event) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Event")
            .setMessage("Remove \"${e.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteEvent(e); requireContext().toast("Event deleted") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeletePoll(p: Poll) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Poll")
            .setMessage("Remove \"${p.question}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deletePoll(p); requireContext().toast("Poll deleted") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClosePoll(p: Poll, onClosed: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Close Poll")
            .setMessage("Close voting for \"${p.question}\"? This can't be undone.")
            .setPositiveButton("Close") { _, _ -> viewModel.closePoll(p); requireContext().toast("Poll closed"); onClosed() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class EventsPollsViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allEvents get() = repo.allEvents
    val allPolls get() = repo.allPolls

    fun addEvent(e: Event) = viewModelScope.launch { repo.addEvent(e) }
    fun deleteEvent(e: Event) = viewModelScope.launch { repo.deleteEvent(e) }
    fun addPoll(p: Poll) = viewModelScope.launch { repo.addPoll(p) }
    fun closePoll(p: Poll) = viewModelScope.launch { repo.closePoll(p) }
    fun deletePoll(p: Poll) = viewModelScope.launch { repo.deletePoll(p) }
    suspend fun getVotes(pollId: String) = repo.getPollVotes(pollId)
    fun castVote(pollId: String, uid: String, optionIndex: Int) =
        viewModelScope.launch { repo.castVote(pollId, uid, optionIndex) }
}

// ─── Event Adapter ────────────────────────────────────────────────────
class EventAdapter(
    private val canManage: Boolean,
    private val onDelete: (Event) -> Unit
) : ListAdapter<Event, EventAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.title
            tvDate.text = item.date.toDateString()
            tvLocation.text = "📍 ${item.location}"
            tvLocation.visibility = if (item.location.isBlank()) View.GONE else View.VISIBLE
            tvDescription.text = item.description
            tvDescription.visibility = if (item.description.isBlank()) View.GONE else View.VISIBLE

            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Event>() {
            override fun areItemsTheSame(a: Event, b: Event) = a.id == b.id
            override fun areContentsTheSame(a: Event, b: Event) = a == b
        }
    }
}

// ─── Poll Adapter ────────────────────────────────────────────────────
class PollAdapter(
    private val canManage: Boolean,
    private val onClick: (Poll) -> Unit,
    private val onDelete: (Poll) -> Unit
) : ListAdapter<Poll, PollAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemPollBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPollBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvQuestion.text = item.question
            if (item.isClosed) {
                tvStatus.text = root.context.getString(R.string.poll_closed)
                tvStatusDot.setBackgroundResource(R.drawable.dot_red)
                tvHint.text = "Tap to view results"
            } else {
                tvStatus.text = root.context.getString(R.string.poll_open)
                tvStatusDot.setBackgroundResource(R.drawable.dot_green)
                tvHint.text = "Tap to vote"
            }
            root.setOnClickListener { onClick(item) }

            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Poll>() {
            override fun areItemsTheSame(a: Poll, b: Poll) = a.id == b.id
            override fun areContentsTheSame(a: Poll, b: Poll) = a == b
        }
    }
}
