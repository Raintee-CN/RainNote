package com.raintee.rainnote.ui.transform

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.raintee.rainnote.data.BlockType
import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock
import com.raintee.rainnote.data.NoteRepository

data class NoteEditorState(
    val notes: List<Note> = emptyList(),
    val selectedNote: Note? = null,
    val blocks: List<NoteBlock> = emptyList()
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
            blocks = selected?.let { repository.getBlocks(it.id) }.orEmpty()
        )
    }

    fun createNote() {
        val note = repository.createNote()
        refresh(note.id)
    }

    fun selectNote(note: Note) {
        refresh(note.id)
    }

    fun updateTitle(title: String) {
        val note = _state.value?.selectedNote ?: return
        repository.updateNoteTitle(note, title)
    }

    fun updateBlock(block: NoteBlock, content: String) {
        repository.saveBlock(block.copy(content = content))
    }

    fun refreshSelected() {
        refresh(_state.value?.selectedNote?.id)
    }

    fun setBlockType(block: NoteBlock, type: BlockType): String {
        val latest = repository.getBlocks(block.noteId).firstOrNull { it.id == block.id } ?: block
        val updated = latest.copy(type = type)
        repository.saveBlock(updated)
        val current = _state.value ?: return updated.id
        _state.value = current.copy(blocks = repository.getBlocks(updated.noteId))
        return block.id
    }

    fun appendBlock(): String? {
        val state = _state.value ?: return null
        val note = state.selectedNote ?: return null
        val latestBlocks = repository.getBlocks(note.id)
        val lastBlock = latestBlocks.lastOrNull()
        val inserted = if (lastBlock == null) {
            repository.insertBlockAfter(note.id, emptyList(), "")
        } else {
            repository.insertBlockAfter(note.id, latestBlocks, lastBlock.id)
        }
        refresh(note.id)
        return inserted.id
    }

    fun insertBlockAfter(block: NoteBlock): String? {
        val latestBlocks = repository.getBlocks(block.noteId)
        val inserted = repository.insertBlockAfter(block.noteId, latestBlocks, block.id)
        refresh(block.noteId)
        return inserted.id
    }

    fun deleteBlock(block: NoteBlock) {
        val latestBlocks = repository.getBlocks(block.noteId)
        val latestBlock = latestBlocks.firstOrNull { it.id == block.id } ?: block
        repository.deleteBlock(latestBlocks, latestBlock)
        refresh(block.noteId)
    }

    fun deleteSelectedNote() {
        val note = _state.value?.selectedNote ?: return
        repository.deleteNote(note.id)
        refresh(null)
    }
}
