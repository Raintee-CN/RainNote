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
        val updated = repository.updateNoteTitle(note, title)
        val current = _state.value ?: return
        _state.value = current.copy(
            selectedNote = updated,
            notes = current.notes.map { if (it.id == updated.id) updated else it }
        )
    }

    fun updateBlock(block: NoteBlock, content: String) {
        val updated = block.copy(content = content)
        repository.saveBlock(updated)
        val current = _state.value ?: return
        _state.value = current.copy(blocks = current.blocks.map { if (it.id == block.id) updated else it })
    }

    fun setBlockType(block: NoteBlock, type: BlockType): String {
        val updated = block.copy(type = type)
        repository.saveBlock(updated)
        val current = _state.value ?: return block.id
        _state.value = current.copy(blocks = current.blocks.map { if (it.id == block.id) updated else it })
        return block.id
    }

    fun appendBlock(): String? {
        val state = _state.value ?: return null
        val note = state.selectedNote ?: return null
        val lastBlock = state.blocks.lastOrNull()
        val inserted = if (lastBlock == null) {
            repository.insertBlockAfter(note.id, emptyList(), "")
        } else {
            repository.insertBlockAfter(note.id, state.blocks, lastBlock.id)
        }
        refresh(note.id)
        return inserted.id
    }

    fun insertBlockAfter(block: NoteBlock): String? {
        val state = _state.value ?: return null
        val inserted = repository.insertBlockAfter(block.noteId, state.blocks, block.id)
        refresh(block.noteId)
        return inserted.id
    }

    fun deleteBlock(block: NoteBlock) {
        val state = _state.value ?: return
        repository.deleteBlock(state.blocks, block)
        refresh(block.noteId)
    }

    fun deleteSelectedNote() {
        val note = _state.value?.selectedNote ?: return
        repository.deleteNote(note.id)
        refresh(null)
    }
}
