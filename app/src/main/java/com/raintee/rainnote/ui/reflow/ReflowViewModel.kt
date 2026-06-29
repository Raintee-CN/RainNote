package com.raintee.rainnote.ui.reflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.raintee.rainnote.sync.SyncManager
import com.raintee.rainnote.sync.PendingSyncNote
import com.raintee.rainnote.sync.WifiDirectPeer

class ReflowViewModel(application: Application) : AndroidViewModel(application) {
    private val syncManager = SyncManager(application)

    private val _text = MutableLiveData(buildText())
    val text: LiveData<String> = _text
    private val _peers = MutableLiveData<List<WifiDirectPeer>>(emptyList())
    val peers: LiveData<List<WifiDirectPeer>> = _peers
    private val _pendingNotes = MutableLiveData<List<PendingSyncNote>>(emptyList())
    val pendingNotes: LiveData<List<PendingSyncNote>> = _pendingNotes
    private val _prompt = MutableLiveData<String>()
    val prompt: LiveData<String> = _prompt

    fun refresh() {
        syncManager.startWifiDirectDiscovery {
            _peers.postValue(it)
            _text.postValue(buildText())
        }
        syncManager.startWifiDirectReceiver {
            _pendingNotes.postValue(syncManager.pendingNotes())
            _prompt.postValue(it)
            _text.postValue(buildText() + "\n\n$it")
        }
        _text.value = buildText()
    }

    fun connectAndSend(peer: WifiDirectPeer) {
        val waiting = "正在连接 ${peer.name}，请稍候。"
        _prompt.value = waiting
        _text.value = buildText() + "\n\n$waiting"
        syncManager.connectAndSendWifiDirect(peer) {
            _pendingNotes.postValue(syncManager.pendingNotes())
            _prompt.postValue(it)
            _text.postValue(buildText() + "\n\n$it")
        }
    }

    fun disconnectWifiDirect() {
        syncManager.disconnectWifiDirect {
            _prompt.postValue(it)
            _text.postValue(buildText() + "\n\n$it")
        }
    }

    fun acceptPending(noteIds: Set<String>) {
        val count = syncManager.acceptPendingNotes(noteIds)
        _pendingNotes.value = emptyList()
        val message = "已接收 $count 个卡片集。"
        _prompt.value = message
        _text.value = buildText() + "\n\n$message"
    }

    fun exportBackupJson(): String = syncManager.exportBackupJson()

    fun loadBackupJson(json: String) {
        val count = syncManager.importBackupJson(json)
        _pendingNotes.value = emptyList()
        val message = "已导入 $count 个卡片集；同名卡片集已覆盖。"
        _prompt.value = message
        _text.value = buildText() + "\n\n$message"
    }

    private fun buildText(): String {
        val payload = syncManager.buildLocalPayload()
        return """
            本机：${payload.notes.size} 个卡片集 · ${payload.cards.size} 张卡片 · ${payload.blocks.size} 个行块
            Wi‑Fi 直连：${syncManager.wifiDirectStatusText()}
            蓝牙：${syncManager.bluetoothStatusText()}
            流程：扫描设备 → 点击设备发送 → 对方数据出现在待接收区 → 选择后接收；导入备份会直接按标题覆盖
        """.trimIndent()
    }
}
