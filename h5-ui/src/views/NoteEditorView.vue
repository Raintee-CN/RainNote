<template>
  <main class="mobile-shell editor-page">
    <van-nav-bar title="编辑卡片集" left-text="返回" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span class="save-link" @click="save">保存</span>
      </template>
    </van-nav-bar>

    <van-skeleton :loading="notes.loading" title :row="6">
      <van-cell-group inset class="glass-group title-group" ref="titleGroupRef">
        <van-field v-model="title" label="标题" placeholder="卡片集标题" @blur="syncTitle" />
      </van-cell-group>

      <van-notice-bar v-if="errorText" class="inline-error" wrapable :scrollable="false" type="danger">
        {{ errorText }}
        <template #right-icon>
          <button class="notice-action" type="button" @click="loadNote">重试</button>
        </template>
      </van-notice-bar>

      <van-empty v-if="!notes.loading && !notes.note" description="卡片集不存在或已被删除">
        <van-button round type="primary" @click="router.replace('/notes')">返回卡片集库</van-button>
      </van-empty>

      <section v-for="card in notes.cards" :key="card.clientKey || card.id" class="card-editor">
        <div class="card-title-row">
          <span>卡片</span>
          <input v-model="card.title" placeholder="卡片标题" />
          <button type="button" class="card-delete-button" @click="removeCard(card)">删除</button>
        </div>
        <van-cell-group inset>
          <van-empty v-if="!card.blocks?.length" image-size="72" description="这张卡片还没有内容" />
          <div v-for="block in card.blocks" :key="block.clientKey || block.id" class="block-editor">
            <van-field v-model="block.content" type="textarea" autosize rows="2" placeholder="输入内容" />
            <div class="block-actions">
              <van-tag plain>{{ block.type }}</van-tag>
              <van-button size="mini" type="danger" plain @click="removeBlock(card, block)">删除</van-button>
            </div>
          </div>
          <van-button block plain type="primary" @click="addBlock(card)">添加行块</van-button>
        </van-cell-group>
      </section>

      <div class="action-area">
        <van-button block plain type="primary" @click="addCard">添加卡片</van-button>
        <van-button block round type="primary" :loading="notes.saving" @click="save">保存全部</van-button>
      </div>
    </van-skeleton>
  </main>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { onBeforeRouteLeave } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { useNotesStore } from '../stores/notes'
import { errorMessage } from '../api/client'
import { runMotion } from '../utils/motion'

const route = useRoute()
const router = useRouter()
const notes = useNotesStore()
const title = ref('')
const titleGroupRef = ref(null)
const errorText = ref('')
const savedSnapshot = ref('')
let clientId = 0

onMounted(async () => {
  await loadNote()
  await nextTick()
  runMotion(({ animate, stagger }) => {
    animate(titleGroupRef.value?.$el || titleGroupRef.value, { opacity: [0, 1], y: [18, 0], duration: 560, ease: 'outExpo' })
    animate('.card-editor', { opacity: [0, 1], y: [30, 0], scale: [0.98, 1], duration: 680, delay: stagger(80), ease: 'outExpo' })
  })
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
  nextTick(() => {
    runMotion(({ animate }) => {
      animate('.card-editor:last-of-type', { opacity: [0, 1], y: [34, 0], scale: [0.94, 1], duration: 580, ease: 'outBack' })
    })
  })
}

function addBlock(card) {
  card.blocks = card.blocks || []
  card.blocks.push({
    id: '',
    clientKey: newClientKey('block'),
    type: 'plain_text',
    content: '',
    sortOrder: card.blocks.length,
  })
  nextTick(() => {
    runMotion(({ animate }) => {
      animate('.block-editor:last-of-type', { opacity: [0, 1], x: [18, 0], duration: 440, ease: 'outExpo' })
    })
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

async function save() {
  syncTitle()
  try {
    normalizeCards()
    await notes.saveCurrent()
    normalizeCards()
    savedSnapshot.value = snapshot()
    runMotion(({ animate }) => {
      animate('.save-link, .action-area .van-button:last-child', { scale: [1, 1.08, 1], duration: 460, ease: 'outBack' })
    })
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
