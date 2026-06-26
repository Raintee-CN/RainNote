import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => ({
  base: mode === 'pc' ? '/web-pc/' : '/web-mobile/',
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: mode === 'pc' ? 'index.pc.html' : 'index.html',
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('vant')) return 'vant'
          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'vue-vendor'
          return 'vendor'
        },
      },
    },
  },
}))
