import axios from 'axios'
import { useConnectionStore } from '../stores/connection'

export function apiClient() {
  const connection = useConnectionStore()
  return axios.create({
    baseURL: connection.baseUrl,
    timeout: 10000,
    headers: connection.token ? { 'X-RainNote-Token': connection.token } : {},
  })
}
