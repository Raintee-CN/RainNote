import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => ({
  base: mode === 'pc' ? '/web-pc/' : '/web-mobile/',
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: mode === 'pc' ? 'index.pc.html' : 'index.html',
    },
  },
}))
