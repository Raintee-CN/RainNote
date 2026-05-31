package com.raintee.rainnote.ui.transform

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.raintee.rainnote.data.BlockType
import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock
import com.raintee.rainnote.data.NoteCard
import com.raintee.rainnote.data.NoteRepository

data class CardEditorItem(
    val card: NoteCard,
    val blocks: List<NoteBlock>
)

data class NoteEditorState(
    val notes: List<Note> = emptyList(),
    val selectedNote: Note? = null,
    val cards: List<CardEditorItem> = emptyList()
)

class TransformViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(application)
    private val _state = MutableLiveData(NoteEditorState())
    val state: LiveData<NoteEditorState> = _state

    init {
        refresh()
    }

    fun refresh(selectedId: String? = _state.value?.selectedNote?.id) {
        val notes = repository.getNotes()
        val selected = notes.firstOrNull { it.id == selectedId } ?: notes.firstOrNull()
        _state.value = NoteEditorState(
            notes = notes,
            selectedNote = selected,
            cards = selected?.let { loadCards(it.id) }.orEmpty()
        )
    }

    fun createNote(title: String = "未命名便签") {
        val note = repository.createNote(title.ifBlank { "未命名便签" })
        refresh(note.id)
    }

    fun selectNote(note: Note) {
        refresh(note.id)
    }

    fun updateTitle(title: String) {
        val note = _state.value?.selectedNote ?: return
        repository.updateNoteTitle(note, title)
    }

    fun createCard(title: String): String? {
        val note = _state.value?.selectedNote ?: return null
        val card = repository.createCard(note.id, title)
        refresh(note.id)
        return card.id
    }

    fun updateCardTitle(card: NoteCard, title: String) {
        repository.updateCardTitle(card, title)
    }

    fun deleteCard(card: NoteCard) {
        repository.deleteCard(card.noteId, card.id)
        refresh(card.noteId)
    }

    fun reorderCards(orderedCardIds: List<String>) {
        val note = _state.value?.selectedNote ?: return
        repository.reorderCards(note.id, orderedCardIds)
        refresh(note.id)
    }

    fun updateBlock(block: NoteBlock, content: String) {
        repository.saveBlock(block.copy(content = content))
    }

    fun refreshSelected() {
        refresh(_state.value?.selectedNote?.id)
    }

    fun setBlockType(block: NoteBlock, type: BlockType): String {
        val latest = repository.getBlocks(block.cardId).firstOrNull { it.id == block.id } ?: block
        repository.saveBlock(latest.copy(type = type))
        refresh(latest.noteId)
        return block.id
    }

    fun appendBlock(card: NoteCard): String? {
        val latestBlocks = repository.getBlocks(card.id)
        val lastBlock = latestBlocks.lastOrNull()
        val inserted = if (lastBlock == null) {
            repository.insertBlockAfter(card, emptyList(), "")
        } else {
            repository.insertBlockAfter(card, latestBlocks, lastBlock.id)
        }
        refresh(card.noteId)
        return inserted.id
    }

    fun insertBlockAfter(block: NoteBlock): String? {
        val card = repository.getCards(block.noteId).firstOrNull { it.id == block.cardId } ?: return null
        val latestBlocks = repository.getBlocks(block.cardId)
        val inserted = repository.insertBlockAfter(card, latestBlocks, block.id)
        refresh(block.noteId)
        return inserted.id
    }

    fun deleteBlock(block: NoteBlock) {
        val latestBlocks = repository.getBlocks(block.cardId)
        val latestBlock = latestBlocks.firstOrNull { it.id == block.id } ?: block
        repository.deleteBlock(latestBlocks, latestBlock)
        refresh(block.noteId)
    }

    fun moveBlock(block: NoteBlock, direction: Int) {
        val latestBlocks = repository.getBlocks(block.cardId)
        val from = latestBlocks.indexOfFirst { it.id == block.id }
        val to = (from + direction).coerceIn(0, latestBlocks.lastIndex)
        if (from == -1 || from == to) return
        val moved = latestBlocks.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to, item)
        repository.reorderBlocks(block.cardId, moved.map { it.id })
        refresh(block.noteId)
    }

    fun appendMarkdown(block: NoteBlock, snippet: String) {
        val latest = repository.getBlocks(block.cardId).firstOrNull { it.id == block.id } ?: block
        val separator = if (latest.content.isBlank()) "" else "\n"
        repository.saveBlock(latest.copy(type = BlockType.RichText, content = latest.content + separator + snippet))
        refresh(latest.noteId)
    }

    fun deleteSelectedNote() {
        val note = _state.value?.selectedNote ?: return
        repository.deleteNote(note.id)
        refresh(null)
    }

    private fun loadCards(noteId: String): List<CardEditorItem> {
        return repository.getCards(noteId).map { card ->
            CardEditorItem(card = card, blocks = repository.getBlocks(card.id))
        }
    }
}
