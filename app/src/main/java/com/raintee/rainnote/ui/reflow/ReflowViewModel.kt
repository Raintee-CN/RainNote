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

            本机待同步便签：${payload.notes.size} 条
            本机待同步行块：${payload.blocks.size} 个

            MVP 当前已完成 NFC 配对状态检测和同步包构建。下一步可接入局域网 HTTP、Wi-Fi Direct 或蓝牙传输层。
        """.trimIndent()
    }
}
