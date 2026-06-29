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
          <van-field
            v-for="block in card.blocks"
            :key="block.clientKey || block.id"
            v-model="block.content"
            type="textarea"
            autosize
            rows="2"
            :label="block.type"
            placeholder="输入内容"
          >
            <template #button>
              <van-button size="small" type="danger" plain @click="removeBlock(card, block)">删除</van-button>
            </template>
          </van-field>
          <van-button block plain type="primary" @click="addBlock(card)">添加行块</van-button>
      </van-cell-group>

      <van-cell-group inset>
        <van-button block plain type="primary" @click="addCard">添加卡片</van-button>
      </van-cell-group>

      <van-action-bar>
        <van-action-bar-button type="primary" :loading="notes.saving" text="保存全部" @click="save" />
      </van-action-bar>
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

const route = useRoute()
const router = useRouter()
const notes = useNotesStore()
const title = ref('')
const errorText = ref('')
const savedSnapshot = ref('')
let clientId = 0

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
    content: '',
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
