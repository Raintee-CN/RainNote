package com.raintee.rainnote.ui.transform

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.raintee.rainnote.data.BlockType
import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock
import com.raintee.rainnote.databinding.FragmentTransformBinding
import com.raintee.rainnote.databinding.ItemNoteBlockBinding
import com.raintee.rainnote.databinding.ItemTransformBinding

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TransformViewModel
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var blockAdapter: BlockAdapter
    private var titleWatcher: TextWatcher? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this)[TransformViewModel::class.java]
        _binding = FragmentTransformBinding.inflate(inflater, container, false)

        noteAdapter = NoteAdapter { viewModel.selectNote(it) }
        blockAdapter = BlockAdapter(
            onContentChanged = viewModel::updateBlock,
            onTypeClicked = viewModel::changeBlockType,
            onDeleteClicked = viewModel::deleteBlock,
            onEnterPressed = viewModel::insertBlockAfter
        )

        binding.recyclerviewTransform.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewTransform.adapter = noteAdapter
        binding.recyclerviewBlocks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewBlocks.adapter = blockAdapter

        binding.buttonAddNote.setOnClickListener { viewModel.createNote() }
        binding.buttonDeleteNote.setOnClickListener { viewModel.deleteSelectedNote() }

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        return binding.root
    }

    private fun render(state: NoteEditorState) {
        noteAdapter.selectedId = state.selectedNote?.id
        noteAdapter.submitList(state.notes)
        blockAdapter.submitList(state.blocks)
        titleWatcher?.let { binding.editNoteTitle.removeTextChangedListener(it) }
        binding.editNoteTitle.setText(state.selectedNote?.title.orEmpty())
        binding.editNoteTitle.setSelection(binding.editNoteTitle.text.length)
        titleWatcher = binding.editNoteTitle.doAfterTextChanged { text ->
            if (state.selectedNote != null && text.toString() != state.selectedNote.title) {
                viewModel.updateTitle(text.toString())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerviewTransform.adapter = null
        binding.recyclerviewBlocks.adapter = null
        _binding = null
    }
}

private class NoteAdapter(private val onClick: (Note) -> Unit) : ListAdapter<Note, NoteViewHolder>(NoteDiff) {
    var selectedId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        return NoteViewHolder(ItemTransformBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == selectedId)
    }
}

private class NoteViewHolder(
    private val binding: ItemTransformBinding,
    private val onClick: (Note) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(note: Note, selected: Boolean) {
        binding.textViewItemTransform.text = note.title
        binding.textViewItemTransform.isSelected = selected
        binding.textViewItemTransform.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        binding.root.setOnClickListener { onClick(note) }
    }
}

private object NoteDiff : DiffUtil.ItemCallback<Note>() {
    override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
}

private class BlockAdapter(
    private val onContentChanged: (NoteBlock, String) -> Unit,
    private val onTypeClicked: (NoteBlock) -> Unit,
    private val onDeleteClicked: (NoteBlock) -> Unit,
    private val onEnterPressed: (NoteBlock) -> Unit
) : ListAdapter<NoteBlock, BlockViewHolder>(BlockDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockViewHolder {
        return BlockViewHolder(
            ItemNoteBlockBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onContentChanged,
            onTypeClicked,
            onDeleteClicked,
            onEnterPressed
        )
    }

    override fun onBindViewHolder(holder: BlockViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

private class BlockViewHolder(
    private val binding: ItemNoteBlockBinding,
    private val onContentChanged: (NoteBlock, String) -> Unit,
    private val onTypeClicked: (NoteBlock) -> Unit,
    private val onDeleteClicked: (NoteBlock) -> Unit,
    private val onEnterPressed: (NoteBlock) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    private var watcher: TextWatcher? = null

    fun bind(block: NoteBlock) {
        binding.buttonBlockType.text = block.type.label
        binding.buttonBlockType.setOnClickListener { onTypeClicked(block) }
        binding.buttonDeleteBlock.setOnClickListener { onDeleteClicked(block) }

        watcher?.let { binding.editBlockContent.removeTextChangedListener(it) }
        binding.editBlockContent.setText(block.content)
        configureInput(binding.editBlockContent, block.type)
        watcher = binding.editBlockContent.doAfterTextChanged { text ->
            val value = text.toString()
            if (value != block.content) onContentChanged(block, value)
        }
        binding.editBlockContent.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (block.type != BlockType.CodeBlock && (actionId == EditorInfo.IME_ACTION_NEXT || isEnter)) {
                onEnterPressed(block)
                true
            } else {
                false
            }
        }
    }

    private fun configureInput(editText: EditText, type: BlockType) {
        when (type) {
            BlockType.PlainText -> {
                editText.isSingleLine = true
                editText.imeOptions = EditorInfo.IME_ACTION_NEXT
                editText.typeface = Typeface.DEFAULT
            }
            BlockType.RichText -> {
                editText.isSingleLine = false
                editText.minLines = 1
                editText.imeOptions = EditorInfo.IME_ACTION_NEXT
                editText.typeface = Typeface.DEFAULT
            }
            BlockType.CodeBlock -> {
                editText.isSingleLine = false
                editText.minLines = 3
                editText.imeOptions = EditorInfo.IME_ACTION_NONE
                editText.typeface = Typeface.MONOSPACE
            }
        }
    }
}

private object BlockDiff : DiffUtil.ItemCallback<NoteBlock>() {
    override fun areItemsTheSame(oldItem: NoteBlock, newItem: NoteBlock) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: NoteBlock, newItem: NoteBlock) = oldItem == newItem
}
