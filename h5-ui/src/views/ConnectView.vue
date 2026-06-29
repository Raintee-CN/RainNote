<template>
  <main class="mobile-shell connect-page">
    <section class="mobile-hero connect-hero" ref="heroRef">
      <div class="hero-orb"></div>
      <div class="hero-orb hero-orb-small"></div>
      <p class="eyebrow">RAINNOTE MOBILE</p>
      <h1>把手机里的卡片集，交给浏览器管理</h1>
      <p class="hint">打开 Android 设置页里的协同服务地址，并输入访问码。</p>
    </section>

    <van-form ref="formRef" @submit="connect">
      <van-cell-group inset class="glass-group">
        <van-field
          v-model="connection.baseUrl"
          label="服务地址"
          placeholder="http://192.168.0.8:48622"
          clearable
          :rules="[{ required: true, message: '请输入服务地址' }]"
        />
        <van-field
          v-model="connection.token"
          label="访问码"
          placeholder="设置页显示的 6 位数字"
          maxlength="6"
          clearable
          :rules="[{ required: true, message: '请输入访问码' }]"
        />
      </van-cell-group>
      <van-notice-bar v-if="errorText" class="inline-error" wrapable :scrollable="false" type="danger" :text="errorText" />
      <div class="action-area">
        <van-button block round type="primary" native-type="submit" :loading="loading">连接</van-button>
      </div>
    </van-form>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { useConnectionStore } from '../stores/connection'
import { health } from '../api/notes'
import { errorMessage } from '../api/client'
import { runMotion } from '../utils/motion'

const router = useRouter()
const connection = useConnectionStore()
const loading = ref(false)
const errorText = ref('')
const heroRef = ref(null)
const formRef = ref(null)

onMounted(() => {
  runMotion(({ animate, createTimeline }) => {
    createTimeline({ defaults: { ease: 'outExpo' } })
      .add(heroRef.value, { opacity: [0, 1], y: [28, 0], scale: [0.96, 1], duration: 760 })
      .add('.connect-hero .eyebrow, .connect-hero h1, .connect-hero .hint', { opacity: [0, 1], y: [18, 0], duration: 620, delay: (_, i) => i * 90 }, '-=460')
      .add(formRef.value?.$el || formRef.value, { opacity: [0, 1], y: [24, 0], duration: 620 }, '-=360')

    animate('.connect-hero .hero-orb', {
      x: [0, -18, 8, 0],
      y: [0, 16, -10, 0],
      scale: [1, 1.08, 0.96, 1],
      duration: 5200,
      loop: true,
      ease: 'inOutSine',
    })
  })
})

async function connect() {
  errorText.value = ''
  connection.normalize()
  runMotion(({ animate }) => {
    animate('.action-area .van-button', { scale: [1, 0.97, 1], duration: 420, ease: 'outBack' })
  })
  loading.value = true
  try {
    const device = await health()
    connection.device = device
    connection.connected = true
    connection.save()
    showSuccessToast('连接成功')
    router.replace('/notes')
  } catch (error) {
    errorText.value = errorMessage(error, '连接失败，请检查地址和访问码')
    showFailToast(errorText.value)
  } finally {
    loading.value = false
  }
}
</script>
