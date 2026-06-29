<template>
  <el-container>
    <el-aside width="320px">
      <el-card header="RainNote Studio">
        <p>协同卡片集控制台</p>
        <el-form label-position="top">
          <el-form-item label="服务地址">
            <el-input v-model="connection.baseUrl" placeholder="http://手机IP:48622" />
          </el-form-item>
          <el-form-item label="访问码">
            <el-input v-model="connection.token" placeholder="访问码" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="connecting" @click="connect">连接服务</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="errorText" :title="errorText" type="error" show-icon :closable="false" />
      </el-card>

      <el-card header="卡片集">
        <el-button type="primary" :disabled="!connection.connected" @click="create">新建卡片集</el-button>
        <el-input v-model="keyword" placeholder="搜索卡片集" clearable />
        <el-menu :default-active="currentId" @select="open">
          <el-menu-item
            v-for="note in filteredNotes"
            :key="note.id"
            :index="note.id"
          >
            <span>{{ note.title }}</span>
          </el-menu-item>
        </el-menu>
          <el-empty v-if="connection.connected && !filteredNotes.length" :description="keyword ? '没有匹配的卡片集' : '暂无卡片集'">
            <el-button type="primary" @click="keyword ? (keyword = '') : create()">{{ keyword ? '清空搜索' : '新建卡片集' }}</el-button>
          </el-empty>
      </el-card>
    </el-aside>

    <el-container>
      <el-header>
        <el-row align="middle" justify="space-between">
          <el-col :span="14">
            <el-input v-model="title" size="large" placeholder="选择或新建一个卡片集" :disabled="!notes.note" @blur="syncTitle" />
          </el-col>
          <el-col :span="10">
          <el-tag v-if="dirty" type="warning" effect="light">未保存</el-tag>
          <el-button type="danger" plain size="large" :disabled="!notes.note" @click="removeCurrentNote">删除卡片集</el-button>
          <el-button type="primary" size="large" :disabled="!notes.note" :loading="notes.saving" @click="save">保存全部</el-button>
          </el-col>
        </el-row>
      </el-header>

      <el-main>
      <el-empty v-if="!notes.note" :description="connection.connected ? '选择或新建一个卡片集' : '连接服务后选择一个卡片集'">
        <el-button v-if="connection.connected" type="primary" @click="create">新建卡片集</el-button>
      </el-empty>
      <el-row v-else :gutter="16">
        <el-col v-for="card in notes.cards" :key="card.clientKey || card.id" :span="12">
          <el-card>
            <template #header>
              <el-row justify="space-between" align="middle">
                <el-col :span="16">
                  <el-input v-model="card.title" placeholder="卡片标题" />
                </el-col>
                <el-col :span="8">
            <el-button text type="danger" @click="removeCard(card)">删除卡片</el-button>
                </el-col>
              </el-row>
            </template>
            <el-empty v-if="!card.blocks?.length" :image-size="72" description="暂无行块" />
            <el-card v-for="block in card.blocks" :key="block.clientKey || block.id">
              <el-select v-model="block.type" size="small">
                <el-option label="文本" value="plain_text" />
                <el-option label="富文" value="rich_text" />
                <el-option label="代码" value="code_block" />
              </el-select>
              <el-input v-model="block.content" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" placeholder="写点什么..." />
              <el-button text type="danger" @click="removeBlock(card, block)">删除</el-button>
            </el-card>
          <el-button plain type="primary" @click="addBlock(card)">添加行块</el-button>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <el-empty description="添加新卡片">
              <el-button type="primary" @click="addCard">添加卡片</el-button>
            </el-empty>
          </el-card>
        </el-col>
      </el-row>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useConnectionStore } from '../stores/connection'
import { useNotesStore } from '../stores/notes'
import { health } from '../api/notes'
import { errorMessage } from '../api/client'

const connection = useConnectionStore()
const notes = useNotesStore()
const connecting = ref(false)
const keyword = ref('')
const currentId = ref('')
const title = ref('')
const errorText = ref('')
const savedSnapshot = ref('')
let clientId = 0

const dirty = computed(() => Boolean(notes.note) && snapshot() !== savedSnapshot.value)

const filteredNotes = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return notes.notes
  return notes.notes.filter((note) => note.title.toLowerCase().includes(value))
})

async function connect() {
  connecting.value = true
  errorText.value = ''
  try {
    connection.normalize()
    connection.device = await health()
    connection.connected = true
    connection.save()
    await notes.loadNotes()
    if (!currentId.value && notes.notes[0]) await open(notes.notes[0].id)
    ElMessage.success('连接成功')
  } catch (error) {
    errorText.value = errorMessage(error, '连接失败，请检查地址和访问码')
    ElMessage.error(errorText.value)
  } finally {
    connecting.value = false
  }
}

async function create() {
  if (!(await confirmDiscard())) return
  try {
    const { value } = await ElMessageBox.prompt('请输入卡片集标题', '新建卡片集', {
      inputValue: '未命名卡片集',
      inputValidator: (value) => Boolean(value?.trim()) || '标题不能为空',
    })
    const note = await notes.createNote(value.trim())
    await open(note.id, true)
    ElMessage.success('已创建')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '创建失败，请重试'))
  }
}

async function open(id, force = false) {
  if (!force && !(await confirmDiscard())) return
  currentId.value = id
  try {
    await notes.loadNote(id)
    normalizeCards()
    title.value = notes.note?.title || ''
    savedSnapshot.value = snapshot()
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载失败，请重试'))
  }
}

function syncTitle() {
  if (notes.note) notes.note.title = title.value || '未命名卡片集'
}

function addCard() {
  notes.cards.push({ id: '', clientKey: newClientKey('card'), title: '新卡片', sortOrder: notes.cards.length, blocks: [] })
}

function addBlock(card) {
  card.blocks = card.blocks || []
  card.blocks.push({ id: '', clientKey: newClientKey('block'), type: 'plain_text', content: '', sortOrder: card.blocks.length })
}

async function removeCard(card) {
  try {
    await ElMessageBox.confirm('这张卡片和里面的所有行块都会在保存后删除。', '删除卡片', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
    notes.cards = notes.cards.filter((item) => item !== card).map((item, index) => ({ ...item, sortOrder: index }))
  } catch (error) {
  }
}

function removeBlock(card, block) {
  card.blocks = card.blocks.filter((item) => item !== block).map((item, index) => ({ ...item, sortOrder: index }))
}

async function save() {
  syncTitle()
  try {
    normalizeCards()
    await notes.saveCurrent()
    normalizeCards()
    savedSnapshot.value = snapshot()
    ElMessage.success('已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败，请重试'))
  }
}

async function removeCurrentNote() {
  if (!notes.note) return
  try {
    await ElMessageBox.confirm('卡片集内的卡片和行块都会删除，此操作无法恢复。', '删除卡片集', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
    await notes.deleteNote(notes.note.id)
    notes.note = null
    notes.cards = []
    title.value = ''
    currentId.value = ''
    savedSnapshot.value = ''
    ElMessage.success('已删除')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '删除失败，请重试'))
  }
}

async function confirmDiscard() {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('当前卡片集还有未保存修改，继续会丢失这些修改。', '放弃修改？', {
      type: 'warning',
      confirmButtonText: '继续',
      cancelButtonText: '留在当前页',
    })
    return true
  } catch (error) {
    return false
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
      content: block.content || '',
      sortOrder: blockIndex,
    })),
  }))
}

function snapshot() {
  if (!notes.note) return ''
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

function preventUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function newClientKey(prefix) {
  clientId += 1
  return `${prefix}-${Date.now()}-${clientId}`
}

window.addEventListener('beforeunload', preventUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', preventUnload))

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : ''
}
</script>
