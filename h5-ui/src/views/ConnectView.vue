<template>
  <main>
    <van-nav-bar title="连接服务" />
    <van-cell-group inset title="RainNote Mobile">
      <van-cell title="把手机里的卡片集，交给浏览器管理" label="打开 Android 设置页里的协同服务地址，并输入访问码。" />
    </van-cell-group>

    <van-form @submit="connect">
      <van-cell-group inset title="服务信息">
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
      <van-notice-bar v-if="errorText" wrapable :scrollable="false" type="danger" :text="errorText" />
      <van-cell-group inset>
        <van-button block round type="primary" native-type="submit" :loading="loading">连接</van-button>
      </van-cell-group>
    </van-form>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { useConnectionStore } from '../stores/connection'
import { health } from '../api/notes'
import { errorMessage } from '../api/client'

const router = useRouter()
const connection = useConnectionStore()
const loading = ref(false)
const errorText = ref('')

async function connect() {
  errorText.value = ''
  connection.normalize()
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
