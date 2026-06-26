import { defineStore } from 'pinia'

const saved = JSON.parse(localStorage.getItem('rainnote.connection') || '{}')
const urlParams = new URLSearchParams(window.location.search)
const embeddedToken = urlParams.get('token') || ''
const embedded = urlParams.get('embedded') === '1'

export const useConnectionStore = defineStore('connection', {
  state: () => ({
    baseUrl: saved.baseUrl || window.location.origin || 'http://127.0.0.1:48622',
    token: embeddedToken || saved.token || '',
    connected: embedded,
    embedded,
    device: null,
  }),
  actions: {
    save() {
      localStorage.setItem('rainnote.connection', JSON.stringify({
        baseUrl: this.baseUrl,
        token: this.token,
      }))
    },
  },
})
