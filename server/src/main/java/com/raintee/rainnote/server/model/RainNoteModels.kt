package com.raintee.rainnote.server.model

data class ApiNote(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 1L
)

data class ApiNoteCard(
    val id: String,
    val noteId: String,
    val title: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val blocks: List<ApiNoteBlock> = emptyList()
)

data class ApiNoteBlock(
    val id: String,
    val noteId: String,
    val cardId: String,
    val type: String,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

data class ApiNoteDetail(
    val note: ApiNote,
    val cards: List<ApiNoteCard>
)

data class CreateNoteRequest(val title: String)

data class UpdateNoteRequest(val title: String)

data class CreateCardRequest(val title: String)

data class UpdateCardRequest(val title: String)

data class CreateBlockRequest(
    val type: String = "plain_text",
    val content: String = ""
)

data class UpdateBlockRequest(
    val type: String? = null,
    val content: String? = null
)

data class ReplaceNoteContentRequest(val cards: List<ApiNoteCard>)

data class ReorderRequest(val orderedIds: List<String>)
