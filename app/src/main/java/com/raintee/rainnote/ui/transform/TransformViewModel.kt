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
        refresh(updated.id)
    }

    fun updateBlock(block: NoteBlock, content: String) {
        repository.saveBlock(block.copy(content = content))
        refresh(block.noteId)
    }

    fun changeBlockType(block: NoteBlock) {
        val nextType = when (block.type) {
            BlockType.PlainText -> BlockType.RichText
            BlockType.RichText -> BlockType.CodeBlock
            BlockType.CodeBlock -> BlockType.PlainText
        }
        repository.saveBlock(block.copy(type = nextType))
        refresh(block.noteId)
    }

    fun insertBlockAfter(block: NoteBlock) {
        val state = _state.value ?: return
        repository.insertBlockAfter(block.noteId, state.blocks, block.id)
        refresh(block.noteId)
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
