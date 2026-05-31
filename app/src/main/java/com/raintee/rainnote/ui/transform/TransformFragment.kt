package com.raintee.rainnote.ui.transform

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
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
import com.raintee.rainnote.data.NoteCard
import com.raintee.rainnote.databinding.FragmentTransformBinding
import com.raintee.rainnote.databinding.ItemNoteBlockBinding
import com.raintee.rainnote.databinding.ItemNoteCardBinding
import com.raintee.rainnote.databinding.ItemTransformBinding

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TransformViewModel
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var cardAdapter: CardAdapter
    private var titleWatcher: TextWatcher? = null
    private var pendingFocusBlockId: String? = null
    private var pendingFocusCardId: String? = null
    private var bindingTitle = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this)[TransformViewModel::class.java]
        _binding = FragmentTransformBinding.inflate(inflater, container, false)

        noteAdapter = NoteAdapter {
            viewModel.selectNote(it)
            showEditorPanel()
        }
        cardAdapter = CardAdapter(
            onCardTitleChanged = viewModel::updateCardTitle,
            onCardLongPressed = ::showCardMenu,
            onAddRowClicked = { card -> pendingFocusBlockId = viewModel.appendBlock(card) },
            onContentChanged = viewModel::updateBlock,
            onTypeClicked = ::showTypeMenu,
            onDeleteClicked = viewModel::deleteBlock,
            onEnterPressed = { block -> pendingFocusBlockId = viewModel.insertBlockAfter(block) }
        )

        binding.recyclerviewTransform.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewTransform.adapter = noteAdapter
        binding.recyclerviewBlocks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewBlocks.adapter = cardAdapter

        binding.buttonAddNote.setOnClickListener { showCreateNoteDialog() }
        binding.buttonAddBlock.setOnClickListener { showCreateCardDialog() }
        binding.buttonDeleteNote.setOnClickListener {
            viewModel.deleteSelectedNote()
            if (!isWideLayout()) showListPanel()
        }
        binding.editNoteTitle.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.refreshSelected()
        }
        binding.buttonBackToList.setOnClickListener { showListPanel() }
        if (isWideLayout()) showEditorPanel() else showListPanel()

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        return binding.root
    }

    private fun render(state: NoteEditorState) {
        noteAdapter.selectedId = state.selectedNote?.id
        noteAdapter.submitList(state.notes)
        cardAdapter.focusBlockId = pendingFocusBlockId
        cardAdapter.focusCardId = pendingFocusCardId
        cardAdapter.submitList(state.cards) {
            pendingFocusBlockId = null
            pendingFocusCardId = null
            cardAdapter.focusBlockId = null
            cardAdapter.focusCardId = null
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

    private fun showCreateNoteDialog() {
        showTitleDialog(title = "新建便签", hint = "例如：工作记录") { title ->
            viewModel.createNote(title)
            showEditorPanel()
        }
    }

    private fun showCreateCardDialog() {
        showTitleDialog(title = "新建卡片", hint = "例如：今天的灵感") { title ->
            pendingFocusCardId = viewModel.createCard(title)
        }
    }

    private fun showTitleDialog(title: String, hint: String, onCreate: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            this.hint = hint
            isSingleLine = true
            setPadding(32, 16, 32, 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ -> onCreate(input.text.toString()) }
            .show()
    }

    private fun showCardMenu(card: NoteCard, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 0, 0, "删除这张卡片")
            setOnMenuItemClickListener {
                viewModel.deleteCard(card)
                true
            }
        }.show()
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

private class CardAdapter(
    private val onCardTitleChanged: (NoteCard, String) -> Unit,
    private val onCardLongPressed: (NoteCard, View) -> Unit,
    private val onAddRowClicked: (NoteCard) -> Unit,
    private val onContentChanged: (NoteBlock, String) -> Unit,
    private val onTypeClicked: (NoteBlock, View) -> Unit,
    private val onDeleteClicked: (NoteBlock) -> Unit,
    private val onEnterPressed: (NoteBlock) -> Unit
) : ListAdapter<CardEditorItem, CardViewHolder>(CardDiff) {
    var focusBlockId: String? = null
    var focusCardId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        return CardViewHolder(
            ItemNoteCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onCardTitleChanged,
            onCardLongPressed,
            onAddRowClicked,
            onContentChanged,
            onTypeClicked,
            onDeleteClicked,
            onEnterPressed
        )
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, focusCardId == item.card.id, focusBlockId)
    }
}

private class CardViewHolder(
    private val binding: ItemNoteCardBinding,
    private val onCardTitleChanged: (NoteCard, String) -> Unit,
    private val onCardLongPressed: (NoteCard, View) -> Unit,
    private val onAddRowClicked: (NoteCard) -> Unit,
    private val onContentChanged: (NoteBlock, String) -> Unit,
    private val onTypeClicked: (NoteBlock, View) -> Unit,
    private val onDeleteClicked: (NoteBlock) -> Unit,
    private val onEnterPressed: (NoteBlock) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    private var titleWatcher: TextWatcher? = null

    fun bind(item: CardEditorItem, requestCardFocus: Boolean, focusBlockId: String?) {
        titleWatcher?.let { binding.editCardTitle.removeTextChangedListener(it) }
        if (binding.editCardTitle.text.toString() != item.card.title && !binding.editCardTitle.hasFocus()) {
            binding.editCardTitle.setText(item.card.title)
        }
        titleWatcher = binding.editCardTitle.doAfterTextChanged { text ->
            if (text.toString() != item.card.title) onCardTitleChanged(item.card, text.toString())
        }
        binding.root.setOnLongClickListener {
            onCardLongPressed(item.card, it)
            true
        }
        binding.buttonAddRow.setOnClickListener { onAddRowClicked(item.card) }
        binding.blockContainer.removeAllViews()
        val inflater = LayoutInflater.from(binding.root.context)
        item.blocks.forEach { block ->
            val row = ItemNoteBlockBinding.inflate(inflater, binding.blockContainer, false)
            bindBlockRow(row, block, focusBlockId == block.id)
            binding.blockContainer.addView(row.root)
        }
        if (requestCardFocus) {
            binding.editCardTitle.post {
                binding.editCardTitle.requestFocus()
                binding.editCardTitle.setSelection(binding.editCardTitle.text.length)
            }
        }
    }

    private fun bindBlockRow(row: ItemNoteBlockBinding, block: NoteBlock, requestFocus: Boolean) {
        row.textBlockType.text = block.type.label
        row.textBlockType.setOnClickListener { onTypeClicked(block, it) }
        row.root.setOnLongClickListener {
            showBlockMenu(block, it)
            true
        }
        if (row.editBlockContent.text.toString() != block.content && !row.editBlockContent.hasFocus()) {
            row.editBlockContent.setText(block.content)
        }
        configureInput(row.editBlockContent, block.type)
        row.editBlockContent.doAfterTextChanged { text ->
            val value = text.toString()
            if (value != block.content) onContentChanged(block, value)
        }
        row.editBlockContent.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (block.type != BlockType.CodeBlock && (actionId == EditorInfo.IME_ACTION_NEXT || isEnter)) {
                onEnterPressed(block)
                true
            } else {
                false
            }
        }
        if (requestFocus) {
            row.editBlockContent.post {
                row.editBlockContent.requestFocus()
                row.editBlockContent.setSelection(row.editBlockContent.text.length)
            }
        }
    }

    private fun showBlockMenu(block: NoteBlock, anchor: View) {
        PopupMenu(anchor.context, anchor).apply {
            menu.add(0, 0, 0, "切换类型")
            menu.add(0, 1, 1, "删除这一行")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> onTypeClicked(block, anchor)
                    1 -> onDeleteClicked(block)
                }
                true
            }
        }.show()
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

private object CardDiff : DiffUtil.ItemCallback<CardEditorItem>() {
    override fun areItemsTheSame(oldItem: CardEditorItem, newItem: CardEditorItem) = oldItem.card.id == newItem.card.id
    override fun areContentsTheSame(oldItem: CardEditorItem, newItem: CardEditorItem) = oldItem == newItem
}
