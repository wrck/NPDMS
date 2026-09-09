import { createApp, defineComponent, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Fixture from './Fixture.vue'
const app = createApp(Fixture).use(ElementPlus)
app.component(
  'ContentWrap',
  defineComponent({
    setup:
      (_, { slots }) =>
      () =>
        h('section', slots.default?.())
  })
)
app.mount('#app')
