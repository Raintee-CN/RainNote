package com.raintee.rainnote.server.service

import com.raintee.rainnote.server.model.ApiNote
import com.raintee.rainnote.server.model.ApiNoteBlock
import com.raintee.rainnote.server.model.ApiNoteCard
import com.raintee.rainnote.server.model.ApiNoteDetail

interface RainNoteService {
    fun listNotes(): List<ApiNote>
    fun getNote(noteId: String): ApiNoteDetail?
    fun createNote(title: String): ApiNote
    fun updateNote(noteId: String, title: String): ApiNote?
    fun deleteNote(noteId: String): Boolean

    fun createCard(noteId: String, title: String): ApiNoteCard?
    fun updateCard(cardId: String, title: String): ApiNoteCard?
    fun deleteCard(cardId: String): Boolean
    fun reorderCards(noteId: String, orderedCardIds: List<String>): Boolean

    fun createBlock(cardId: String, type: String = "plain_text", content: String = ""): ApiNoteBlock?
    fun updateBlock(blockId: String, type: String? = null, content: String? = null): ApiNoteBlock?
    fun deleteBlock(blockId: String): Boolean
    fun reorderBlocks(cardId: String, orderedBlockIds: List<String>): Boolean

    fun replaceNoteContent(noteId: String, cards: List<ApiNoteCard>): ApiNoteDetail?
}
