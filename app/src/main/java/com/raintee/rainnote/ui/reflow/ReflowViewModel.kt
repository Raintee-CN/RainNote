package com.raintee.rainnote.ui.reflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.raintee.rainnote.sync.SyncManager
import com.raintee.rainnote.sync.WifiDirectPeer

class ReflowViewModel(application: Application) : AndroidViewModel(application) {
    private val syncManager = SyncManager(application)

    private val _text = MutableLiveData(buildText())
    val text: LiveData<String> = _text
    private val _peers = MutableLiveData<List<WifiDirectPeer>>(emptyList())
    val peers: LiveData<List<WifiDirectPeer>> = _peers

    fun refresh() {
        syncManager.startWifiDirectDiscovery {
            _peers.postValue(it)
            _text.postValue(buildText())
        }
        syncManager.startWifiDirectReceiver {
            _text.postValue(buildText() + "\n\n$it")
        }
        _text.value = buildText()
    }

    fun connectAndSend(peer: WifiDirectPeer) {
        _text.value = buildText() + "\n\n正在连接 ${peer.name}..."
        syncManager.connectAndSendWifiDirect(peer) {
            _text.postValue(buildText() + "\n\n$it")
        }
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

            点击下方设备可连接并发送本机同步数据；本页也会监听对方发来的同步数据。
        """.trimIndent()
    }
}
