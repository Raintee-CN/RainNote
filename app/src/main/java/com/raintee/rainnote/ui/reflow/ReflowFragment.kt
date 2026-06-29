package com.raintee.rainnote.ui.reflow

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.raintee.rainnote.databinding.FragmentReflowBinding
import com.raintee.rainnote.databinding.ItemPendingNoteBinding
import com.raintee.rainnote.databinding.ItemWifiPeerBinding
import com.raintee.rainnote.sync.PendingSyncNote
import com.raintee.rainnote.sync.WifiDirectPeer

class ReflowFragment : Fragment() {

    private var _binding: FragmentReflowBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ReflowViewModel
    private lateinit var adapter: WifiPeerAdapter
    private lateinit var pendingAdapter: PendingNoteAdapter
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this)[ReflowViewModel::class.java]
        _binding = FragmentReflowBinding.inflate(inflater, container, false)
        adapter = WifiPeerAdapter { viewModel.connectAndSend(it) }
        pendingAdapter = PendingNoteAdapter()
        binding.recyclerviewWifiPeers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewWifiPeers.adapter = adapter
        binding.recyclerviewPendingNotes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewPendingNotes.adapter = pendingAdapter
        binding.buttonDiscoverWifi.setOnClickListener {
            requestWifiPermissionsIfNeeded()
            viewModel.refresh()
        }
        binding.buttonDisconnectWifi.setOnClickListener { viewModel.disconnectWifiDirect() }
        binding.buttonExportBackup.setOnClickListener { exportBackup.launch("雨笺卡片集备份.json") }
        binding.buttonImportBackup.setOnClickListener { importBackup.launch(arrayOf("application/json", "text/*", "*/*")) }
        viewModel.text.observe(viewLifecycleOwner) { binding.textReflow.text = it }
        viewModel.prompt.observe(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        viewModel.peers.observe(viewLifecycleOwner) { peers ->
            adapter.submitList(peers)
            binding.textPeerEmpty.visibility = if (peers.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.pendingNotes.observe(viewLifecycleOwner) { notes ->
            pendingAdapter.submitList(notes) { updatePendingActions(notes.size) }
            binding.textPendingEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            binding.textPendingTitle.text = if (notes.isEmpty()) "2. 确认接收" else "2. 确认接收（${notes.size} 个卡片集待处理）"
        }
        binding.buttonSelectAllPending.setOnClickListener {
            pendingAdapter.selectAll()
            updatePendingActions(pendingAdapter.itemCount)
        }
        binding.buttonClearPending.setOnClickListener {
            pendingAdapter.clearSelection()
            updatePendingActions(pendingAdapter.itemCount)
        }
        binding.buttonAcceptPending.setOnClickListener {
            val selectedIds = pendingAdapter.selectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(requireContext(), "请先选择要接收的卡片集", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.acceptPending(selectedIds)
                pendingAdapter.clearSelection()
                updatePendingActions(0)
            }
        }
        requestWifiPermissionsIfNeeded()
        viewModel.refresh()
        return binding.root
    }

    private fun updatePendingActions(totalCount: Int) {
        val selectedCount = pendingAdapter.selectedIds().size
        binding.buttonAcceptPending.isEnabled = totalCount > 0 && selectedCount > 0
        binding.buttonAcceptPending.text = if (selectedCount > 0) "接收选中的 $selectedCount 个卡片集" else "接收选中的卡片集"
        binding.buttonSelectAllPending.isEnabled = totalCount > 0
        binding.buttonClearPending.isEnabled = selectedCount > 0
    }

    private fun requestWifiPermissionsIfNeeded() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 1002)
    }

    private fun writeBackup(uri: Uri) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(viewModel.exportBackupJson().toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(requireContext(), "卡片集备份已导出", Toast.LENGTH_SHORT).show()
        } catch (error: Throwable) {
            Toast.makeText(requireContext(), "导出失败：${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readBackup(uri: Uri) {
        try {
            val json = requireContext().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            viewModel.loadBackupJson(json)
        } catch (error: Throwable) {
            Toast.makeText(requireContext(), "导入失败：${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerviewWifiPeers.adapter = null
        binding.recyclerviewPendingNotes.adapter = null
        _binding = null
    }
}

private class WifiPeerAdapter(private val onClick: (WifiDirectPeer) -> Unit) : ListAdapter<WifiDirectPeer, WifiPeerViewHolder>(WifiPeerDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiPeerViewHolder {
        return WifiPeerViewHolder(ItemWifiPeerBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)
    }

    override fun onBindViewHolder(holder: WifiPeerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

private class WifiPeerViewHolder(
    private val binding: ItemWifiPeerBinding,
    private val onClick: (WifiDirectPeer) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(peer: WifiDirectPeer) {
        binding.textWifiPeer.text = "${peer.name}\n${peer.address}\n点击发送本机数据"
        binding.root.setOnClickListener { onClick(peer) }
    }
}

private object WifiPeerDiff : DiffUtil.ItemCallback<WifiDirectPeer>() {
    override fun areItemsTheSame(oldItem: WifiDirectPeer, newItem: WifiDirectPeer) = oldItem.address == newItem.address
    override fun areContentsTheSame(oldItem: WifiDirectPeer, newItem: WifiDirectPeer) = oldItem == newItem
}

private class PendingNoteAdapter : ListAdapter<PendingSyncNote, PendingNoteViewHolder>(PendingNoteDiff) {
    private val selected = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingNoteViewHolder {
        return PendingNoteViewHolder(ItemPendingNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)) { note ->
            if (!selected.add(note.id)) selected.remove(note.id)
            notifyDataSetChanged()
        }
    }

    override fun onBindViewHolder(holder: PendingNoteViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id in selected)
    }

    fun selectedIds(): Set<String> = selected.toSet()

    fun selectAll() {
        selected.clear()
        selected.addAll(currentList.map { it.id })
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }
}

private class PendingNoteViewHolder(
    private val binding: ItemPendingNoteBinding,
    private val onClick: (PendingSyncNote) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(note: PendingSyncNote, selected: Boolean) {
        val prefix = if (selected) "已选择" else "未选择"
        binding.textPendingNote.text = "$prefix · ${note.title}\n卡片 ${note.cardCount} 张，行块 ${note.blockCount} 个，字符 ${note.charCount} 个"
        binding.textPendingNote.isChecked = selected
        binding.root.setOnClickListener { onClick(note) }
    }
}

private object PendingNoteDiff : DiffUtil.ItemCallback<PendingSyncNote>() {
    override fun areItemsTheSame(oldItem: PendingSyncNote, newItem: PendingSyncNote) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: PendingSyncNote, newItem: PendingSyncNote) = oldItem == newItem
}
