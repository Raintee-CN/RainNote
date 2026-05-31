package com.raintee.rainnote.ui.slideshow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SlideshowViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = """
            雨笺

            一款清爽的卡片式便签应用。

            核心能力
            · 便签内可创建多张卡片
            · 每张卡片包含多个行块
            · 行块支持文本、富文和代码块
            · 支持卡片排序、行块排序和搜索
            · 支持 Wi‑Fi 直连同步
            · 支持 JSON 文件备份导入导出

            同步说明
            发送方点击附近设备后发出本机数据；接收方会先看到待接收便签列表，勾选后才会导入。

            当前版本
            1.0
        """.trimIndent()
    }
    val text: LiveData<String> = _text
}
