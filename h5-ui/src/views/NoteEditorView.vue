<template>
  <main>
    <van-nav-bar title="编辑卡片集" left-text="返回" left-arrow @click-left="router.back()">
      <template #right>
        <van-button size="small" type="primary" :loading="notes.saving" @click="save">保存</van-button>
      </template>
    </van-nav-bar>

    <van-skeleton :loading="notes.loading" title :row="6">
      <van-cell-group inset title="卡片集信息">
        <van-field v-model="title" label="标题" placeholder="卡片集标题" @blur="syncTitle" />
      </van-cell-group>

      <van-notice-bar v-if="errorText" wrapable :scrollable="false" type="danger">
        {{ errorText }}
        <template #right-icon>
          <van-button size="mini" type="danger" plain @click="loadNote">重试</van-button>
        </template>
      </van-notice-bar>

      <van-empty v-if="!notes.loading && !notes.note" description="卡片集不存在或已被删除">
        <van-button round type="primary" @click="router.replace('/notes')">返回卡片集库</van-button>
      </van-empty>

      <van-cell-group v-for="(card, index) in notes.cards" :key="card.clientKey || card.id" inset :title="`卡片 ${index + 1}`">
        <van-field v-model="card.title" label="标题" placeholder="卡片标题">
          <template #button>
            <van-button size="small" type="danger" plain @click="removeCard(card)">删除</van-button>
          </template>
        </van-field>
        <van-empty v-if="!card.blocks?.length" image-size="72" description="这张卡片还没有内容" />
        <van-cell v-for="block in card.blocks" :key="block.clientKey || block.id">
          <template #icon>
            <van-button
              round
              size="mini"
              :color="blockTypeColor(block.type)"
              @touchstart="startTypePress(block)"
              @touchend="cancelTypePress"
              @touchcancel="cancelTypePress"
              @mousedown="startTypePress(block)"
              @mouseup="cancelTypePress"
              @mouseleave="cancelTypePress"
              @click="openTypeSheet(block)"
            />
          </template>
          <template #title>
            <div
              v-if="block.type === 'plain_text' || block.type === 'rich_text'"
              :contenteditable="true"
              :style="block.type === 'rich_text' ? richEditorStyle : plainEditorStyle"
              @focus="activeRichBlock = block"
              @blur="syncRichHtml(block, $event)"
              @input="syncRichHtml(block, $event)"
              @keydown.delete="removeEmptyRichBlock(card, block, $event)"
              v-html="safeHtml(block.content)"
            />
            <van-field
              v-else
              :model-value="codeText(block.content)"
              type="textarea"
              autosize
              rows="2"
              :placeholder="blockPlaceholder(block.type)"
              :style="blockFieldStyle(block.type)"
              @update:model-value="updateCodeText(block, $event)"
              @keydown.delete="removeEmptyBlock(card, block, $event)"
            />
            <van-cell-group v-if="block.type === 'rich_text'" inset>
              <van-button size="mini" plain @mousedown.prevent @click="applyRichCommand('formatBlock', 'h3')">标题</van-button>
              <van-button size="mini" plain @mousedown.prevent @click="applyRichCommand('bold')">加粗</van-button>
              <van-button size="mini" plain @mousedown.prevent @click="applyRichCommand('italic')">斜体</van-button>
              <van-button size="mini" plain @mousedown.prevent @click="applyRichCommand('formatBlock', 'blockquote')">引用</van-button>
            </van-cell-group>
          </template>
        </van-cell>
          <van-button block plain type="primary" @click="addBlock(card)">添加行块</van-button>
      </van-cell-group>

      <van-cell-group inset>
        <van-button block plain type="primary" @click="addCard">添加卡片</van-button>
      </van-cell-group>

      <van-action-bar>
        <van-action-bar-button type="primary" :loading="notes.saving" text="保存全部" @click="save" />
      </van-action-bar>

      <van-action-sheet
        v-model:show="typeSheetVisible"
        :actions="blockTypeActions"
        cancel-text="取消"
        description="修改行块类型"
        @select="selectBlockType"
      />
    </van-skeleton>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { onBeforeRouteLeave } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { useNotesStore } from '../stores/notes'
import { errorMessage } from '../api/client'
import { codeText, htmlToText, normalizeBlockContent, sanitizeHtml, stringifyCodeContent, textToHtml, updateCodeText } from '../utils/blockContent'

const route = useRoute()
const router = useRouter()
const notes = useNotesStore()
const title = ref('')
const errorText = ref('')
const savedSnapshot = ref('')
const typeSheetVisible = ref(false)
const activeTypeBlock = ref(null)
const activeRichBlock = ref(null)
let typePressTimer = null
let clientId = 0

const richEditorStyle = {
  minHeight: '44px',
  outline: 'none',
  lineHeight: '1.7',
  wordBreak: 'break-word',
  whiteSpace: 'normal',
}

const plainEditorStyle = {
  minHeight: '38px',
  outline: 'none',
  lineHeight: '1.7',
  wordBreak: 'break-word',
  whiteSpace: 'normal',
}

const blockTypeActions = [
  { name: '普通文本', value: 'plain_text', color: '#1989fa' },
  { name: '富文本', value: 'rich_text', color: '#07c160' },
  { name: '代码块', value: 'code_block', color: '#7232dd' },
]

onMounted(async () => {
  await loadNote()
})

onBeforeRouteLeave(async () => {
  if (!hasUnsavedChanges()) return true
  try {
    await showConfirmDialog({
      title: '放弃未保存修改？',
      message: '当前卡片集还有未保存的修改，离开后会丢失。',
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch (error) {
    return false
  }
})

window.addEventListener('beforeunload', preventUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', preventUnload))

async function loadNote() {
  errorText.value = ''
  try {
    await notes.loadNote(route.params.id)
    normalizeCards()
    title.value = notes.note?.title || ''
    savedSnapshot.value = snapshot()
  } catch (error) {
    errorText.value = errorMessage(error, '加载卡片集失败，请重试')
  }
}

function syncTitle() {
  if (notes.note) notes.note.title = title.value || '未命名卡片集'
}

function addCard() {
  notes.cards.push({
    id: '',
    clientKey: newClientKey('card'),
    title: '新卡片',
    sortOrder: notes.cards.length,
    blocks: [],
  })
}

function addBlock(card) {
  card.blocks = card.blocks || []
  card.blocks.push({
    id: '',
    clientKey: newClientKey('block'),
    type: 'plain_text',
    content: '<p><br></p>',
    sortOrder: card.blocks.length,
  })
}

async function removeCard(card) {
  try {
    await showConfirmDialog({
      title: '删除卡片',
      message: '这张卡片和里面的所有行块都会在保存后删除。',
      confirmButtonText: '删除',
      confirmButtonColor: '#ee0a24',
    })
  } catch (error) {
    return
  }
  notes.cards = notes.cards
    .filter((item) => item !== card)
    .map((item, index) => ({ ...item, sortOrder: index }))
}

function removeBlock(card, block) {
  card.blocks = card.blocks
    .filter((item) => item !== block)
    .map((item, index) => ({ ...item, sortOrder: index }))
}

function removeEmptyBlock(card, block, event) {
  if ((block.type === 'code_block' ? codeText(block.content) : htmlToText(block.content)).trim()) return
  event.preventDefault()
  removeBlock(card, block)
}

function startTypePress(block) {
  cancelTypePress()
  typePressTimer = window.setTimeout(() => openTypeSheet(block), 450)
}

function cancelTypePress() {
  if (!typePressTimer) return
  window.clearTimeout(typePressTimer)
  typePressTimer = null
}

function openTypeSheet(block) {
  cancelTypePress()
  activeTypeBlock.value = block
  typeSheetVisible.value = true
}

function selectBlockType(action) {
  if (activeTypeBlock.value) {
    activeTypeBlock.value.type = action.value
    if (action.value === 'rich_text' || action.value === 'plain_text') {
      activeTypeBlock.value.content = textToHtml(activeTypeBlock.value.content)
      activeRichBlock.value = activeTypeBlock.value
    } else if (action.value === 'code_block') {
      activeTypeBlock.value.content = stringifyCodeContent({ language: 'plain', code: htmlToText(activeTypeBlock.value.content) })
    }
  }
  typeSheetVisible.value = false
}

function blockTypeColor(type) {
  return blockTypeActions.find((item) => item.value === type)?.color || '#1989fa'
}

function blockPlaceholder(type) {
  if (type === 'rich_text') return '输入富文本'
  if (type === 'code_block') return '输入代码'
  return '输入内容'
}

function blockFieldStyle(type) {
  if (type !== 'code_block') return undefined
  return {
    fontFamily: 'monospace',
    background: '#f7f8fa',
    borderRadius: '6px',
  }
}

function blockKey(block) {
  return block.clientKey || block.id
}

function syncRichHtml(block, event) {
  block.content = sanitizeHtml(event.currentTarget.innerHTML)
}

function removeEmptyRichBlock(card, block, event) {
  if (htmlToText(block.content).trim()) return
  event.preventDefault()
  removeBlock(card, block)
}

function applyRichCommand(command, value = null) {
  document.execCommand(command, false, value)
  if (activeRichBlock.value) {
    const selection = window.getSelection()
    const node = selection?.anchorNode?.parentElement
    const editor = node?.closest?.('[contenteditable="true"]')
    if (editor) activeRichBlock.value.content = sanitizeHtml(editor.innerHTML)
  }
}

function safeHtml(content) {
  return sanitizeHtml(content || '<p><br></p>')
}

async function save() {
  syncTitle()
  try {
    normalizeCards()
    await notes.saveCurrent()
    normalizeCards()
    savedSnapshot.value = snapshot()
    showSuccessToast('已保存')
  } catch (error) {
    showFailToast(errorMessage(error, '保存失败，请重试'))
  }
}

function normalizeCards() {
  notes.cards = (notes.cards || []).map((card, cardIndex) => ({
    ...card,
    clientKey: card.clientKey || newClientKey('card'),
    title: card.title || '未命名卡片',
    sortOrder: cardIndex,
    blocks: (card.blocks || []).map((block, blockIndex) => ({
      ...block,
      clientKey: block.clientKey || newClientKey('block'),
      type: block.type || 'plain_text',
      content: normalizeBlockContent(block),
      sortOrder: blockIndex,
    })),
  }))
}

function snapshot() {
  return JSON.stringify({
    title: title.value || '未命名卡片集',
    cards: notes.cards.map((card, cardIndex) => ({
      id: card.id,
      title: card.title || '未命名卡片',
      sortOrder: cardIndex,
      blocks: (card.blocks || []).map((block, blockIndex) => ({
        id: block.id,
        type: block.type || 'plain_text',
        content: block.content || '',
        sortOrder: blockIndex,
      })),
    })),
  })
}

function hasUnsavedChanges() {
  if (!notes.note) return false
  return snapshot() !== savedSnapshot.value
}

function preventUnload(event) {
  if (!hasUnsavedChanges()) return
  event.preventDefault()
  event.returnValue = ''
}

function newClientKey(prefix) {
  clientId += 1
  return `${prefix}-${Date.now()}-${clientId}`
}
</script>
