package com.raintee.rainnote.server

import android.content.Context
import com.raintee.rainnote.data.BlockType
import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock
import com.raintee.rainnote.data.NoteCard
import com.raintee.rainnote.data.NoteRepository
import com.raintee.rainnote.server.model.ApiNote
import com.raintee.rainnote.server.model.ApiNoteBlock
import com.raintee.rainnote.server.model.ApiNoteCard
import com.raintee.rainnote.server.model.ApiNoteDetail
import com.raintee.rainnote.server.service.RainNoteService
import java.util.UUID

class RainNoteRepositoryService(context: Context) : RainNoteService {
    private val repository = NoteRepository(context.applicationContext)

    override fun listNotes(): List<ApiNote> = repository.getNotes().map { it.toApi() }

    override fun getNote(noteId: String): ApiNoteDetail? {
        val note = findNote(noteId) ?: return null
        return detail(note)
    }

    override fun createNote(title: String): ApiNote = repository.createNote(title).toApi()

    override fun updateNote(noteId: String, title: String): ApiNote? {
        val note = findNote(noteId) ?: return null
        return repository.updateNoteTitle(note, title).toApi()
    }

    override fun deleteNote(noteId: String): Boolean {
        if (findNote(noteId) == null) return false
        repository.deleteNote(noteId)
        return true
    }

    override fun createCard(noteId: String, title: String): ApiNoteCard? {
        if (findNote(noteId) == null) return null
        val card = repository.createCard(noteId, title)
        return card.toApi(blocks = repository.getBlocks(card.id).map { block -> block.toApi() })
    }

    override fun updateCard(cardId: String, title: String): ApiNoteCard? {
        val card = findCard(cardId) ?: return null
        repository.updateCardTitle(card, title)
        val updated = findCard(cardId) ?: return null
        return updated.toApi(repository.getBlocks(updated.id).map { it.toApi() })
    }

    override fun deleteCard(cardId: String): Boolean {
        val card = findCard(cardId) ?: return false
        repository.deleteCard(card.noteId, card.id)
        return true
    }

    override fun reorderCards(noteId: String, orderedCardIds: List<String>): Boolean {
        if (findNote(noteId) == null) return false
        repository.reorderCards(noteId, orderedCardIds)
        return true
    }

    override fun createBlock(cardId: String, type: String, content: String): ApiNoteBlock? {
        val card = findCard(cardId) ?: return null
        val inserted = repository.insertBlockAfter(
            card = card,
            blocks = repository.getBlocks(card.id),
            currentBlockId = repository.getBlocks(card.id).lastOrNull()?.id.orEmpty(),
            type = BlockType.fromStorageName(type)
        )
        val saved = inserted.copy(content = content)
        repository.saveBlock(saved)
        return saved.toApi()
    }

    override fun updateBlock(blockId: String, type: String?, content: String?): ApiNoteBlock? {
        val block = findBlock(blockId) ?: return null
        val updated = block.copy(
            type = type?.let { BlockType.fromStorageName(it) } ?: block.type,
            content = content ?: block.content
        )
        repository.saveBlock(updated)
        return (findBlock(blockId) ?: updated).toApi()
    }

    override fun deleteBlock(blockId: String): Boolean {
        val block = findBlock(blockId) ?: return false
        repository.deleteBlock(repository.getBlocks(block.cardId), block)
        return true
    }

    override fun reorderBlocks(cardId: String, orderedBlockIds: List<String>): Boolean {
        if (findCard(cardId) == null) return false
        repository.reorderBlocks(cardId, orderedBlockIds)
        return true
    }

    override fun replaceNoteContent(noteId: String, cards: List<ApiNoteCard>): ApiNoteDetail? {
        val note = findNote(noteId) ?: return null
        val now = System.currentTimeMillis()
        val localCards = cards.mapIndexed { index, card ->
            NoteCard(
                id = card.id.ifBlank { UUID.randomUUID().toString() },
                noteId = note.id,
                title = card.title.ifBlank { "未命名卡片" },
                sortOrder = index,
                createdAt = card.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
                deletedAt = null
            )
        }
        val blocksByCardId = cards.zip(localCards).associate { (apiCard, localCard) ->
            localCard.id to apiCard.blocks.mapIndexed { blockIndex, block ->
                NoteBlock(
                    id = block.id.ifBlank { UUID.randomUUID().toString() },
                    noteId = note.id,
                    cardId = localCard.id,
                    type = BlockType.fromStorageName(block.type),
                    content = block.content,
                    sortOrder = blockIndex,
                    createdAt = block.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = now,
                    deletedAt = null
                )
            }
        }
        repository.replaceNoteContent(note.id, localCards, blocksByCardId)
        return detail(note)
    }

    private fun detail(note: Note): ApiNoteDetail = ApiNoteDetail(
        note = note.toApi(),
        cards = repository.getCards(note.id).map { card ->
            card.toApi(repository.getBlocks(card.id).map { it.toApi() })
        }
    )

    private fun findNote(noteId: String): Note? = repository.getNotes().firstOrNull { it.id == noteId }

    private fun findCard(cardId: String): NoteCard? = repository.getNotes()
        .asSequence()
        .flatMap { repository.getCards(it.id).asSequence() }
        .firstOrNull { it.id == cardId }

    private fun findBlock(blockId: String): NoteBlock? = repository.getNotes()
        .asSequence()
        .flatMap { repository.getCards(it.id).asSequence() }
        .flatMap { repository.getBlocks(it.id).asSequence() }
        .firstOrNull { it.id == blockId }
}

private fun Note.toApi() = ApiNote(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    version = version
)

private fun NoteCard.toApi(blocks: List<ApiNoteBlock> = emptyList()) = ApiNoteCard(
    id = id,
    noteId = noteId,
    title = title,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    blocks = blocks
)

private fun NoteBlock.toApi() = ApiNoteBlock(
    id = id,
    noteId = noteId,
    cardId = cardId,
    type = type.storageName,
    content = content,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
