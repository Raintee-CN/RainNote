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
import com.raintee.rainnote.debug.AppLog
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
    private var lastStatus: String = "Wi-Fi 直连未启动。"
    @Volatile private var isReceiving = false

    val isAvailable: Boolean get() = manager != null && channel != null

    fun hasDiscoveryPermission(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNearbyWifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        return hasLocation && hasNearbyWifi
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(onPeersChanged: (List<WifiDirectPeer>) -> Unit = {}) {
        AppLog.d("WifiDirect", "startDiscovery requested")
        val p2pManager = manager ?: return updateStatus("此设备不支持 Wi-Fi 直连。")
        val p2pChannel = channel ?: return updateStatus("Wi-Fi 直连初始化失败。")
        if (!hasDiscoveryPermission()) return updateStatus("缺少 Wi-Fi 直连发现权限，需要定位/附近设备权限。")
        registerReceiver(onPeersChanged)
        p2pManager.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                updateStatus("正在发现附近 Wi-Fi 直连设备。")
                AppLog.d("WifiDirect", "discoverPeers success")
            }

            override fun onFailure(reason: Int) {
                updateStatus("Wi-Fi 直连发现失败：$reason")
                AppLog.d("WifiDirect", "discoverPeers failure reason=$reason")
            }
        })
    }

    fun stopDiscovery() {
        receiver?.let { appContext.unregisterReceiver(it) }
        receiver = null
        AppLog.d("WifiDirect", "receiver stopped")
    }

    fun currentPeers(): List<WifiDirectPeer> = peers

    fun disconnect(onStatus: (String) -> Unit = {}) {
        val p2pManager = manager ?: return onStatus("此设备不支持 Wi-Fi 直连。")
        val p2pChannel = channel ?: return onStatus("Wi-Fi 直连初始化失败。")
        p2pManager.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                updateStatus("Wi-Fi 直连已断开。")
                AppLog.d("WifiDirect", "removeGroup success")
                onStatus("Wi-Fi 直连已断开，可以重新发现并连接其他设备。")
            }

            override fun onFailure(reason: Int) {
                updateStatus("Wi-Fi 直连断开失败：$reason")
                AppLog.d("WifiDirect", "removeGroup failure reason=$reason")
                onStatus("Wi-Fi 直连断开失败：$reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(peer: WifiDirectPeer, onConnected: (WifiP2pInfo) -> Unit = {}) {
        AppLog.d("WifiDirect", "connect requested peer=${peer.name}/${peer.address}")
        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        if (!hasDiscoveryPermission()) return
        val config = WifiP2pConfig().apply { deviceAddress = peer.address }
        p2pManager.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                updateStatus("正在连接 ${peer.name}。")
                AppLog.d("WifiDirect", "connect success callback peer=${peer.name}")
                requestConnectionInfo(onConnected)
            }

            override fun onFailure(reason: Int) {
                updateStatus("连接 ${peer.name} 失败：$reason")
                AppLog.d("WifiDirect", "connect failure peer=${peer.name} reason=$reason")
            }
        })
    }

    fun sendPayload(host: InetAddress, payload: SyncPayload, port: Int = SYNC_PORT): String? {
        AppLog.d("WifiDirect", "sendPayload host=${host.hostAddress} port=$port notes=${payload.notes.size} cards=${payload.cards.size} blocks=${payload.blocks.size}")
        val bytes = payload.toJson().toString().toByteArray(StandardCharsets.UTF_8)
        return Socket(host, port).use { socket ->
            val output = socket.getOutputStream()
            output.write(bytes)
            output.flush()
            socket.shutdownOutput()
            val response = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readText().ifBlank { null }
            AppLog.d("WifiDirect", "sendPayload responseBytes=${response?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0}")
            response
        }
    }

    fun receivePayloadOnce(
        port: Int = SYNC_PORT,
        responsePayload: () -> SyncPayload? = { null },
        onPayload: (String) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        if (isReceiving) {
            updateStatus("Wi-Fi 直连接收端已在监听端口 $port。")
            AppLog.d("WifiDirect", "receivePayloadOnce skipped; already listening port=$port")
            return
        }
        isReceiving = true
        Thread {
            try {
                ServerSocket(port).use { server ->
                    updateStatus("Wi-Fi 直连正在监听端口 $port。")
                    AppLog.d("WifiDirect", "server listening port=$port")
                    while (isReceiving) {
                        val socket = server.accept()
                        socket.use {
                            val text = it.getInputStream().bufferedReader(StandardCharsets.UTF_8).readText()
                            AppLog.d("WifiDirect", "server received bytes=${text.toByteArray(StandardCharsets.UTF_8).size}")
                            onPayload(text)
                            val response = responsePayload()?.toJson()?.toString()
                            if (response != null) {
                                val bytes = response.toByteArray(StandardCharsets.UTF_8)
                                it.getOutputStream().write(bytes)
                                it.getOutputStream().flush()
                                AppLog.d("WifiDirect", "server responded bytes=${bytes.size}")
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                AppLog.e("WifiDirect", "server receive failed", error)
                onError(error)
            } finally {
                isReceiving = false
                AppLog.d("WifiDirect", "server stopped port=$port")
            }
        }.start()
    }

    fun statusText(): String {
        return when {
            !isAvailable -> "此设备不支持 Wi-Fi 直连。"
            !hasDiscoveryPermission() -> "Wi-Fi 直连缺少定位/附近设备权限。"
            peers.isEmpty() -> lastStatus
            else -> "发现 Wi-Fi 直连设备 ${peers.size} 台：${peers.joinToString { it.name }}"
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
                        updateStatus(if (enabled) "Wi-Fi 直连已开启。" else "Wi-Fi 直连未开启。")
                        AppLog.d("WifiDirect", "state changed enabled=$enabled")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers(onPeersChanged)
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        updateStatus(if (networkInfo?.isConnected == true) "Wi-Fi 直连已连接。" else "Wi-Fi 直连未连接。")
                        AppLog.d("WifiDirect", "connection changed connected=${networkInfo?.isConnected == true}")
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
            updateStatus("发现 Wi-Fi 直连设备 ${peers.size} 台。")
            AppLog.d("WifiDirect", "peers changed count=${peers.size} peers=${peers.joinToString { it.name + "/" + it.address }}")
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
            .put("blocks", JSONArray(blocks.map {
                JSONObject()
                    .put("id", it.id)
                    .put("noteId", it.noteId)
                    .put("cardId", it.cardId)
                    .put("type", it.type.storageName)
                    .put("content", it.content)
                    .put("sortOrder", it.sortOrder)
                    .put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt)
            }))
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
