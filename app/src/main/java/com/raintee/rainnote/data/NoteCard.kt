package com.raintee.rainnote.data

data class NoteCard(
    val id: String,
    val noteId: String,
    val title: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
