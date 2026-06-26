<template>
  <main class="mobile-shell connect-page">
    <section class="mobile-hero">
      <div class="hero-orb"></div>
      <p class="eyebrow">RAINNOTE MOBILE</p>
      <h1>把手机里的便签，交给浏览器管理</h1>
      <p class="hint">打开 Android 设置页里的协同服务地址，并输入访问码。</p>
    </section>

    <van-form @submit="connect">
      <van-cell-group inset class="glass-group">
        <van-field v-model="connection.baseUrl" label="服务地址" placeholder="http://192.168.0.8:48622" clearable />
        <van-field v-model="connection.token" label="访问码" placeholder="设置页显示的 6 位数字" clearable />
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
