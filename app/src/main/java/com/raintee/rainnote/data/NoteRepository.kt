package com.raintee.rainnote.data

import android.content.Context
import java.util.UUID

class NoteRepository(context: Context) {

    private val database = NoteDatabaseHelper(context.applicationContext)

    fun getNotes(): List<Note> {
        val notes = database.getNotes()
        return notes.ifEmpty { listOf(createNote("欢迎使用 RainNote")) }
    }

    fun getBlocks(noteId: String): List<NoteBlock> = database.getBlocks(noteId)

    fun createNote(title: String = "未命名便签"): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        database.upsertNote(note)
        database.upsertBlock(
            NoteBlock(
                id = UUID.randomUUID().toString(),
                noteId = note.id,
                type = BlockType.PlainText,
                content = if (title.startsWith("欢迎")) "按回车创建下一行，右侧按钮切换文本/富文/代码。" else "",
                sortOrder = 0,
                createdAt = now,
                updatedAt = now
            )
        )
        return note
    }

    fun updateNoteTitle(note: Note, title: String): Note {
        val updated = note.copy(title = title.ifBlank { "未命名便签" }, updatedAt = System.currentTimeMillis(), version = note.version + 1)
        database.upsertNote(updated)
        return updated
    }

    fun saveBlock(block: NoteBlock) {
        database.upsertBlock(block.copy(updatedAt = System.currentTimeMillis()))
    }

    fun insertBlockAfter(noteId: String, blocks: List<NoteBlock>, currentBlockId: String, type: BlockType = BlockType.PlainText): NoteBlock {
        val currentIndex = blocks.indexOfFirst { it.id == currentBlockId }
        val now = System.currentTimeMillis()
        val nextBlock = NoteBlock(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
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

    fun deleteNote(noteId: String) {
        database.softDeleteNote(noteId, System.currentTimeMillis())
    }
}
