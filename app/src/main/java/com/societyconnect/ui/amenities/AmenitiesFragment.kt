package com.societyconnect.ui.amenities

import android.app.DatePickerDialog
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
import com.google.android.material.chip.Chip
import com.societyconnect.R
import com.societyconnect.data.models.Amenity
import com.societyconnect.data.models.Booking
import com.societyconnect.data.models.withId
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.BottomSheetAddAmenityBinding
import com.societyconnect.databinding.BottomSheetBookAmenityBinding
import com.societyconnect.databinding.FragmentAmenitiesBinding
import com.societyconnect.databinding.ItemAmenityBinding
import com.societyconnect.databinding.ItemBookingBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getAmenityIcon
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Fragment ─────────────────────────────────────────────────────────
class AmenitiesFragment : Fragment() {
    private var _binding: FragmentAmenitiesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AmenitiesViewModel
    private lateinit var session: SessionManager
    private lateinit var amenityAdapter: AmenityAdapter
    private lateinit var bookingAdapter: BookingAdapter
    private var selectedBookingDate = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAmenitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[AmenitiesViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canManage = session.canManageContent()
        val currentUid = session.getUserId()

        amenityAdapter = AmenityAdapter(
            canManage = canManage,
            onBook = { showBookSheet(it) },
            onDelete = { confirmDeleteAmenity(it) }
        )
        bookingAdapter = BookingAdapter(
            canManage = canManage,
            currentUid = currentUid,
            onApprove = { viewModel.updateBooking(it.copy(status = "APPROVED").withId(it.id)); requireContext().toast("Booking approved") },
            onDeny = { viewModel.updateBooking(it.copy(status = "DENIED").withId(it.id)); requireContext().toast("Booking denied") },
            onCancel = { confirmCancelBooking(it) }
        )

        binding.rvAmenities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAmenities.adapter = amenityAdapter
        binding.rvBookings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBookings.adapter = bookingAdapter

        viewModel.allAmenities.observe(viewLifecycleOwner) { list ->
            amenityAdapter.submitList(list)
            binding.emptyAmenities.visibility = if (list.isEmpty() && binding.chipAmenities.isChecked) View.VISIBLE else View.GONE
        }
        viewModel.allBookings.observe(viewLifecycleOwner) { list ->
            bookingAdapter.submitList(list)
            binding.emptyBookings.visibility = if (list.isEmpty() && binding.chipBookings.isChecked) View.VISIBLE else View.GONE
        }

        binding.chipGroupTab.setOnCheckedStateChangeListener { _, _ -> applyTab() }
        applyTab()

        binding.fabAdd.setOnClickListener { showAddAmenitySheet() }
    }

    private fun applyTab() {
        val showAmenities = binding.chipAmenities.isChecked
        binding.rvAmenities.visibility = if (showAmenities) View.VISIBLE else View.GONE
        binding.rvBookings.visibility = if (showAmenities) View.GONE else View.VISIBLE
        binding.emptyAmenities.visibility = if (showAmenities && amenityAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyBookings.visibility = if (!showAmenities && bookingAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.fabAdd.visibility = if (showAmenities && session.canManageContent()) View.VISIBLE else View.GONE
    }

    private fun showAddAmenitySheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddAmenityBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        var selectedCategory = "CLUBHOUSE"
        sheet.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            selectedCategory = chip.tag.toString()
        }

        sheet.btnSave.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            if (name.isEmpty()) {
                requireContext().toast("Please enter a name")
                return@setOnClickListener
            }
            viewModel.addAmenity(
                Amenity(
                    name = name,
                    category = selectedCategory,
                    description = sheet.etDescription.text.toString().trim(),
                    addedBy = session.getName()
                )
            )
            requireContext().toast("Amenity added")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showBookSheet(amenity: Amenity) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetBookAmenityBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.tvAmenityName.text = amenity.name
        selectedBookingDate = System.currentTimeMillis()
        sheet.tvSelectedDate.text = selectedBookingDate.toDateString()

        sheet.btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                selectedBookingDate = cal.timeInMillis
                sheet.tvSelectedDate.text = selectedBookingDate.toDateString()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        sheet.btnSave.setOnClickListener {
            val slot = sheet.etSlot.text.toString().trim()
            if (slot.isEmpty()) {
                requireContext().toast("Please enter a time slot")
                return@setOnClickListener
            }
            viewModel.addBooking(
                Booking(
                    amenityId = amenity.id,
                    amenityName = amenity.name,
                    flatNo = session.getFlatNo(),
                    bookedBy = session.getName(),
                    bookedByUid = session.getUserId(),
                    date = selectedBookingDate,
                    slot = slot
                )
            )
            requireContext().toast("Booking requested")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmDeleteAmenity(a: Amenity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Amenity")
            .setMessage("Remove \"${a.name}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteAmenity(a); requireContext().toast("Amenity removed") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmCancelBooking(b: Booking) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Booking")
            .setMessage("Cancel your booking for \"${b.amenityName}\"?")
            .setPositiveButton("Yes") { _, _ -> viewModel.updateBooking(b.copy(status = "CANCELLED").withId(b.id)); requireContext().toast("Booking cancelled") }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class AmenitiesViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allAmenities get() = repo.allAmenities
    val allBookings get() = repo.allBookings

    fun addAmenity(a: Amenity) = viewModelScope.launch { repo.addAmenity(a) }
    fun deleteAmenity(a: Amenity) = viewModelScope.launch { repo.deleteAmenity(a) }
    fun addBooking(b: Booking) = viewModelScope.launch { repo.addBooking(b) }
    fun updateBooking(b: Booking) = viewModelScope.launch { repo.updateBooking(b) }
}

// ─── Amenity Adapter ────────────────────────────────────────────────────
class AmenityAdapter(
    private val canManage: Boolean,
    private val onBook: (Amenity) -> Unit,
    private val onDelete: (Amenity) -> Unit
) : ListAdapter<Amenity, AmenityAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAmenityBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAmenityBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvIcon.text = getAmenityIcon(item.category)
            tvName.text = item.name
            tvDescription.text = item.description
            tvDescription.visibility = if (item.description.isBlank()) View.GONE else View.VISIBLE

            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
            btnBook.setOnClickListener { onBook(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Amenity>() {
            override fun areItemsTheSame(a: Amenity, b: Amenity) = a.id == b.id
            override fun areContentsTheSame(a: Amenity, b: Amenity) = a == b
        }
    }
}

// ─── Booking Adapter ────────────────────────────────────────────────────
class BookingAdapter(
    private val canManage: Boolean,
    private val currentUid: String,
    private val onApprove: (Booking) -> Unit,
    private val onDeny: (Booking) -> Unit,
    private val onCancel: (Booking) -> Unit
) : ListAdapter<Booking, BookingAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemBookingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvAmenityName.text = item.amenityName
            tvFlat.text = "Flat ${item.flatNo}"
            tvBookedBy.text = "Booked by ${item.bookedBy}"
            tvDateSlot.text = "${item.date.toDateString()} · ${item.slot}"

            val context = root.context
            when (item.status) {
                "APPROVED" -> {
                    tvStatus.text = "✅ Approved"
                    tvStatus.setTextColor(context.getColor(R.color.status_resolved))
                    tvStatusDot.setBackgroundResource(R.drawable.dot_green)
                }
                "DENIED" -> {
                    tvStatus.text = "❌ Denied"
                    tvStatus.setTextColor(context.getColor(R.color.status_pending))
                    tvStatusDot.setBackgroundResource(R.drawable.dot_red)
                }
                "CANCELLED" -> {
                    tvStatus.text = "🚫 Cancelled"
                    tvStatus.setTextColor(context.getColor(R.color.text_secondary))
                    tvStatusDot.setBackgroundResource(R.drawable.dot_grey)
                }
                else -> {
                    tvStatus.text = "⏳ Awaiting approval"
                    tvStatus.setTextColor(context.getColor(R.color.status_pending))
                    tvStatusDot.setBackgroundResource(R.drawable.dot_grey)
                }
            }

            layoutDecision.visibility = if (canManage && item.status == "PENDING") View.VISIBLE else View.GONE
            btnApprove.setOnClickListener { onApprove(item) }
            btnDeny.setOnClickListener { onDeny(item) }

            btnCancel.visibility = if (item.bookedByUid == currentUid && item.status == "PENDING") View.VISIBLE else View.GONE
            btnCancel.setOnClickListener { onCancel(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Booking>() {
            override fun areItemsTheSame(a: Booking, b: Booking) = a.id == b.id
            override fun areContentsTheSame(a: Booking, b: Booking) = a == b
        }
    }
}
