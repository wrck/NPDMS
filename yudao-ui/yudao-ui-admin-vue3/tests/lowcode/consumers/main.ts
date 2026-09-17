import { createApp, h } from 'vue'
import { RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import * as icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import formCreate from '@form-create/element-ui'
import { setToken, setTenantId } from '@/utils/auth'
import router from './router'
setToken({ accessToken: 'consumer-fixture', refreshToken: 'fixture-refresh' } as Parameters<typeof setToken>[0])
setTenantId(7)
const app = createApp({ render: () => h(RouterView) })
app.use(ElementPlus).use(formCreate).use(router)
Object.entries(icons).forEach(([name, component]) => app.component(name, component))
// UI permission presentation is isolated; actual method-security is tested in Java.
app.directive('hasPermi', {})
app.directive('permission', {})
await router.isReady()
app.mount('#app')
Object.assign(window, { consumerRouter: router })
