<template>
  <main class="mobile-shell editor-page">
    <van-nav-bar title="编辑便签" left-text="返回" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span class="save-link" @click="save">保存</span>
      </template>
    </van-nav-bar>

    <van-skeleton :loading="notes.loading" title :row="6">
      <van-cell-group inset class="glass-group title-group" ref="titleGroupRef">
        <van-field v-model="title" label="标题" placeholder="便签标题" @blur="syncTitle" />
      </van-cell-group>

      <section v-for="card in notes.cards" :key="card.id" class="card-editor">
        <div class="card-title-row">
          <span>卡片</span>
          <input v-model="card.title" placeholder="卡片标题" />
        </div>
        <van-cell-group inset>
          <div v-for="block in card.blocks" :key="block.id" class="block-editor">
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
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { useNotesStore } from '../stores/notes'
import { runMotion } from '../utils/motion'

const route = useRoute()
const router = useRouter()
const notes = useNotesStore()
const title = ref('')
const titleGroupRef = ref(null)

onMounted(async () => {
  await notes.loadNote(route.params.id)
  title.value = notes.note?.title || ''
  await nextTick()
  runMotion(({ animate, stagger }) => {
    animate(titleGroupRef.value?.$el || titleGroupRef.value, { opacity: [0, 1], y: [18, 0], duration: 560, ease: 'outExpo' })
    animate('.card-editor', { opacity: [0, 1], y: [30, 0], scale: [0.98, 1], duration: 680, delay: stagger(80), ease: 'outExpo' })
  })
})

function syncTitle() {
  if (notes.note) notes.note.title = title.value || '未命名便签'
}

function addCard() {
  notes.cards.push({
    id: '',
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

function removeBlock(card, block) {
  card.blocks = card.blocks.filter((item) => item !== block)
}

async function save() {
  syncTitle()
  await notes.saveCurrent()
  runMotion(({ animate }) => {
    animate('.save-link, .action-area .van-button:last-child', { scale: [1, 1.08, 1], duration: 460, ease: 'outBack' })
  })
  showSuccessToast('已保存')
}
</script>
