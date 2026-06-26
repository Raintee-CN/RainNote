import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import 'element-plus/dist/index.css'
import './pc.css'
import PcApp from './PcApp.vue'

const app = createApp(PcApp)

app.use(createPinia())
;[ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElSelect, ElTag].forEach((component) => {
  app.component(component.name, component)
})

app.mount('#app')
