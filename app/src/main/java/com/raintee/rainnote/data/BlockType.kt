package com.raintee.rainnote.data

enum class BlockType(val storageName: String, val label: String) {
    PlainText("plain_text", "文本"),
    RichText("rich_text", "富文"),
    CodeBlock("code_block", "代码");

    companion object {
        fun fromStorageName(value: String): BlockType = entries.firstOrNull { it.storageName == value } ?: PlainText
    }
}
