<template>
  <main class="page">
    <van-nav-bar title="RainNote" left-text="连接" left-arrow @click-left="router.push('/connect')" />
    <van-search v-model="keyword" placeholder="搜索便签" />

    <van-empty v-if="!filteredNotes.length && !notes.loading" description="暂无便签" />
    <van-list v-else>
      <van-swipe-cell v-for="note in filteredNotes" :key="note.id">
        <van-cell :title="note.title" :label="formatTime(note.updatedAt)" is-link @click="router.push(`/notes/${note.id}`)" />
        <template #right>
          <van-button square type="danger" text="删除" @click="remove(note.id)" />
        </template>
      </van-swipe-cell>
    </van-list>

    <van-floating-bubble axis="xy" icon="plus" magnetic="x" @click="create" />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { useNotesStore } from '../stores/notes'

const router = useRouter()
const notes = useNotesStore()
const keyword = ref('')

const filteredNotes = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return notes.notes
  return notes.notes.filter((note) => note.title.toLowerCase().includes(value))
})

onMounted(() => notes.loadNotes())

async function create() {
  const title = window.prompt('便签标题', '未命名便签')
  if (title === null) return
  const note = await notes.createNote(title)
  showSuccessToast('已创建')
  router.push(`/notes/${note.id}`)
}

async function remove(noteId) {
  await showConfirmDialog({ title: '删除便签？', message: '便签内的卡片和行块都会删除。' })
  await notes.deleteNote(noteId)
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : ''
}
</script>
