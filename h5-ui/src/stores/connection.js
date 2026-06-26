import { defineStore } from 'pinia'

const saved = JSON.parse(localStorage.getItem('rainnote.connection') || '{}')

export const useConnectionStore = defineStore('connection', {
  state: () => ({
    baseUrl: saved.baseUrl || 'http://127.0.0.1:48622',
    token: saved.token || '',
    connected: false,
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
