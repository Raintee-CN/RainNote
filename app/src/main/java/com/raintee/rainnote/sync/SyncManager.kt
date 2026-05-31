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
    private var pendingPayload: JSONObject? = null

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
                pendingPayload = JSONObject(json)
                val count = pendingNotes().size
                onStatus("已收到同步数据，待选择接收 $count 个便签。")
            },
            onError = { error -> onStatus("Wi-Fi Direct 接收失败：${error.message}") }
        )
    }

    fun pendingNotes(): List<PendingSyncNote> {
        val root = pendingPayload ?: return emptyList()
        val cards = root.optJSONArray("cards")
        val blocks = root.optJSONArray("blocks")
        val cardCountByNote = mutableMapOf<String, Int>()
        val blockCountByNote = mutableMapOf<String, Int>()
        for (index in 0 until (cards?.length() ?: 0)) {
            val item = cards!!.getJSONObject(index)
            val noteId = item.optString("noteId")
            cardCountByNote[noteId] = (cardCountByNote[noteId] ?: 0) + 1
        }
        for (index in 0 until (blocks?.length() ?: 0)) {
            val item = blocks!!.getJSONObject(index)
            val noteId = item.optString("noteId")
            blockCountByNote[noteId] = (blockCountByNote[noteId] ?: 0) + 1
        }
        val notes = root.optJSONArray("notes") ?: return emptyList()
        return buildList {
            for (index in 0 until notes.length()) {
                val item = notes.getJSONObject(index)
                val id = item.getString("id")
                add(
                    PendingSyncNote(
                        id = id,
                        title = item.optString("title", "未命名便签"),
                        cardCount = cardCountByNote[id] ?: 0,
                        blockCount = blockCountByNote[id] ?: 0
                    )
                )
            }
        }
    }

    fun acceptPendingNotes(noteIds: Set<String>): Int {
        val root = pendingPayload ?: return 0
        applyPayloadJson(root, noteIds)
        pendingPayload = null
        return noteIds.size
    }

    fun connectAndSendWifiDirect(peer: WifiDirectPeer, onStatus: (String) -> Unit = {}) {
        wifiDirectSyncManager.connect(peer) { info ->
            AppLog.d("SyncManager", "connectionInfo peer=${peer.name} isGroupOwner=${info.isGroupOwner} groupOwner=${info.groupOwnerAddress?.hostAddress}")
            if (info.isGroupOwner) {
                startWifiDirectReceiver(onStatus)
                onStatus("本机是 Wi-Fi Direct 组主，正在等待 ${peer.name} 发送同步数据。")
                return@connect
            }
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

    private fun applyPayloadJson(root: JSONObject, acceptedNoteIds: Set<String>) {
        AppLog.d("SyncManager", "applyPayloadJson started")
        val acceptedCardIds = mutableSetOf<String>()
        val notes = root.optJSONArray("notes")
        for (index in 0 until (notes?.length() ?: 0)) {
            val item = notes!!.getJSONObject(index)
            val noteId = item.getString("id")
            if (noteId !in acceptedNoteIds) continue
            repository.upsertRemoteNote(
                Note(
                    id = noteId,
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
            val noteId = item.getString("noteId")
            if (noteId !in acceptedNoteIds) continue
            acceptedCardIds.add(item.getString("id"))
            repository.upsertRemoteCard(
                NoteCard(
                    id = item.getString("id"),
                    noteId = noteId,
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
            if (item.getString("cardId") !in acceptedCardIds) continue
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
