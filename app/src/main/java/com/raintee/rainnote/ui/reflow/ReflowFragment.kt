package com.raintee.rainnote.ui.reflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.raintee.rainnote.databinding.FragmentReflowBinding
import com.raintee.rainnote.databinding.ItemWifiPeerBinding
import com.raintee.rainnote.sync.WifiDirectPeer

class ReflowFragment : Fragment() {

    private var _binding: FragmentReflowBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ReflowViewModel
    private lateinit var adapter: WifiPeerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this)[ReflowViewModel::class.java]
        _binding = FragmentReflowBinding.inflate(inflater, container, false)
        adapter = WifiPeerAdapter { viewModel.connectAndSend(it) }
        binding.recyclerviewWifiPeers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewWifiPeers.adapter = adapter
        binding.buttonDiscoverWifi.setOnClickListener {
            requestWifiPermissionsIfNeeded()
            viewModel.refresh()
        }
        viewModel.text.observe(viewLifecycleOwner) { binding.textReflow.text = it }
        viewModel.peers.observe(viewLifecycleOwner) { adapter.submitList(it) }
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerviewWifiPeers.adapter = null
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
