package com.societyconnect.ui.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
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
import com.societyconnect.data.models.SocietyDocument
import com.societyconnect.data.repository.SocietyRepository
import com.societyconnect.databinding.BottomSheetAddDocumentBinding
import com.societyconnect.databinding.FragmentDocumentsBinding
import com.societyconnect.databinding.ItemDocumentBinding
import com.societyconnect.utils.SessionManager
import com.societyconnect.utils.getDocumentCategoryLabel
import com.societyconnect.utils.toDateString
import com.societyconnect.utils.toFileSizeString
import com.societyconnect.utils.toast
import kotlinx.coroutines.launch

// ─── Fragment ─────────────────────────────────────────────────────────
class DocumentsFragment : Fragment() {
    private var _binding: FragmentDocumentsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: DocumentsViewModel
    private lateinit var session: SessionManager
    private lateinit var adapter: DocumentsAdapter

    private var pickedFileUri: Uri? = null
    private var pickedFileName: String = ""
    private var pickedFileSize: Long = 0L
    private var addSheet: BottomSheetAddDocumentBinding? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val (name, size) = queryFileInfo(uri)
        if (size > MAX_FILE_SIZE) {
            requireContext().toast("File too large (max 10 MB)")
            return@registerForActivityResult
        }
        pickedFileUri = uri
        pickedFileName = name
        pickedFileSize = size
        addSheet?.tvFileName?.text = name
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDocumentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        viewModel = ViewModelProvider(this)[DocumentsViewModel::class.java]
        viewModel.init(session.getSocietyId())

        val canManage = session.canManageContent()
        adapter = DocumentsAdapter(canManage = canManage, onClick = { openDocument(it) }, onDelete = { confirmDelete(it) })

        binding.rvDocuments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDocuments.adapter = adapter

        viewModel.allDocuments.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.visibility = if (canManage) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddSheet() }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetAddDocumentBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        addSheet = sheet

        pickedFileUri = null
        pickedFileName = ""
        pickedFileSize = 0L
        sheet.tvFileName.text = getString(R.string.no_file_chosen)

        var selectedCategory = "OTHER"
        sheet.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            selectedCategory = chip.tag.toString()
        }

        sheet.btnChooseFile.setOnClickListener { pickFileLauncher.launch("*/*") }

        sheet.btnUpload.setOnClickListener {
            val title = sheet.etTitle.text.toString().trim()
            val uri = pickedFileUri
            if (title.isEmpty()) {
                requireContext().toast("Please enter a title")
                return@setOnClickListener
            }
            if (uri == null) {
                requireContext().toast("Please choose a file")
                return@setOnClickListener
            }
            viewModel.uploadDocument(uri, pickedFileName, pickedFileSize, title, selectedCategory, session.getName())
            requireContext().toast("Uploading document…")
            dialog.dismiss()
        }
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { addSheet = null }
        dialog.show()
    }

    private fun queryFileInfo(uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun openDocument(doc: SocietyDocument) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc.downloadUrl)))
    }

    private fun confirmDelete(doc: SocietyDocument) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Document")
            .setMessage("Remove \"${doc.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteDocument(doc); requireContext().toast("Document removed") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object {
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024L
    }
}

// ─── ViewModel ────────────────────────────────────────────────────────
class DocumentsViewModel : ViewModel() {
    private lateinit var repo: SocietyRepository
    fun init(societyId: String) { if (!::repo.isInitialized) repo = SocietyRepository(societyId) }

    val allDocuments get() = repo.allDocuments

    fun uploadDocument(uri: Uri, fileName: String, fileSize: Long, title: String, category: String, uploadedBy: String) =
        viewModelScope.launch { repo.uploadDocument(uri, fileName, fileSize, title, category, uploadedBy) }

    fun deleteDocument(doc: SocietyDocument) = viewModelScope.launch { repo.deleteDocument(doc) }
}

// ─── Adapter ─────────────────────────────────────────────────────────
class DocumentsAdapter(
    private val canManage: Boolean,
    private val onClick: (SocietyDocument) -> Unit,
    private val onDelete: (SocietyDocument) -> Unit
) : ListAdapter<SocietyDocument, DocumentsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.title
            tvCategory.text = getDocumentCategoryLabel(item.category)
            tvMeta.text = "${item.fileSize.toFileSizeString()} · ${item.uploadedAt.toDateString()}"
            root.setOnClickListener { onClick(item) }
            btnDelete.visibility = if (canManage) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SocietyDocument>() {
            override fun areItemsTheSame(a: SocietyDocument, b: SocietyDocument) = a.id == b.id
            override fun areContentsTheSame(a: SocietyDocument, b: SocietyDocument) = a == b
        }
    }
}
