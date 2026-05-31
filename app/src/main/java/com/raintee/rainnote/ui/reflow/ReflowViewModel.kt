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
        val message = "已接收 $count 个便签。"
        _prompt.value = message
        _text.value = buildText() + "\n\n$message"
    }

    fun exportBackupJson(): String = syncManager.exportBackupJson()

    fun loadBackupJson(json: String) {
        val count = syncManager.loadPendingPayload(json)
        _pendingNotes.value = syncManager.pendingNotes()
        val message = "已解析备份文件，待确认 $count 个便签。"
        _prompt.value = message
        _text.value = buildText() + "\n\n$message"
    }

    private fun buildText(): String {
        val payload = syncManager.buildLocalPayload()
        return """
            碰一碰同步

            ${syncManager.statusText()}

            ${syncManager.bluetoothStatusText()}

            ${syncManager.wifiDirectStatusText()}

            本机便签：${payload.notes.size} 个
            本机卡片：${payload.cards.size} 张
            本机行块：${payload.blocks.size} 个

            点击设备会向对方发送本机数据；收到对方数据后，请在“待接收便签”里选择并点击接收。
        """.trimIndent()
    }
}
