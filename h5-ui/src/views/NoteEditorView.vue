<template>
  <main class="page editor-page">
    <van-nav-bar title="编辑便签" left-text="返回" left-arrow @click-left="router.back()">
      <template #right>
        <span class="save-link" @click="save">保存</span>
      </template>
    </van-nav-bar>

    <van-skeleton :loading="notes.loading" title :row="6">
      <van-cell-group inset>
        <van-field v-model="title" label="标题" placeholder="便签标题" @blur="syncTitle" />
      </van-cell-group>

      <section v-for="card in notes.cards" :key="card.id" class="card-editor">
        <van-field v-model="card.title" label="卡片" placeholder="卡片标题" />
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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { useNotesStore } from '../stores/notes'

const route = useRoute()
const router = useRouter()
const notes = useNotesStore()
const title = ref('')

onMounted(async () => {
  await notes.loadNote(route.params.id)
  title.value = notes.note?.title || ''
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
}

function addBlock(card) {
  card.blocks = card.blocks || []
  card.blocks.push({
    id: '',
    type: 'plain_text',
    content: '',
    sortOrder: card.blocks.length,
  })
}

function removeBlock(card, block) {
  card.blocks = card.blocks.filter((item) => item !== block)
}

async function save() {
  syncTitle()
  await notes.saveCurrent()
  showSuccessToast('已保存')
}
</script>
