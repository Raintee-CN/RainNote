package com.raintee.rainnote.sync

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class BluetoothSyncManager(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val isBluetoothAvailable: Boolean get() = bluetoothAdapter != null
    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BluetoothPeer> {
        if (!isBluetoothEnabled || !hasConnectPermission()) return emptyList()
        return bluetoothAdapter?.bondedDevices.orEmpty().map { device ->
            BluetoothPeer(
                name = device.name ?: "未知设备",
                address = device.address
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun sendPayloadToPairedDevice(address: String, payload: SyncPayload) {
        if (!isBluetoothEnabled || !hasConnectPermission()) return
        val device = bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address } ?: return
        sendPayload(device, payload)
    }

    @SuppressLint("MissingPermission")
    private fun sendPayload(device: BluetoothDevice, payload: SyncPayload) {
        val bytes = payload.toJson().toString().toByteArray(StandardCharsets.UTF_8)
        device.createRfcommSocketToServiceRecord(SERVICE_UUID).use { socket ->
            bluetoothAdapter?.cancelDiscovery()
            socket.connect()
            socket.outputStream.use { output ->
                output.write(bytes)
                output.flush()
            }
        }
    }

    fun statusText(): String = when {
        !isBluetoothAvailable -> "此设备不支持蓝牙。"
        !isBluetoothEnabled -> "蓝牙未开启，请先在系统设置中开启。"
        !hasConnectPermission() -> "缺少蓝牙连接权限，需要授权后才能同步。"
        else -> "蓝牙已就绪，已配对设备 ${pairedDevices().size} 台。"
    }

    private fun SyncPayload.toJson(): JSONObject {
        return JSONObject()
            .put("deviceId", deviceId)
            .put("timestamp", timestamp)
            .put("notes", JSONArray(notes.map { note ->
                JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt)
                    .put("version", note.version)
            }))
            .put("cards", JSONArray(cards.map { card ->
                JSONObject()
                    .put("id", card.id)
                    .put("noteId", card.noteId)
                    .put("title", card.title)
                    .put("sortOrder", card.sortOrder)
                    .put("createdAt", card.createdAt)
                    .put("updatedAt", card.updatedAt)
            }))
            .put("blocks", JSONArray(blocks.map { block ->
                JSONObject()
                    .put("id", block.id)
                    .put("noteId", block.noteId)
                    .put("cardId", block.cardId)
                    .put("type", block.type.storageName)
                    .put("content", block.content)
                    .put("sortOrder", block.sortOrder)
                    .put("createdAt", block.createdAt)
                    .put("updatedAt", block.updatedAt)
            }))
            .put("deletedIds", JSONArray(deletedIds))
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("b6a55f6e-831d-4a0b-b5c2-24897f1a6a52")
    }
}

data class BluetoothPeer(
    val name: String,
    val address: String
)
