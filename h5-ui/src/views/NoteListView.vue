<template>
  <main class="mobile-shell notes-page">
    <header class="mobile-topbar" ref="topbarRef">
      <div>
        <p class="eyebrow">RAINNOTE</p>
        <h2>便签库</h2>
      </div>
      <van-button v-if="!connection.embedded" size="small" round plain type="primary" @click="router.push('/connect')">连接</van-button>
    </header>

    <van-search ref="searchRef" v-model="keyword" shape="round" background="transparent" placeholder="搜索标题" />

    <van-empty v-if="!filteredNotes.length && !notes.loading" description="暂无便签" />
    <van-list v-else class="note-stack">
      <van-swipe-cell v-for="note in filteredNotes" :key="note.id">
        <article class="note-tile" @click="router.push(`/notes/${note.id}`)">
          <div class="note-pin"></div>
          <div class="note-glow"></div>
          <h3>{{ note.title }}</h3>
          <p>{{ formatTime(note.updatedAt) }}</p>
        </article>
        <template #right>
          <van-button square type="danger" text="删除" @click="remove(note.id)" />
        </template>
      </van-swipe-cell>
    </van-list>

    <van-floating-bubble axis="xy" icon="plus" magnetic="x" @click="create" />
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { useConnectionStore } from '../stores/connection'
import { useNotesStore } from '../stores/notes'
import { runMotion } from '../utils/motion'

const router = useRouter()
const connection = useConnectionStore()
const notes = useNotesStore()
const keyword = ref('')
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

  await notes.loadNotes()
  await nextTick()
  animateNotes()
})

watch(filteredNotes, async () => {
  await nextTick()
  animateNotes()
})

async function create() {
  const title = window.prompt('便签标题', '未命名便签')
  if (title === null) return
  const note = await notes.createNote(title)
  showSuccessToast('已创建')
  runMotion(({ animate }) => {
    animate('.van-floating-bubble', { rotate: [0, 90], scale: [1, 1.16, 1], duration: 520, ease: 'outBack' })
  })
  router.push(`/notes/${note.id}`)
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
  await showConfirmDialog({ title: '删除便签？', message: '便签内的卡片和行块都会删除。' })
  await notes.deleteNote(noteId)
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : ''
}
</script>
