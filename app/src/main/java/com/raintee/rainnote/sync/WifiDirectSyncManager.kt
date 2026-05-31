package com.raintee.rainnote.sync

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class WifiDirectSyncManager(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(appContext, appContext.mainLooper, null)
    private var receiver: BroadcastReceiver? = null
    private var peers: List<WifiDirectPeer> = emptyList()
    private var lastStatus: String = "Wi-Fi Direct 未启动。"

    val isAvailable: Boolean get() = manager != null && channel != null

    fun hasDiscoveryPermission(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNearbyWifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        return hasLocation && hasNearbyWifi
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(onPeersChanged: (List<WifiDirectPeer>) -> Unit = {}) {
        val p2pManager = manager ?: return updateStatus("此设备不支持 Wi-Fi Direct。")
        val p2pChannel = channel ?: return updateStatus("Wi-Fi Direct 初始化失败。")
        if (!hasDiscoveryPermission()) return updateStatus("缺少 Wi-Fi Direct 发现权限，需要定位/附近设备权限。")
        registerReceiver(onPeersChanged)
        p2pManager.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                updateStatus("正在发现附近 Wi-Fi Direct 设备。")
            }

            override fun onFailure(reason: Int) {
                updateStatus("Wi-Fi Direct 发现失败：$reason")
            }
        })
    }

    fun stopDiscovery() {
        receiver?.let { appContext.unregisterReceiver(it) }
        receiver = null
    }

    @SuppressLint("MissingPermission")
    fun connect(peer: WifiDirectPeer, onConnected: (WifiP2pInfo) -> Unit = {}) {
        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        if (!hasDiscoveryPermission()) return
        val config = WifiP2pConfig().apply { deviceAddress = peer.address }
        p2pManager.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                updateStatus("正在连接 ${peer.name}。")
                requestConnectionInfo(onConnected)
            }

            override fun onFailure(reason: Int) {
                updateStatus("连接 ${peer.name} 失败：$reason")
            }
        })
    }

    fun sendPayload(host: InetAddress, payload: SyncPayload, port: Int = SYNC_PORT) {
        val bytes = payload.toJson().toString().toByteArray(StandardCharsets.UTF_8)
        Socket(host, port).use { socket ->
            socket.getOutputStream().use { output ->
                output.write(bytes)
                output.flush()
            }
        }
    }

    fun receivePayloadOnce(port: Int = SYNC_PORT, onPayload: (String) -> Unit) {
        Thread {
            ServerSocket(port).use { server ->
                val socket = server.accept()
                socket.use {
                    val text = it.getInputStream().bufferedReader(StandardCharsets.UTF_8).readText()
                    onPayload(text)
                }
            }
        }.start()
    }

    fun statusText(): String {
        return when {
            !isAvailable -> "此设备不支持 Wi-Fi Direct。"
            !hasDiscoveryPermission() -> "Wi-Fi Direct 缺少定位/附近设备权限。"
            peers.isEmpty() -> lastStatus
            else -> "发现 Wi-Fi Direct 设备 ${peers.size} 台：${peers.joinToString { it.name }}"
        }
    }

    private fun registerReceiver(onPeersChanged: (List<WifiDirectPeer>) -> Unit) {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        updateStatus(if (enabled) "Wi-Fi Direct 已开启。" else "Wi-Fi Direct 未开启。")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers(onPeersChanged)
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        updateStatus(if (networkInfo?.isConnected == true) "Wi-Fi Direct 已连接。" else "Wi-Fi Direct 未连接。")
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers(onPeersChanged: (List<WifiDirectPeer>) -> Unit) {
        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        if (!hasDiscoveryPermission()) return
        p2pManager.requestPeers(p2pChannel) { peerList ->
            peers = peerList.deviceList.map { it.toPeer() }
            updateStatus("发现 Wi-Fi Direct 设备 ${peers.size} 台。")
            onPeersChanged(peers)
        }
    }

    private fun requestConnectionInfo(onConnected: (WifiP2pInfo) -> Unit) {
        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        p2pManager.requestConnectionInfo(p2pChannel) { info -> onConnected(info) }
    }

    private fun updateStatus(value: String) {
        lastStatus = value
    }

    private fun WifiP2pDevice.toPeer(): WifiDirectPeer = WifiDirectPeer(
        name = deviceName ?: "未知设备",
        address = deviceAddress,
        status = status
    )

    private fun SyncPayload.toJson(): JSONObject {
        return JSONObject()
            .put("deviceId", deviceId)
            .put("timestamp", timestamp)
            .put("notes", JSONArray(notes.map { JSONObject().put("id", it.id).put("title", it.title).put("updatedAt", it.updatedAt) }))
            .put("cards", JSONArray(cards.map { JSONObject().put("id", it.id).put("noteId", it.noteId).put("title", it.title).put("sortOrder", it.sortOrder) }))
            .put("blocks", JSONArray(blocks.map { JSONObject().put("id", it.id).put("cardId", it.cardId).put("type", it.type.storageName).put("content", it.content).put("sortOrder", it.sortOrder) }))
    }

    companion object {
        const val SYNC_PORT = 48620
    }
}

data class WifiDirectPeer(
    val name: String,
    val address: String,
    val status: Int
)
