<template>
  <main class="pc-shell">
    <aside class="sidebar">
      <section class="brand-card">
        <div class="brand-mark">雨</div>
        <div>
          <p>RainNote Studio</p>
          <h1>协同便签控制台</h1>
        </div>
      </section>

      <section class="connect-card">
        <el-input v-model="connection.baseUrl" size="large" placeholder="http://手机IP:48622" />
        <el-input v-model="connection.token" size="large" placeholder="访问码" show-password />
        <el-button type="primary" size="large" :loading="connecting" @click="connect">连接服务</el-button>
      </section>

      <section class="note-panel">
        <div class="panel-head">
          <span>便签</span>
          <el-button text type="primary" @click="create">新建</el-button>
        </div>
        <el-input v-model="keyword" placeholder="搜索便签" clearable />
        <div class="note-list">
          <button
            v-for="note in filteredNotes"
            :key="note.id"
            class="note-row"
            :class="{ active: currentId === note.id }"
            @click="open(note.id)"
          >
            <strong>{{ note.title }}</strong>
            <small>{{ formatTime(note.updatedAt) }}</small>
          </button>
        </div>
      </section>
    </aside>

    <section class="workspace">
      <header class="workspace-head">
        <div>
          <p class="eyebrow">EDITOR</p>
          <input v-model="title" class="title-input" placeholder="选择或新建一条便签" @blur="syncTitle" />
        </div>
        <el-button type="primary" size="large" :loading="notes.saving" @click="save">保存全部</el-button>
      </header>

      <el-empty v-if="!notes.note" description="连接服务后选择一条便签" />
      <div v-else class="card-grid">
        <article v-for="card in notes.cards" :key="card.id" class="paper-card">
          <input v-model="card.title" class="card-title" placeholder="卡片标题" />
          <div class="block-list">
            <div v-for="block in card.blocks" :key="block.id" class="block-row">
              <el-select v-model="block.type" class="block-type" size="small">
                <el-option label="文本" value="plain_text" />
                <el-option label="富文" value="rich_text" />
                <el-option label="代码" value="code_block" />
              </el-select>
              <el-input v-model="block.content" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" placeholder="写点什么..." />
              <el-button text type="danger" @click="removeBlock(card, block)">删除</el-button>
            </div>
          </div>
          <el-button plain type="primary" @click="addBlock(card)">添加行块</el-button>
        </article>
        <button class="add-card" @click="addCard">+ 新卡片</button>
      </div>
    </section>

    <aside class="inspector">
      <section class="status-card">
        <p class="eyebrow">SERVICE</p>
        <h3>{{ connection.connected ? '已连接' : '未连接' }}</h3>
        <p>{{ connection.baseUrl }}</p>
      </section>
      <section class="status-card warm">
        <p class="eyebrow">WEB</p>
        <h3>/web-pc</h3>
        <p>桌面端使用 Element Plus；移动端请访问 /web-mobile。</p>
      </section>
    </aside>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useConnectionStore } from '../stores/connection'
import { useNotesStore } from '../stores/notes'
import { health } from '../api/notes'

const connection = useConnectionStore()
const notes = useNotesStore()
const connecting = ref(false)
const keyword = ref('')
const currentId = ref('')
const title = ref('')

const filteredNotes = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return notes.notes
  return notes.notes.filter((note) => note.title.toLowerCase().includes(value))
})

async function connect() {
  connecting.value = true
  try {
    connection.device = await health()
    connection.connected = true
    connection.save()
    await notes.loadNotes()
    ElMessage.success('连接成功')
  } catch (error) {
    ElMessage.error('连接失败，请检查地址和访问码')
  } finally {
    connecting.value = false
  }
}

async function create() {
  const { value } = await ElMessageBox.prompt('请输入便签标题', '新建便签', {
    inputValue: '未命名便签',
  })
  const note = await notes.createNote(value)
  await open(note.id)
}

async function open(id) {
  currentId.value = id
  await notes.loadNote(id)
  title.value = notes.note?.title || ''
}

function syncTitle() {
  if (notes.note) notes.note.title = title.value || '未命名便签'
}

function addCard() {
  notes.cards.push({ id: '', title: '新卡片', sortOrder: notes.cards.length, blocks: [] })
}

function addBlock(card) {
  card.blocks = card.blocks || []
  card.blocks.push({ id: '', type: 'plain_text', content: '', sortOrder: card.blocks.length })
}

function removeBlock(card, block) {
  card.blocks = card.blocks.filter((item) => item !== block)
}

async function save() {
  syncTitle()
  await notes.saveCurrent()
  ElMessage.success('已保存')
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : ''
}
</script>
