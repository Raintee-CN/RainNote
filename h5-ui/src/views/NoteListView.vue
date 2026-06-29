<template>
  <main>
    <van-nav-bar title="卡片集库">
      <template #right>
        <van-button v-if="!connection.embedded" size="small" plain type="primary" @click="router.push('/connect')">连接</van-button>
      </template>
    </van-nav-bar>

    <van-search v-model="keyword" placeholder="搜索标题" />

    <van-notice-bar v-if="errorText" wrapable :scrollable="false" type="danger">
      {{ errorText }}
      <template #right-icon>
        <van-button size="mini" type="danger" plain @click="loadNotes">重试</van-button>
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

    <van-list v-if="filteredNotes.length">
      <van-swipe-cell v-for="note in filteredNotes" :key="note.id">
        <van-cell :title="note.title" :label="formatTime(note.updatedAt)" is-link @click="router.push(`/notes/${note.id}`)" />
        <template #right>
          <van-button square type="danger" text="删除" :loading="deletingId === note.id" @click="remove(note.id)" />
        </template>
      </van-swipe-cell>
    </van-list>

    <van-cell-group inset>
      <van-button block type="primary" @click="openCreateDialog">新建卡片集</van-button>
    </van-cell-group>

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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showDialog, showFailToast, showSuccessToast } from 'vant'
import { useConnectionStore } from '../stores/connection'
import { useNotesStore } from '../stores/notes'
import { errorMessage } from '../api/client'

const router = useRouter()
const connection = useConnectionStore()
const notes = useNotesStore()
const keyword = ref('')
const errorText = ref('')
const deletingId = ref('')
const createDialogVisible = ref(false)
const newTitle = ref('未命名卡片集')

const filteredNotes = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return notes.notes
  return notes.notes.filter((note) => note.title.toLowerCase().includes(value))
})

onMounted(async () => {
  await loadNotes()
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
