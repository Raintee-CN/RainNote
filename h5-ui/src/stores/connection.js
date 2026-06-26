import { defineStore } from 'pinia'

function readSavedConnection() {
  try {
    return JSON.parse(localStorage.getItem('rainnote.connection') || '{}')
  } catch (error) {
    localStorage.removeItem('rainnote.connection')
    return {}
  }
}

const saved = readSavedConnection()
const urlParams = new URLSearchParams(window.location.search)
const embeddedToken = urlParams.get('token') || ''
const embedded = urlParams.get('embedded') === '1'
const defaultBaseUrl = window.location.origin || 'http://127.0.0.1:48622'

export const useConnectionStore = defineStore('connection', {
  state: () => ({
    baseUrl: saved.baseUrl || defaultBaseUrl,
    token: embeddedToken || saved.token || '',
    connected: embedded,
    embedded,
    device: null,
  }),
  actions: {
    normalize() {
      this.baseUrl = (this.baseUrl || defaultBaseUrl).trim().replace(/\/+$/, '')
      this.token = (this.token || '').trim()
    },
    save() {
      this.normalize()
      localStorage.setItem('rainnote.connection', JSON.stringify({
        baseUrl: this.baseUrl,
        token: this.token,
      }))
    },
  },
})
