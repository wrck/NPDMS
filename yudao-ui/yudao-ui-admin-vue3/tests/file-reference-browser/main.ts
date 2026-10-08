import { createApp, h } from 'vue'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/dist/index.css'
import Fixture from './Fixture.vue'
const app = createApp(Fixture)
app.use(ElementPlus)
app.directive('hasPermi', {})
app.component('Icon', { render: () => h('span') })
app.mount('#app')
export const message = { success: ElMessage.success, warning: ElMessage.warning, error: ElMessage.error, prompt: ElMessageBox.prompt }
