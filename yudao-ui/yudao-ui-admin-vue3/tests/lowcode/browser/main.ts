import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import formCreate from '@form-create/element-ui'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from './App.vue'

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
await router.push('/')
const app = createApp(App)
app.use(ElementPlus).use(formCreate).use(router).mount('#app')
