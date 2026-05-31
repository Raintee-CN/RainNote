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
        binding.buttonExportBackup.setOnClickListener { exportBackup.launch("雨笺备份.json") }
        binding.buttonImportBackup.setOnClickListener { importBackup.launch(arrayOf("application/json", "text/*", "*/*")) }
        viewModel.text.observe(viewLifecycleOwner) { binding.textReflow.text = it }
        viewModel.prompt.observe(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        viewModel.peers.observe(viewLifecycleOwner) { adapter.submitList(it) }
        viewModel.pendingNotes.observe(viewLifecycleOwner) { pendingAdapter.submitList(it) }
        binding.buttonAcceptPending.setOnClickListener { viewModel.acceptPending(pendingAdapter.selectedIds()) }
        requestWifiPermissionsIfNeeded()
        viewModel.refresh()
        return binding.root
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
        requireContext().contentResolver.openOutputStream(uri)?.use { output ->
            output.write(viewModel.exportBackupJson().toByteArray(Charsets.UTF_8))
        }
        Toast.makeText(requireContext(), "备份已导出", Toast.LENGTH_SHORT).show()
    }

    private fun readBackup(uri: Uri) {
        val json = requireContext().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        viewModel.loadBackupJson(json)
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
        binding.textWifiPeer.text = "${peer.name}\n${peer.address}"
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
}

private class PendingNoteViewHolder(
    private val binding: ItemPendingNoteBinding,
    private val onClick: (PendingSyncNote) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(note: PendingSyncNote, selected: Boolean) {
        binding.textPendingNote.text = "${note.title}\n卡片 ${note.cardCount} 张，行块 ${note.blockCount} 个，字符 ${note.charCount} 个"
        binding.textPendingNote.isChecked = selected
        binding.root.setOnClickListener { onClick(note) }
    }
}

private object PendingNoteDiff : DiffUtil.ItemCallback<PendingSyncNote>() {
    override fun areItemsTheSame(oldItem: PendingSyncNote, newItem: PendingSyncNote) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: PendingSyncNote, newItem: PendingSyncNote) = oldItem == newItem
}
