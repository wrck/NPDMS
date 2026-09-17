import { createRouter, createWebHashHistory } from 'vue-router'
import Designer from '@/views/lowcode/form-designer/index.vue'
import Runtime from '@/views/lowcode/render/index.vue'
export default createRouter({ history: createWebHashHistory(), routes: [
  { path: '/designer', component: Designer },
  { path: '/lowcode/:pageType/:pageCode', component: Runtime },
  { path: '/:pathMatch(.*)*', component: { template: '<div>Fixture home</div>' } }
] })
