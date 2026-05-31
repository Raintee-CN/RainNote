package com.raintee.rainnote.data

data class NoteBlock(
    val id: String,
    val noteId: String,
    val type: BlockType,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
