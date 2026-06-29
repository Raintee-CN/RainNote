import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert,
  ElAside,
  ElButton,
  ElCard,
  ElCol,
  ElContainer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElInput,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElRow,
  ElSelect,
  ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import PcApp from './PcApp.vue'

const app = createApp(PcApp)

app.use(createPinia())
;[
  ElAlert,
  ElAside,
  ElButton,
  ElCard,
  ElCol,
  ElContainer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElInput,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElRow,
  ElSelect,
  ElTag,
].forEach((component) => {
  app.component(component.name, component)
})

app.mount('#app')
