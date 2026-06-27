package com.societyconnect.ui.agm

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.TextView
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
import com.societyconnect.data.models.AgmMeeting
import com.societyconnect.data.models.AgmRsvp
import com.societyconnect.data.models.withId
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.BottomSheetAddAgmBinding
import com.societyconnect.databinding.BottomSheetAgmDetailBinding
import com.societyconnect.databinding.FragmentAgmBinding
import com.societyconnect.databinding.ItemAgmBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Fragment ─────────────────────────────────────────────────────────
class AgmFragment : Fragment() {
    private var _binding: FragmentAgmBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AgmViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: AgmAdapter
    private var selectedDate = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAgmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[AgmViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canManage = session.canManageContent()
        adapter = AgmAdapter(canManage = canManage, onClick = { showDetailSheet(it) }, onDelete = { confirmDelete(it) })

        binding.rvAgm.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAgm.adapter = adapter

        viewModel.allMeetings.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.visibility = if (canManage) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddAgmBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        selectedDate = System.currentTimeMillis()
        sheet.tvSelectedDate.text = selectedDate.toDateString()

        sheet.btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                selectedDate = cal.timeInMillis
                sheet.tvSelectedDate.text = selectedDate.toDateString()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        sheet.btnSave.setOnClickListener {
            val title = sheet.etTitle.text.toString().trim()
            if (title.isEmpty()) {
                requireContext().toast("Please enter a title")
                return@setOnClickListener
            }
            val agenda = sheet.etAgenda.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            viewModel.addMeeting(
                AgmMeeting(
                    title = title,
                    date = selectedDate,
                    time = sheet.etTime.text.toString().trim(),
                    venue = sheet.etVenue.text.toString().trim(),
                    agenda = agenda,
                    createdBy = session.getName()
                )
            )
            requireContext().toast("AGM scheduled")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDetailSheet(meeting: AgmMeeting) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAgmDetailBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.tvTitle.text = meeting.title
        sheet.tvDate.text = meeting.date.toDateString()
        sheet.tvTime.text = "🕒 ${meeting.time}"
        sheet.tvTime.visibility = if (meeting.time.isBlank()) View.GONE else View.VISIBLE
        sheet.tvVenue.text = "📍 ${meeting.venue}"
        sheet.tvVenue.visibility = if (meeting.venue.isBlank()) View.GONE else View.VISIBLE

        sheet.containerAgenda.removeAllViews()
        meeting.agenda.forEach { point ->
            val row = TextView(requireContext()).apply {
                text = "•  $point"
                textSize = 14f
                setTextColor(requireContext().getColor(R.color.text_primary))
                setPadding(0, 4, 0, 4)
            }
            sheet.containerAgenda.addView(row)
        }

        val canManage = session.canManageContent()
        val currentUid = session.getUserId()
        sheet.rsvpSection.visibility = if (meeting.status == "COMPLETED") View.GONE else View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val rsvps = viewModel.getRsvps(meeting.id)
            val attendingCount = rsvps.count { it.attending }
            sheet.tvRsvpCount.text = "$attendingCount attending"
            val myRsvp = rsvps.find { it.id == currentUid }
            sheet.tvMyRsvp.visibility = if (myRsvp == null) View.GONE else View.VISIBLE
            sheet.tvMyRsvp.text = if (myRsvp?.attending == true) "You're attending ✅" else if (myRsvp != null) "You declined ❌" else ""

            sheet.btnRsvpYes.setOnClickListener {
                viewModel.setRsvp(meeting.id, currentUid, AgmRsvp(name = session.getName(), flatNo = session.getFlatNo(), attending = true))
                requireContext().toast("RSVP saved")
                dialog.dismiss()
            }
            sheet.btnRsvpNo.setOnClickListener {
                viewModel.setRsvp(meeting.id, currentUid, AgmRsvp(name = session.getName(), flatNo = session.getFlatNo(), attending = false))
                requireContext().toast("RSVP saved")
                dialog.dismiss()
            }
        }

        sheet.minutesSection.visibility = if (canManage || meeting.minutes.isNotBlank()) View.VISIBLE else View.GONE
        sheet.etMinutes.setText(meeting.minutes)
        sheet.etMinutes.isEnabled = canManage
        sheet.btnSaveMinutes.visibility = if (canManage) View.VISIBLE else View.GONE
        sheet.btnSaveMinutes.setOnClickListener {
            viewModel.updateMeeting(meeting.copy(minutes = sheet.etMinutes.text.toString().trim()).withId(meeting.id))
            requireContext().toast("Minutes saved")
            dialog.dismiss()
        }
        sheet.btnMarkCompleted.visibility = if (canManage && meeting.status == "UPCOMING") View.VISIBLE else View.GONE
        sheet.btnMarkCompleted.setOnClickListener {
            viewModel.updateMeeting(meeting.copy(status = "COMPLETED").withId(meeting.id))
            requireContext().toast("Marked as completed")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmDelete(m: AgmMeeting) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete AGM")
            .setMessage("Remove \"${m.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteMeeting(m); requireContext().toast("AGM removed") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class AgmViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allMeetings get() = repo.allAgmMeetings

    fun addMeeting(m: AgmMeeting) = viewModelScope.launch { repo.addAgmMeeting(m) }
    fun updateMeeting(m: AgmMeeting) = viewModelScope.launch { repo.updateAgmMeeting(m) }
    fun deleteMeeting(m: AgmMeeting) = viewModelScope.launch { repo.deleteAgmMeeting(m) }
    suspend fun getRsvps(agmId: String) = repo.getAgmRsvps(agmId)
    fun setRsvp(agmId: String, uid: String, rsvp: AgmRsvp) = viewModelScope.launch { repo.setAgmRsvp(agmId, uid, rsvp) }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class AgmAdapter(
    private val canManage: Boolean,
    private val onClick: (AgmMeeting) -> Unit,
    private val onDelete: (AgmMeeting) -> Unit
) : ListAdapter<AgmMeeting, AgmAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAgmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAgmBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.title
            tvDate.text = item.date.toDateString()
            tvVenue.text = "📍 ${item.venue}"
            tvVenue.visibility = if (item.venue.isBlank()) View.GONE else View.VISIBLE

            val isCompleted = item.status == "COMPLETED"
            tvStatus.text = root.context.getString(if (isCompleted) R.string.agm_completed else R.string.agm_upcoming)
            statusDot.setBackgroundResource(if (isCompleted) com.societyconnect.R.drawable.dot_red else com.societyconnect.R.drawable.dot_green)

            root.setOnClickListener { onClick(item) }
            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AgmMeeting>() {
            override fun areItemsTheSame(a: AgmMeeting, b: AgmMeeting) = a.id == b.id
            override fun areContentsTheSame(a: AgmMeeting, b: AgmMeeting) = a == b
        }
    }
}
