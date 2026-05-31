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
import android.widget.PopupMenu
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
    private var pendingFocusBlockId: String? = null
    private var bindingTitle = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this)[TransformViewModel::class.java]
        _binding = FragmentTransformBinding.inflate(inflater, container, false)

        noteAdapter = NoteAdapter {
            viewModel.selectNote(it)
            showEditorPanel()
        }
        blockAdapter = BlockAdapter(
            onContentChanged = viewModel::updateBlock,
            onTypeClicked = ::showTypeMenu,
            onDeleteClicked = viewModel::deleteBlock,
            onEnterPressed = { block -> pendingFocusBlockId = viewModel.insertBlockAfter(block) }
        )

        binding.recyclerviewTransform.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewTransform.adapter = noteAdapter
        binding.recyclerviewBlocks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewBlocks.adapter = blockAdapter

        binding.buttonAddNote.setOnClickListener {
            viewModel.createNote()
            showEditorPanel()
        }
        binding.buttonAddBlock.setOnClickListener { pendingFocusBlockId = viewModel.appendBlock() }
        binding.buttonDeleteNote.setOnClickListener {
            viewModel.deleteSelectedNote()
            if (!isWideLayout()) showListPanel()
        }
        binding.buttonBackToList.setOnClickListener { showListPanel() }
        if (isWideLayout()) showEditorPanel() else showListPanel()

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        return binding.root
    }

    private fun render(state: NoteEditorState) {
        noteAdapter.selectedId = state.selectedNote?.id
        noteAdapter.submitList(state.notes)
        blockAdapter.focusBlockId = pendingFocusBlockId
        blockAdapter.submitList(state.blocks) {
            pendingFocusBlockId = null
            blockAdapter.focusBlockId = null
        }
        titleWatcher?.let { binding.editNoteTitle.removeTextChangedListener(it) }
        val title = state.selectedNote?.title.orEmpty()
        if (binding.editNoteTitle.text.toString() != title && !binding.editNoteTitle.hasFocus()) {
            bindingTitle = true
            binding.editNoteTitle.setText(title)
            binding.editNoteTitle.setSelection(binding.editNoteTitle.text.length)
            bindingTitle = false
        }
        titleWatcher = binding.editNoteTitle.doAfterTextChanged { text ->
            if (!bindingTitle && state.selectedNote != null && text.toString() != state.selectedNote.title) {
                viewModel.updateTitle(text.toString())
            }
        }
    }

    private fun showTypeMenu(block: NoteBlock, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            BlockType.entries.forEachIndexed { index, type -> menu.add(0, index, index, type.label) }
            setOnMenuItemClickListener { item ->
                val type = BlockType.entries[item.itemId]
                pendingFocusBlockId = viewModel.setBlockType(block, type)
                true
            }
        }.show()
    }

    private fun isWideLayout(): Boolean = binding.buttonBackToList.visibility == View.GONE

    private fun showListPanel() {
        binding.noteListPanel.visibility = View.VISIBLE
        binding.editorPanel.visibility = if (isWideLayout()) View.VISIBLE else View.GONE
    }

    private fun showEditorPanel() {
        binding.editorPanel.visibility = View.VISIBLE
        if (!isWideLayout()) binding.noteListPanel.visibility = View.GONE
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
    private val onTypeClicked: (NoteBlock, View) -> Unit,
    private val onDeleteClicked: (NoteBlock) -> Unit,
    private val onEnterPressed: (NoteBlock) -> Unit
) : ListAdapter<NoteBlock, BlockViewHolder>(BlockDiff) {
    var focusBlockId: String? = null

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
        val block = getItem(position)
        holder.bind(block, block.id == focusBlockId)
    }
}

private class BlockViewHolder(
    private val binding: ItemNoteBlockBinding,
    private val onContentChanged: (NoteBlock, String) -> Unit,
    private val onTypeClicked: (NoteBlock, View) -> Unit,
    private val onDeleteClicked: (NoteBlock) -> Unit,
    private val onEnterPressed: (NoteBlock) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    private var watcher: TextWatcher? = null

    fun bind(block: NoteBlock, requestFocus: Boolean) {
        binding.buttonBlockType.text = block.type.label
        binding.buttonBlockType.setOnClickListener { onTypeClicked(block, it) }
        binding.buttonDeleteBlock.setOnClickListener { onDeleteClicked(block) }

        watcher?.let { binding.editBlockContent.removeTextChangedListener(it) }
        if (binding.editBlockContent.text.toString() != block.content && !binding.editBlockContent.hasFocus()) {
            binding.editBlockContent.setText(block.content)
        }
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
        if (requestFocus) {
            binding.editBlockContent.post {
                binding.editBlockContent.requestFocus()
                binding.editBlockContent.setSelection(binding.editBlockContent.text.length)
            }
        }
    }

    private fun configureInput(editText: EditText, type: BlockType) {
        when (type) {
            BlockType.PlainText -> {
                editText.isSingleLine = true
                editText.imeOptions = EditorInfo.IME_ACTION_NEXT
                editText.typeface = Typeface.DEFAULT
                editText.hint = "输入一行文字，回车到下一行"
            }
            BlockType.RichText -> {
                editText.isSingleLine = false
                editText.minLines = 1
                editText.imeOptions = EditorInfo.IME_ACTION_NEXT
                editText.typeface = Typeface.DEFAULT
                editText.hint = "支持 Markdown，回车创建下一块"
            }
            BlockType.CodeBlock -> {
                editText.isSingleLine = false
                editText.minLines = 3
                editText.imeOptions = EditorInfo.IME_ACTION_NONE
                editText.typeface = Typeface.MONOSPACE
                editText.hint = "代码块内回车换行"
            }
        }
    }
}

private object BlockDiff : DiffUtil.ItemCallback<NoteBlock>() {
    override fun areItemsTheSame(oldItem: NoteBlock, newItem: NoteBlock) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: NoteBlock, newItem: NoteBlock) = oldItem == newItem
}
