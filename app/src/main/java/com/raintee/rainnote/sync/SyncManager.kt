package com.raintee.rainnote.sync

import android.content.Context
import com.raintee.rainnote.data.BlockType
import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock
import com.raintee.rainnote.data.NoteCard
import com.raintee.rainnote.debug.AppLog
import com.raintee.rainnote.data.NoteRepository
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

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

    fun disconnectWifiDirect(onStatus: (String) -> Unit = {}) {
        wifiDirectSyncManager.disconnect(onStatus)
    }

    fun startWifiDirectReceiver(onStatus: (String) -> Unit = {}) {
        wifiDirectSyncManager.receivePayloadOnce(
            responsePayload = { buildLocalPayload() },
            onPayload = { json ->
                AppLog.d("SyncManager", "received payload json length=${json.length}")
                pendingPayload = JSONObject(json)
                val count = pendingNotes().size
                onStatus("已收到对方数据，待选择接收 $count 个卡片集。")
            },
            onError = { error -> onStatus("Wi-Fi 直连接收失败：${error.message}") }
        )
    }

    fun pendingNotes(): List<PendingSyncNote> {
        val root = pendingPayload ?: return emptyList()
        val cards = root.optJSONArray("cards")
        val blocks = root.optJSONArray("blocks")
        val cardCountByNote = mutableMapOf<String, Int>()
        val blockCountByNote = mutableMapOf<String, Int>()
        val charCountByNote = mutableMapOf<String, Int>()
        for (index in 0 until (cards?.length() ?: 0)) {
            val item = cards!!.getJSONObject(index)
            val noteId = item.optString("noteId")
            cardCountByNote[noteId] = (cardCountByNote[noteId] ?: 0) + 1
        }
        for (index in 0 until (blocks?.length() ?: 0)) {
            val item = blocks!!.getJSONObject(index)
            val noteId = item.optString("noteId")
            blockCountByNote[noteId] = (blockCountByNote[noteId] ?: 0) + 1
            charCountByNote[noteId] = (charCountByNote[noteId] ?: 0) + item.optString("content").length
        }
        val notes = root.optJSONArray("notes") ?: return emptyList()
        return buildList {
            for (index in 0 until notes.length()) {
                val item = notes.getJSONObject(index)
                val id = item.getString("id")
                add(
                    PendingSyncNote(
                        id = id,
                        title = item.optString("title", "未命名卡片集"),
                        cardCount = cardCountByNote[id] ?: 0,
                        blockCount = blockCountByNote[id] ?: 0,
                        charCount = charCountByNote[id] ?: 0
                    )
                )
            }
        }
    }

    fun exportBackupJson(): String = payloadToJson(buildLocalPayload()).toString(2)

    fun loadPendingPayload(json: String): Int {
        pendingPayload = JSONObject(json)
        val count = pendingNotes().size
        AppLog.d("SyncManager", "loaded backup payload pendingNotes=$count")
        return count
    }

    fun importBackupJson(json: String): Int {
        val root = JSONObject(json)
        val ids = noteIds(root)
        applyPayloadJson(root, ids)
        pendingPayload = null
        AppLog.d("SyncManager", "importBackupJson imported=${ids.size}")
        return ids.size
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
                onStatus("已连接 ${peer.name}，正在等待对方建立交换通道。")
                return@connect
            }
            val host = info.groupOwnerAddress
            if (host == null) {
                AppLog.d("SyncManager", "connectAndSend no groupOwnerAddress peer=${peer.name}")
                onStatus("已连接，但未获取到交换地址，请稍后重试。")
                return@connect
            }
            Thread {
                try {
                    AppLog.d("SyncManager", "exchanging payload with peer=${peer.name} host=${host.hostAddress}")
                    val response = wifiDirectSyncManager.sendPayload(host, buildLocalPayload())
                    if (!response.isNullOrBlank()) {
                        pendingPayload = JSONObject(response)
                        onStatus("已和 ${peer.name} 完成数据交换，待选择接收 ${pendingNotes().size} 个卡片集。")
                    } else {
                        onStatus("已向 ${peer.name} 发送本机数据，但未收到对方数据。")
                    }
                } catch (error: Throwable) {
                    AppLog.e("SyncManager", "exchange payload failed peer=${peer.name}", error)
                    onStatus("和 ${peer.name} 交换数据失败：${error.message}")
                }
            }.start()
        }
    }

    private fun applyPayloadJson(root: JSONObject, acceptedNoteIds: Set<String>) {
        AppLog.d("SyncManager", "applyPayloadJson started")
        val localNotesByTitle = repository.getNotes().associateBy { normalizeTitle(it.title) }
        val acceptedCardIds = mutableMapOf<String, String>()
        val targetNoteIds = mutableMapOf<String, String>()
        val notes = root.optJSONArray("notes")
        for (index in 0 until (notes?.length() ?: 0)) {
            val item = notes!!.getJSONObject(index)
            val sourceNoteId = item.getString("id")
            if (sourceNoteId !in acceptedNoteIds) continue
            val title = item.optString("title", "未命名卡片集")
            val targetNoteId = localNotesByTitle[normalizeTitle(title)]?.id ?: sourceNoteId
            targetNoteIds[sourceNoteId] = targetNoteId
            repository.upsertRemoteNote(
                Note(
                    id = targetNoteId,
                    title = title,
                    createdAt = localNotesByTitle[normalizeTitle(title)]?.createdAt ?: item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    version = item.optLong("version", 1L)
                )
            )
        }
        val cards = root.optJSONArray("cards")
        val cardsByTargetNoteId = mutableMapOf<String, MutableList<NoteCard>>()
        val blocksByTargetCardId = mutableMapOf<String, MutableList<NoteBlock>>()
        for (index in 0 until (cards?.length() ?: 0)) {
            val item = cards!!.getJSONObject(index)
            val sourceNoteId = item.getString("noteId")
            val targetNoteId = targetNoteIds[sourceNoteId] ?: continue
            val sourceCardId = item.getString("id")
            val targetCardId = UUID.randomUUID().toString()
            acceptedCardIds[sourceCardId] = targetCardId
            val card = NoteCard(
                id = targetCardId,
                noteId = targetNoteId,
                title = item.optString("title", "未命名卡片"),
                sortOrder = item.optInt("sortOrder", index),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
            cardsByTargetNoteId.getOrPut(targetNoteId) { mutableListOf() }.add(card)
        }
        val blocks = root.optJSONArray("blocks")
        for (index in 0 until (blocks?.length() ?: 0)) {
            val item = blocks!!.getJSONObject(index)
            val targetCardId = acceptedCardIds[item.getString("cardId")] ?: continue
            val sourceNoteId = item.optString("noteId")
            val targetNoteId = targetNoteIds[sourceNoteId] ?: continue
            val block = NoteBlock(
                id = UUID.randomUUID().toString(),
                noteId = targetNoteId,
                cardId = targetCardId,
                type = BlockType.fromStorageName(item.optString("type")),
                content = item.optString("content"),
                sortOrder = item.optInt("sortOrder", index),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
            blocksByTargetCardId.getOrPut(targetCardId) { mutableListOf() }.add(block)
        }
        cardsByTargetNoteId.forEach { (targetNoteId, targetCards) ->
            repository.replaceNoteContent(targetNoteId, targetCards, blocksByTargetCardId)
        }
        AppLog.d("SyncManager", "applyPayloadJson finished notes=${notes?.length() ?: 0} cards=${cards?.length() ?: 0} blocks=${blocks?.length() ?: 0}")
    }

    private fun noteIds(root: JSONObject): Set<String> {
        val notes = root.optJSONArray("notes") ?: return emptySet()
        return buildSet {
            for (index in 0 until notes.length()) add(notes.getJSONObject(index).getString("id"))
        }
    }

    private fun normalizeTitle(title: String): String = title.trim().ifBlank { "未命名卡片集" }

    private fun payloadToJson(payload: SyncPayload): JSONObject {
        return JSONObject()
            .put("deviceId", payload.deviceId)
            .put("timestamp", payload.timestamp)
            .put("notes", JSONArray(payload.notes.map { note ->
                JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt)
                    .put("version", note.version)
            }))
            .put("cards", JSONArray(payload.cards.map { card ->
                JSONObject()
                    .put("id", card.id)
                    .put("noteId", card.noteId)
                    .put("title", card.title)
                    .put("sortOrder", card.sortOrder)
                    .put("createdAt", card.createdAt)
                    .put("updatedAt", card.updatedAt)
            }))
            .put("blocks", JSONArray(payload.blocks.map { block ->
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
            .put("deletedIds", JSONArray(payload.deletedIds))
    }
}
