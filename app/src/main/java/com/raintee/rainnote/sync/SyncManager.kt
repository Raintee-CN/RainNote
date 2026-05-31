package com.raintee.rainnote.sync

import android.content.Context
import com.raintee.rainnote.data.BlockType
import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock
import com.raintee.rainnote.data.NoteCard
import com.raintee.rainnote.debug.AppLog
import com.raintee.rainnote.data.NoteRepository
import org.json.JSONObject

class SyncManager(context: Context) {

    private val repository = NoteRepository(context.applicationContext)
    private val pairingManager = NfcPairingManager(context.applicationContext)
    private val bluetoothSyncManager = BluetoothSyncManager(context.applicationContext)
    private val wifiDirectSyncManager = WifiDirectSyncManager(context.applicationContext)

    fun buildLocalPayload(): SyncPayload {
        val notes = repository.getNotes()
        val cards = notes.flatMap { repository.getCards(it.id) }
        val blocks = cards.flatMap { repository.getBlocks(it.id) }
        AppLog.d("SyncManager", "buildLocalPayload notes=${notes.size} cards=${cards.size} blocks=${blocks.size}")
        return SyncPayload(
            deviceId = pairingManager.localHandshake().deviceId,
            notes = notes,
            cards = cards,
            blocks = blocks,
            deletedIds = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    fun statusText(): String = pairingManager.statusText()

    fun bluetoothStatusText(): String = bluetoothSyncManager.statusText()

    fun startWifiDirectDiscovery(onPeersChanged: (List<WifiDirectPeer>) -> Unit = {}) {
        wifiDirectSyncManager.startDiscovery(onPeersChanged)
    }

    fun wifiDirectStatusText(): String = wifiDirectSyncManager.statusText()

    fun wifiDirectPeers(): List<WifiDirectPeer> = wifiDirectSyncManager.currentPeers()

    fun startWifiDirectReceiver(onStatus: (String) -> Unit = {}) {
        wifiDirectSyncManager.receivePayloadOnce(
            onPayload = { json ->
                AppLog.d("SyncManager", "received payload json length=${json.length}")
                applyPayloadJson(json)
                onStatus("已接收并合并 Wi-Fi Direct 同步数据。")
            },
            onError = { error -> onStatus("Wi-Fi Direct 接收失败：${error.message}") }
        )
    }

    fun connectAndSendWifiDirect(peer: WifiDirectPeer, onStatus: (String) -> Unit = {}) {
        wifiDirectSyncManager.connect(peer) { info ->
            val host = info.groupOwnerAddress
            if (host == null) {
                AppLog.d("SyncManager", "connectAndSend no groupOwnerAddress peer=${peer.name}")
                onStatus("已连接，但未获取到对方地址。")
                return@connect
            }
            Thread {
                try {
                    AppLog.d("SyncManager", "sending payload to peer=${peer.name} host=${host.hostAddress}")
                    wifiDirectSyncManager.sendPayload(host, buildLocalPayload())
                    onStatus("已向 ${peer.name} 发送同步数据。")
                } catch (error: Throwable) {
                    AppLog.e("SyncManager", "send payload failed peer=${peer.name}", error)
                    onStatus("发送到 ${peer.name} 失败：${error.message}")
                }
            }.start()
        }
    }

    private fun applyPayloadJson(json: String) {
        val root = JSONObject(json)
        AppLog.d("SyncManager", "applyPayloadJson started")
        val notes = root.optJSONArray("notes")
        for (index in 0 until (notes?.length() ?: 0)) {
            val item = notes!!.getJSONObject(index)
            repository.upsertRemoteNote(
                Note(
                    id = item.getString("id"),
                    title = item.optString("title", "未命名便签"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    version = item.optLong("version", 1L)
                )
            )
        }
        val cards = root.optJSONArray("cards")
        for (index in 0 until (cards?.length() ?: 0)) {
            val item = cards!!.getJSONObject(index)
            repository.upsertRemoteCard(
                NoteCard(
                    id = item.getString("id"),
                    noteId = item.getString("noteId"),
                    title = item.optString("title", "未命名卡片"),
                    sortOrder = item.optInt("sortOrder", index),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        val blocks = root.optJSONArray("blocks")
        for (index in 0 until (blocks?.length() ?: 0)) {
            val item = blocks!!.getJSONObject(index)
            repository.upsertRemoteBlock(
                NoteBlock(
                    id = item.getString("id"),
                    noteId = item.optString("noteId"),
                    cardId = item.getString("cardId"),
                    type = BlockType.fromStorageName(item.optString("type")),
                    content = item.optString("content"),
                    sortOrder = item.optInt("sortOrder", index),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        AppLog.d("SyncManager", "applyPayloadJson finished notes=${notes?.length() ?: 0} cards=${cards?.length() ?: 0} blocks=${blocks?.length() ?: 0}")
    }
}
