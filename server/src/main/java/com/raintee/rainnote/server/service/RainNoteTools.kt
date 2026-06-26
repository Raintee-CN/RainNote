package com.raintee.rainnote.server.service

import com.raintee.rainnote.server.model.ApiNote
import com.raintee.rainnote.server.model.ApiNoteBlock
import com.raintee.rainnote.server.model.ApiNoteCard
import com.raintee.rainnote.server.model.ApiNoteDetail

class RainNoteTools(private val service: RainNoteService) {
    fun listNotes(): List<ApiNote> = service.listNotes()

    fun getNote(noteId: String): ApiNoteDetail? = service.getNote(noteId)

    fun createNote(title: String): ApiNote = service.createNote(title)

    fun renameNote(noteId: String, title: String): ApiNote? = service.updateNote(noteId, title)

    fun deleteNote(noteId: String): Boolean = service.deleteNote(noteId)

    fun createCard(noteId: String, title: String): ApiNoteCard? = service.createCard(noteId, title)

    fun renameCard(cardId: String, title: String): ApiNoteCard? = service.updateCard(cardId, title)

    fun deleteCard(cardId: String): Boolean = service.deleteCard(cardId)

    fun reorderCards(noteId: String, orderedCardIds: List<String>): Boolean = service.reorderCards(noteId, orderedCardIds)

    fun createBlock(cardId: String, type: String = "plain_text", content: String = ""): ApiNoteBlock? =
        service.createBlock(cardId, type, content)

    fun updateBlock(blockId: String, type: String? = null, content: String? = null): ApiNoteBlock? =
        service.updateBlock(blockId, type, content)

    fun deleteBlock(blockId: String): Boolean = service.deleteBlock(blockId)

    fun reorderBlocks(cardId: String, orderedBlockIds: List<String>): Boolean = service.reorderBlocks(cardId, orderedBlockIds)

    fun replaceNoteContent(noteId: String, cards: List<ApiNoteCard>): ApiNoteDetail? =
        service.replaceNoteContent(noteId, cards)
}
