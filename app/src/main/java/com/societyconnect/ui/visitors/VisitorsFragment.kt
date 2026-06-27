package com.societyconnect.ui.visitors

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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.societyconnect.R
import com.societyconnect.databinding.FragmentVisitorsBinding
import com.societyconnect.databinding.BottomSheetAddVisitorBinding
import com.societyconnect.databinding.BottomSheetInviteGuestBinding
import com.societyconnect.databinding.DialogVisitorQrBinding
import com.societyconnect.databinding.ItemVisitorBinding
import com.societyconnect.data.models.Visitor
import com.societyconnect.data.models.withId
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.generateQrBitmap
import com.societyconnect.utils.parseVisitorQrContent
import com.societyconnect.utils.toast
import com.societyconnect.utils.toDateTimeString
import com.societyconnect.utils.visitorQrContent
import kotlinx.coroutines.launch

// ─── Fragment ─────────────────────────────────────────────────────────
class VisitorsFragment : Fragment() {
    private var _binding: FragmentVisitorsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VisitorsViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: VisitorsAdapter

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleScannedCode(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVisitorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[VisitorsViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canCheckOut = session.isSecurity() || session.isAdmin()
        adapter = VisitorsAdapter(
            canCheckOut = canCheckOut,
            canDecide = { v ->
                session.isAdmin() ||
                    ((session.isResident() || session.isCommittee()) && v.visitingFlat == session.getFlatNo())
            },
            onCheckOut = { viewModel.checkOut(it); requireContext().toast("${it.visitorName} checked out") },
            onApprove = { viewModel.approve(it); requireContext().toast("Visitor approved") },
            onDeny = { viewModel.deny(it); requireContext().toast("Visitor denied") }
        )

        binding.rvVisitors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVisitors.adapter = adapter

        val ownFlatOnly = session.isResident() || session.isCommittee()
        val liveData = if (ownFlatOnly) viewModel.getByFlat(session.getFlatNo()) else viewModel.allVisitors

        liveData.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.todayCount.observe(viewLifecycleOwner) {
            binding.tvTodayCount.text = "Today: $it visitors"
        }

        val canLog = session.isSecurity() || session.isAdmin()
        val canInvite = session.isResident() || session.isCommittee()
        val canScan = session.isSecurity() || session.isAdmin()

        binding.fabAdd.visibility = if (canLog || canInvite) View.VISIBLE else View.GONE
        binding.fabAdd.text = if (canLog) "Log Visitor" else "Invite Guest"
        binding.fabAdd.setOnClickListener { if (canLog) showAddSheet() else showInviteSheet() }

        binding.fabScan.visibility = if (canScan) View.VISIBLE else View.GONE
        binding.fabScan.setOnClickListener { launchScanner() }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddVisitorBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.btnLog.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            val flat = sheet.etFlat.text.toString().trim().uppercase()
            val purpose = sheet.etPurpose.text.toString().trim()
            val vehicle = sheet.etVehicle.text.toString().trim()

            if (name.isEmpty() || flat.isEmpty() || purpose.isEmpty()) {
                requireContext().toast("Please fill required fields")
                return@setOnClickListener
            }

            viewModel.addVisitor(
                Visitor(
                    visitorName = name,
                    visitingFlat = flat,
                    purpose = purpose,
                    vehicleNo = vehicle,
                    loggedBy = session.getName(),
                    checkIn = System.currentTimeMillis()
                )
            )
            requireContext().toast("Visitor logged for Flat $flat")
            dialog.dismiss()
        }

        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showInviteSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetInviteGuestBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.btnInvite.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            val purpose = sheet.etPurpose.text.toString().trim()
            val vehicle = sheet.etVehicle.text.toString().trim()

            if (name.isEmpty() || purpose.isEmpty()) {
                requireContext().toast("Please fill required fields")
                return@setOnClickListener
            }

            viewModel.inviteGuest(
                Visitor(
                    visitorName = name,
                    visitingFlat = session.getFlatNo(),
                    purpose = purpose,
                    vehicleNo = vehicle,
                    loggedBy = session.getName(),
                    status = "APPROVED",
                    preApproved = true,
                    checkIn = null
                )
            ) { created -> showQrDialog(created) }
            dialog.dismiss()
        }

        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showQrDialog(visitor: Visitor) {
        val qrBinding = DialogVisitorQrBinding.inflate(layoutInflater)
        qrBinding.tvGuestName.text = "QR Code for ${visitor.visitorName}"
        qrBinding.ivQrCode.setImageBitmap(
            generateQrBitmap(visitorQrContent(session.getSocietyId(), visitor.id))
        )

        val dialog = AlertDialog.Builder(requireContext()).setView(qrBinding.root).create()
        qrBinding.btnDone.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan the guest's QR code")
            setBeepEnabled(true)
            setOrientationLocked(true)
        }
        qrScanLauncher.launch(options)
    }

    private fun handleScannedCode(content: String) {
        val parsed = parseVisitorQrContent(content)
        if (parsed == null) {
            requireContext().toast("Invalid QR code")
            return
        }
        val (societyId, visitorId) = parsed
        if (societyId != session.getSocietyId()) {
            requireContext().toast("This QR code is not for your society")
            return
        }
        viewModel.checkInByQr(visitorId) { message -> requireContext().toast(message) }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class VisitorsViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allVisitors get() = repo.allVisitors
    val todayCount get() = repo.todayVisitorCount
    fun getByFlat(flat: String) = repo.getVisitorsByFlat(flat)
    fun addVisitor(v: Visitor) = viewModelScope.launch { repo.addVisitor(v) }
    fun checkOut(v: Visitor) = viewModelScope.launch {
        repo.updateVisitor(v.copy(checkOut = System.currentTimeMillis()).withId(v.id))
    }
    fun approve(v: Visitor) = viewModelScope.launch { repo.updateVisitor(v.copy(status = "APPROVED").withId(v.id)) }
    fun deny(v: Visitor) = viewModelScope.launch { repo.updateVisitor(v.copy(status = "DENIED").withId(v.id)) }

    fun inviteGuest(v: Visitor, onCreated: (Visitor) -> Unit) = viewModelScope.launch {
        val newId = repo.addVisitor(v)
        onCreated(v.also { it.id = newId })
    }

    fun checkInByQr(visitorId: String, onResult: (String) -> Unit) = viewModelScope.launch {
        val visitor = repo.getVisitor(visitorId)
        when {
            visitor == null -> onResult("Invalid QR code")
            visitor.checkIn != null -> onResult("${visitor.visitorName} is already checked in")
            visitor.status != "APPROVED" -> onResult("This guest is not approved")
            else -> {
                repo.updateVisitor(visitor.copy(checkIn = System.currentTimeMillis()).withId(visitor.id))
                onResult("Welcome ${visitor.visitorName}! Checked in for Flat ${visitor.visitingFlat}")
            }
        }
    }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class VisitorsAdapter(
    private val canCheckOut: Boolean,
    private val canDecide: (Visitor) -> Boolean,
    private val onCheckOut: (Visitor) -> Unit,
    private val onApprove: (Visitor) -> Unit,
    private val onDeny: (Visitor) -> Unit
) : ListAdapter<Visitor, VisitorsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemVisitorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVisitorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvVisitorName.text = item.visitorName
            tvFlat.text = "Flat ${item.visitingFlat}"
            tvPurpose.text = item.purpose

            if (item.vehicleNo.isNotEmpty()) {
                tvVehicle.visibility = View.VISIBLE
                tvVehicle.text = "🚗 ${item.vehicleNo}"
            } else {
                tvVehicle.visibility = View.GONE
            }

            when (item.status) {
                "PENDING" -> { tvStatus.visibility = View.VISIBLE; tvStatus.text = "⏳ Awaiting approval" }
                "DENIED" -> { tvStatus.visibility = View.VISIBLE; tvStatus.text = "✕ Denied" }
                else -> tvStatus.visibility = View.GONE
            }

            layoutDecision.visibility =
                if (item.status == "PENDING" && canDecide(item)) View.VISIBLE else View.GONE
            btnApprove.setOnClickListener { onApprove(item) }
            btnDeny.setOnClickListener { onDeny(item) }

            when {
                item.checkIn == null -> {
                    tvCheckIn.text = if (item.preApproved) "🕐 Awaiting arrival" else "Not checked in"
                    tvCheckOut.visibility = View.GONE
                    btnCheckOut.visibility = View.GONE
                    tvStatusDot.setBackgroundResource(R.drawable.dot_grey)
                }
                item.checkOut != null -> {
                    tvCheckIn.text = "In: ${item.checkIn.toDateTimeString()}"
                    tvCheckOut.visibility = View.VISIBLE
                    tvCheckOut.text = "Out: ${item.checkOut.toDateTimeString()}"
                    btnCheckOut.visibility = View.GONE
                    tvStatusDot.setBackgroundResource(R.drawable.dot_grey)
                }
                else -> {
                    tvCheckIn.text = "In: ${item.checkIn.toDateTimeString()}"
                    tvCheckOut.visibility = View.GONE
                    btnCheckOut.visibility =
                        if (canCheckOut && item.status == "APPROVED") View.VISIBLE else View.GONE
                    tvStatusDot.setBackgroundResource(R.drawable.dot_green)
                }
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Visitor>() {
            override fun areItemsTheSame(a: Visitor, b: Visitor) = a.id == b.id
            override fun areContentsTheSame(a: Visitor, b: Visitor) = a == b
        }
    }
}
