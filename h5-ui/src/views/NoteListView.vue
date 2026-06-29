<template>
  <main class="mobile-shell notes-page">
    <header class="mobile-topbar" ref="topbarRef">
      <div>
        <p class="eyebrow">RAINNOTE</p>
        <h2>卡片集库</h2>
      </div>
      <van-button v-if="!connection.embedded" size="small" round plain type="primary" @click="router.push('/connect')">连接</van-button>
    </header>

    <van-search ref="searchRef" v-model="keyword" shape="round" background="transparent" placeholder="搜索标题" />

    <van-notice-bar v-if="errorText" class="inline-error" wrapable :scrollable="false" type="danger">
      {{ errorText }}
      <template #right-icon>
        <button class="notice-action" type="button" @click="loadNotes">重试</button>
      </template>
    </van-notice-bar>

    <van-skeleton :loading="notes.loading" title :row="5">
      <van-empty
        v-if="!filteredNotes.length"
        :description="keyword.trim() ? '没有匹配的卡片集' : '暂无卡片集，创建第一条吧'"
      >
        <van-button round type="primary" @click="keyword.trim() ? (keyword = '') : openCreateDialog()">
          {{ keyword.trim() ? '清空搜索' : '新建卡片集' }}
        </van-button>
      </van-empty>
    </van-skeleton>

    <van-list v-if="filteredNotes.length" class="note-stack">
      <van-swipe-cell v-for="note in filteredNotes" :key="note.id">
        <article class="note-tile" @click="router.push(`/notes/${note.id}`)">
          <div class="note-pin"></div>
          <div class="note-glow"></div>
          <h3>{{ note.title }}</h3>
          <p>{{ formatTime(note.updatedAt) }}</p>
        </article>
        <template #right>
          <van-button square type="danger" text="删除" :loading="deletingId === note.id" @click="remove(note.id)" />
        </template>
      </van-swipe-cell>
    </van-list>

    <van-floating-bubble axis="xy" icon="plus" magnetic="x" @click="openCreateDialog" />

    <van-dialog
      v-model:show="createDialogVisible"
      title="新建卡片集"
      show-cancel-button
      confirm-button-text="创建"
      :before-close="beforeCreateClose"
    >
      <van-field v-model="newTitle" autofocus label="标题" placeholder="未命名卡片集" maxlength="40" clearable />
    </van-dialog>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showDialog, showFailToast, showSuccessToast } from 'vant'
import { useConnectionStore } from '../stores/connection'
import { useNotesStore } from '../stores/notes'
import { errorMessage } from '../api/client'
import { runMotion } from '../utils/motion'

const router = useRouter()
const connection = useConnectionStore()
const notes = useNotesStore()
const keyword = ref('')
const errorText = ref('')
const deletingId = ref('')
const createDialogVisible = ref(false)
const newTitle = ref('未命名卡片集')
const topbarRef = ref(null)
const searchRef = ref(null)

const filteredNotes = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return notes.notes
  return notes.notes.filter((note) => note.title.toLowerCase().includes(value))
})

onMounted(async () => {
  runMotion(({ createTimeline }) => {
    createTimeline({ defaults: { ease: 'outExpo' } })
      .add(topbarRef.value, { opacity: [0, 1], y: [-18, 0], duration: 640 })
      .add(searchRef.value?.$el || searchRef.value, { opacity: [0, 1], y: [18, 0], duration: 560 }, '-=420')
  })

  await loadNotes()
  await nextTick()
  animateNotes()
})

watch(filteredNotes, async () => {
  await nextTick()
  animateNotes()
})

function openCreateDialog() {
  newTitle.value = '未命名卡片集'
  createDialogVisible.value = true
}

async function beforeCreateClose(action) {
  if (action !== 'confirm') return true
  await create()
  return true
}

async function create() {
  try {
    const note = await notes.createNote(newTitle.value.trim() || '未命名卡片集')
    showSuccessToast('已创建')
    runMotion(({ animate }) => {
      animate('.van-floating-bubble', { rotate: [0, 90], scale: [1, 1.16, 1], duration: 520, ease: 'outBack' })
    })
    router.push(`/notes/${note.id}`)
  } catch (error) {
    showFailToast(errorMessage(error, '创建失败，请重试'))
  }
}

async function loadNotes() {
  errorText.value = ''
  try {
    await notes.loadNotes()
  } catch (error) {
    errorText.value = errorMessage(error, '加载卡片集失败，请重试')
  }
}

function animateNotes() {
  runMotion(({ animate, stagger }) => {
    animate('.note-tile', {
      opacity: [0, 1],
      y: [26, 0],
      rotate: [-1.8, 0],
      duration: 620,
      delay: stagger(70),
      ease: 'outExpo',
    })
  })
}

async function remove(noteId) {
  try {
    await showConfirmDialog({
      title: '删除卡片集？',
      message: '卡片集内的卡片和行块都会删除，此操作保存后无法恢复。',
      confirmButtonText: '删除',
      confirmButtonColor: '#ee0a24',
    })
    deletingId.value = noteId
    await notes.deleteNote(noteId)
    showSuccessToast('已删除')
  } catch (error) {
    if (error !== 'cancel') showDialog({ title: '删除失败', message: errorMessage(error) })
  } finally {
    deletingId.value = ''
  }
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : ''
}
</script>
