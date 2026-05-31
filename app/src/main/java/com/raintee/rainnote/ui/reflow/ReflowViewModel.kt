package com.raintee.rainnote.ui.reflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.raintee.rainnote.sync.SyncManager

class ReflowViewModel(application: Application) : AndroidViewModel(application) {
    private val syncManager = SyncManager(application)

    private val _text = MutableLiveData(buildText())
    val text: LiveData<String> = _text

    fun refresh() {
        _text.value = buildText()
    }

    private fun buildText(): String {
        val payload = syncManager.buildLocalPayload()
        return """
            碰一碰同步

            ${syncManager.statusText()}

            ${syncManager.bluetoothStatusText()}

            本机便签：${payload.notes.size} 个
            本机卡片：${payload.cards.size} 张
            本机行块：${payload.blocks.size} 个

            当前已具备 NFC 配对状态检测、蓝牙状态检测、蓝牙发送通道基础和同步包构建。
        """.trimIndent()
    }
}
