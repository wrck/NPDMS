import { createApp, defineComponent, h } from 'vue'
import { createRouter, createWebHashHistory, RouterView } from 'vue-router'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import formCreate from '@form-create/element-ui'
import Fixture from './Fixture.vue'

Object.assign(globalThis, { useMessage: () => ({ warning: ElMessage.warning, success: ElMessage.success, info: ElMessage.info }) })
const router = createRouter({ history: createWebHashHistory(), routes: [{ path: '/', component: Fixture }] })
const app = createApp({ render: () => h(RouterView) })
app.component('ContentWrap', defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) }))
app.use(router).use(ElementPlus).use(formCreate).mount('#app')
