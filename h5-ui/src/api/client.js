import axios from 'axios'
import { useConnectionStore } from '../stores/connection'

export function apiClient() {
  const connection = useConnectionStore()
  connection.normalize()
  return axios.create({
    baseURL: connection.baseUrl,
    timeout: 12000,
    headers: connection.token ? { 'X-RainNote-Token': connection.token } : {},
  })
}

export function errorMessage(error, fallback = '操作失败，请稍后重试') {
  if (error?.code === 'ECONNABORTED') return '请求超时，请确认手机服务仍在运行'
  if (error?.response?.status === 401) return '访问码不正确，请重新输入设置页里的 6 位数字'
  if (error?.response?.status === 404) return '内容不存在，可能已被删除'
  if (!navigator.onLine) return '当前网络不可用，请检查连接'
  return error?.response?.data?.message || error?.message || fallback
}
