import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './pc.css'
import PcApp from './PcApp.vue'

createApp(PcApp)
  .use(createPinia())
  .use(ElementPlus)
  .mount('#app')
