<template>
  <main class="page connect-page">
    <section class="hero-card">
      <p class="eyebrow">RainNote 协同管理</p>
      <h1>连接手机端服务</h1>
      <p class="hint">在手机和当前设备处于同一局域网时，输入 Android 端服务地址。</p>
    </section>

    <van-form @submit="connect">
      <van-cell-group inset>
        <van-field v-model="connection.baseUrl" label="服务地址" placeholder="http://192.168.0.8:48622" />
        <van-field v-model="connection.token" label="访问码" placeholder="可选" />
      </van-cell-group>
      <div class="action-area">
        <van-button block round type="primary" native-type="submit" :loading="loading">连接</van-button>
      </div>
    </van-form>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { useConnectionStore } from '../stores/connection'
import { health } from '../api/notes'

const router = useRouter()
const connection = useConnectionStore()
const loading = ref(false)

async function connect() {
  loading.value = true
  try {
    const device = await health()
    connection.device = device
    connection.connected = true
    connection.save()
    showSuccessToast('连接成功')
    router.replace('/notes')
  } catch (error) {
    showFailToast('连接失败，请检查地址')
  } finally {
    loading.value = false
  }
}
</script>
