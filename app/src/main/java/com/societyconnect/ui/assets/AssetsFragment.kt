package com.societyconnect.ui.assets

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
import com.societyconnect.data.models.Asset
import com.societyconnect.data.models.ServiceRecord
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.BottomSheetAddAssetBinding
import com.societyconnect.databinding.BottomSheetLogServiceBinding
import com.societyconnect.databinding.FragmentAssetsBinding
import com.societyconnect.databinding.ItemAssetBinding
import com.societyconnect.databinding.ItemServiceRecordBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getAssetIcon
import com.societyconnect.utils.makeCall
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toRupees
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Fragment ─────────────────────────────────────────────────────────
class AssetsFragment : Fragment() {
    private var _binding: FragmentAssetsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AssetsViewModel
    private lateinit var session: SessionManager
    private lateinit var assetAdapter: AssetAdapter
    private lateinit var serviceAdapter: ServiceRecordAdapter
    private var selectedServiceDate = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAssetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[AssetsViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canManage = session.canManageContent()

        assetAdapter = AssetAdapter(
            canManage = canManage,
            onLogService = { showLogServiceSheet(it) },
            onDelete = { confirmDeleteAsset(it) }
        )
        serviceAdapter = ServiceRecordAdapter(
            canManage = canManage,
            onDelete = { confirmDeleteServiceRecord(it) }
        )

        binding.rvAssets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAssets.adapter = assetAdapter
        binding.rvServiceLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvServiceLog.adapter = serviceAdapter

        viewModel.allAssets.observe(viewLifecycleOwner) { list ->
            assetAdapter.submitList(list)
            binding.emptyAssets.visibility = if (list.isEmpty() && binding.chipAssets.isChecked) View.VISIBLE else View.GONE
        }
        viewModel.allServiceRecords.observe(viewLifecycleOwner) { list ->
            serviceAdapter.submitList(list)
            binding.emptyServiceLog.visibility = if (list.isEmpty() && !binding.chipAssets.isChecked) View.VISIBLE else View.GONE
        }

        binding.chipGroupTab.setOnCheckedStateChangeListener { _, _ -> applyTab() }
        applyTab()

        binding.fabAdd.setOnClickListener { showAddAssetSheet() }
    }

    private fun applyTab() {
        val showAssets = binding.chipAssets.isChecked
        binding.rvAssets.visibility = if (showAssets) View.VISIBLE else View.GONE
        binding.rvServiceLog.visibility = if (showAssets) View.GONE else View.VISIBLE
        binding.emptyAssets.visibility = if (showAssets && assetAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyServiceLog.visibility = if (!showAssets && serviceAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.fabAdd.visibility = if (showAssets && session.canManageContent()) View.VISIBLE else View.GONE
    }

    private fun showAddAssetSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddAssetBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        var selectedCategory = "LIFT"
        var purchaseDate: Long? = null
        var warrantyDate: Long? = null
        var amcDate: Long? = null

        sheet.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            selectedCategory = chip.tag.toString()
        }

        sheet.btnPickPurchase.setOnClickListener {
            pickDate { purchaseDate = it; sheet.tvPurchaseDate.text = it.toDateString() }
        }
        sheet.btnPickWarranty.setOnClickListener {
            pickDate { warrantyDate = it; sheet.tvWarrantyDate.text = it.toDateString() }
        }
        sheet.btnPickAmc.setOnClickListener {
            pickDate { amcDate = it; sheet.tvAmcDate.text = it.toDateString() }
        }

        sheet.btnSave.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            if (name.isEmpty()) {
                requireContext().toast("Please enter a name")
                return@setOnClickListener
            }
            viewModel.addAsset(
                Asset(
                    name = name,
                    category = selectedCategory,
                    location = sheet.etLocation.text.toString().trim(),
                    vendorName = sheet.etVendorName.text.toString().trim(),
                    vendorPhone = sheet.etVendorPhone.text.toString().trim(),
                    purchaseDate = purchaseDate,
                    warrantyExpiry = warrantyDate,
                    amcExpiry = amcDate,
                    notes = sheet.etNotes.text.toString().trim(),
                    addedBy = session.getName()
                )
            )
            requireContext().toast("Asset added")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showLogServiceSheet(asset: Asset) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetLogServiceBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.tvAssetName.text = asset.name
        selectedServiceDate = System.currentTimeMillis()
        sheet.tvSelectedDate.text = selectedServiceDate.toDateString()

        sheet.btnPickDate.setOnClickListener {
            pickDate { selectedServiceDate = it; sheet.tvSelectedDate.text = it.toDateString() }
        }

        sheet.btnSave.setOnClickListener {
            val description = sheet.etDescription.text.toString().trim()
            if (description.isEmpty()) {
                requireContext().toast("Please describe the service performed")
                return@setOnClickListener
            }
            val cost = sheet.etCost.text.toString().toDoubleOrNull() ?: 0.0
            viewModel.addServiceRecord(
                ServiceRecord(
                    assetId = asset.id,
                    assetName = asset.name,
                    description = description,
                    cost = cost,
                    performedBy = sheet.etPerformedBy.text.toString().trim(),
                    date = selectedServiceDate,
                    loggedBy = session.getName()
                )
            )
            requireContext().toast("Service logged for ${asset.name}")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun pickDate(onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            cal.set(y, m, d)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun confirmDeleteAsset(a: Asset) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Asset")
            .setMessage("Remove \"${a.name}\" from the asset catalog?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteAsset(a); requireContext().toast("Asset removed") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteServiceRecord(s: ServiceRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Service Record")
            .setMessage("Remove this service entry for \"${s.assetName}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteServiceRecord(s); requireContext().toast("Service record removed") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class AssetsViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allAssets get() = repo.allAssets
    val allServiceRecords get() = repo.allServiceRecords

    fun addAsset(a: Asset) = viewModelScope.launch { repo.addAsset(a) }
    fun deleteAsset(a: Asset) = viewModelScope.launch { repo.deleteAsset(a) }
    fun addServiceRecord(s: ServiceRecord) = viewModelScope.launch { repo.addServiceRecord(s) }
    fun deleteServiceRecord(s: ServiceRecord) = viewModelScope.launch { repo.deleteServiceRecord(s) }
}

// ─── Asset Adapter ────────────────────────────────────────────────────
class AssetAdapter(
    private val canManage: Boolean,
    private val onLogService: (Asset) -> Unit,
    private val onDelete: (Asset) -> Unit
) : ListAdapter<Asset, AssetAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAssetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val now = System.currentTimeMillis()
        with(holder.binding) {
            tvIcon.text = getAssetIcon(item.category)
            tvName.text = item.name
            tvLocation.text = item.location
            tvLocation.visibility = if (item.location.isBlank()) View.GONE else View.VISIBLE

            tvVendor.text = "Vendor: ${item.vendorName}" + if (item.vendorPhone.isNotBlank()) " · ${item.vendorPhone}" else ""
            tvVendor.visibility = if (item.vendorName.isBlank()) View.GONE else View.VISIBLE
            btnCallVendor.visibility = if (item.vendorPhone.isNotBlank()) View.VISIBLE else View.GONE
            btnCallVendor.setOnClickListener { root.context.makeCall(item.vendorPhone) }

            val context = root.context
            tvWarranty.text = "Warranty: ${item.warrantyExpiry?.toDateString() ?: "Not set"}"
            tvWarranty.setTextColor(
                context.getColor(if (item.warrantyExpiry != null && item.warrantyExpiry < now) R.color.status_pending else R.color.text_secondary)
            )

            tvAmc.text = "AMC: ${item.amcExpiry?.toDateString() ?: "Not set"}"
            tvAmc.setTextColor(
                context.getColor(if (item.amcExpiry != null && item.amcExpiry < now) R.color.status_pending else R.color.text_secondary)
            )

            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
            btnLogService.visibility = if (canManage) View.VISIBLE else View.GONE
            btnLogService.setOnClickListener { onLogService(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Asset>() {
            override fun areItemsTheSame(a: Asset, b: Asset) = a.id == b.id
            override fun areContentsTheSame(a: Asset, b: Asset) = a == b
        }
    }
}

// ─── Service Record Adapter ────────────────────────────────────────────
class ServiceRecordAdapter(
    private val canManage: Boolean,
    private val onDelete: (ServiceRecord) -> Unit
) : ListAdapter<ServiceRecord, ServiceRecordAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemServiceRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemServiceRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvAssetName.text = item.assetName
            tvDate.text = item.date.toDateString()
            tvDescription.text = item.description
            tvCost.text = item.cost.toRupees()
            tvPerformedBy.text = "By ${item.performedBy}"
            tvPerformedBy.visibility = if (item.performedBy.isBlank()) View.GONE else View.VISIBLE

            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ServiceRecord>() {
            override fun areItemsTheSame(a: ServiceRecord, b: ServiceRecord) = a.id == b.id
            override fun areContentsTheSame(a: ServiceRecord, b: ServiceRecord) = a == b
        }
    }
}
