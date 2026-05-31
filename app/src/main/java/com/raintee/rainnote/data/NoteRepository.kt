package com.raintee.rainnote.data

import android.content.Context
import java.util.UUID

class NoteRepository(context: Context) {

    private val database = NoteDatabaseHelper(context.applicationContext)

    fun getNotes(): List<Note> {
        val notes = database.getNotes()
        return notes.ifEmpty { listOf(createNote("欢迎使用雨笺")) }
    }

    fun getCards(noteId: String): List<NoteCard> = database.getCards(noteId)

    fun getBlocks(cardId: String): List<NoteBlock> = database.getBlocks(cardId)

    fun createNote(title: String = "未命名便签"): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        database.upsertNote(note)
        val card = createCard(note.id, if (title.startsWith("欢迎")) "第一张卡片" else "新卡片")
        if (title.startsWith("欢迎")) {
            val firstBlock = database.getBlocks(card.id).firstOrNull()
            if (firstBlock != null) {
                saveBlock(firstBlock.copy(content = "一个便签可以包含多张卡片；每张卡片内可以继续添加文本、富文和代码行。"))
            }
        }
        return note
    }

    fun updateNoteTitle(note: Note, title: String): Note {
        val updated = note.copy(title = title.ifBlank { "未命名便签" }, updatedAt = System.currentTimeMillis(), version = note.version + 1)
        database.upsertNote(updated)
        return updated
    }

    fun createCard(noteId: String, title: String): NoteCard {
        val now = System.currentTimeMillis()
        val card = NoteCard(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            title = title.ifBlank { "未命名卡片" },
            sortOrder = database.getCards(noteId).size,
            createdAt = now,
            updatedAt = now
        )
        database.upsertCard(card)
        insertBlockAfter(card, emptyList(), "")
        return card
    }

    fun updateCardTitle(card: NoteCard, title: String) {
        database.upsertCard(card.copy(title = title.ifBlank { "未命名卡片" }, updatedAt = System.currentTimeMillis()))
    }

    fun saveBlock(block: NoteBlock) {
        database.upsertBlock(block.copy(updatedAt = System.currentTimeMillis()))
    }

    fun insertBlockAfter(card: NoteCard, blocks: List<NoteBlock>, currentBlockId: String, type: BlockType = BlockType.PlainText): NoteBlock {
        val currentIndex = blocks.indexOfFirst { it.id == currentBlockId }
        val now = System.currentTimeMillis()
        val nextBlock = NoteBlock(
            id = UUID.randomUUID().toString(),
            noteId = card.noteId,
            cardId = card.id,
            type = type,
            content = "",
            sortOrder = if (currentIndex == -1) blocks.size else currentIndex + 1,
            createdAt = now,
            updatedAt = now
        )
        val reordered = blocks.toMutableList().apply { add(nextBlock.sortOrder, nextBlock) }
        reordered.forEachIndexed { index, block -> database.upsertBlock(block.copy(sortOrder = index)) }
        return nextBlock
    }

    fun deleteBlock(blocks: List<NoteBlock>, block: NoteBlock) {
        if (blocks.size <= 1) {
            saveBlock(block.copy(content = ""))
            return
        }
        database.softDeleteBlock(block.id, System.currentTimeMillis())
        blocks.filterNot { it.id == block.id }.forEachIndexed { index, remaining ->
            database.upsertBlock(remaining.copy(sortOrder = index))
        }
    }

    fun reorderBlocks(cardId: String, orderedBlockIds: List<String>) {
        val blocksById = database.getBlocks(cardId).associateBy { it.id }
        orderedBlockIds.forEachIndexed { index, blockId ->
            val block = blocksById[blockId] ?: return@forEachIndexed
            database.upsertBlock(block.copy(sortOrder = index, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteCard(noteId: String, cardId: String) {
        val cards = database.getCards(noteId)
        if (cards.size <= 1) return
        database.softDeleteCard(cardId, System.currentTimeMillis())
        cards.filterNot { it.id == cardId }.forEachIndexed { index, remaining ->
            database.upsertCard(remaining.copy(sortOrder = index))
        }
    }

    fun reorderCards(noteId: String, orderedCardIds: List<String>) {
        val cardsById = database.getCards(noteId).associateBy { it.id }
        orderedCardIds.forEachIndexed { index, cardId ->
            val card = cardsById[cardId] ?: return@forEachIndexed
            database.upsertCard(card.copy(sortOrder = index, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteNote(noteId: String) {
        database.softDeleteNote(noteId, System.currentTimeMillis())
    }
}
