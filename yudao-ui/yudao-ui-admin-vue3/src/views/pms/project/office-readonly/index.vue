<template>
  <section data-office-readonly>
    <el-alert title="办事处项目只读查看：可浏览项目详情及业务页签，不能办理或下载。" type="info" :closable="false" show-icon class="mb-12px" />
    <ProjectDetail :key="route.fullPath" />
  </section>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import ProjectDetail from '../project-master-detail/index.vue'
import { officeReadonlyControlAllowed } from '@/utils/officeProjectReadonly'

defineOptions({ name: 'OfficeProjectReadonly' })
const route = useRoute()
let observer: MutationObserver | undefined
const guard = (event: Event) => {
  const target = (event.target as Element)?.closest('button, a, [role="button"], input[type="submit"]')
  if (!target?.closest('[data-office-readonly], .el-dialog, .el-drawer, .el-message-box')) return
  if (!target || officeReadonlyControlAllowed(target)) return
  event.preventDefault()
  event.stopImmediatePropagation()
}
const markControls = () => {
  // Dialog/Drawer会传送到body；只读路由存在期间同时覆盖其操作控件。
  document.querySelectorAll('button, a, [role="button"]').forEach(element => {
    if (!element.closest('[data-office-readonly], .el-dialog, .el-drawer, .el-message-box')) return
    if (officeReadonlyControlAllowed(element)) return
    if (!element.hasAttribute('data-office-disabled')) {
      element.setAttribute('data-office-disabled', element.getAttribute('aria-disabled') || '')
      element.setAttribute('aria-disabled', 'true')
      element.setAttribute('title', '只读查看，不能办理或下载')
    }
  })
}
onMounted(() => {
  document.addEventListener('click', guard, true)
  document.addEventListener('submit', guard, true)
  observer = new MutationObserver(markControls)
  observer.observe(document.body, { childList: true, subtree: true, characterData: true })
  markControls()
})
onBeforeUnmount(() => {
  observer?.disconnect()
  document.removeEventListener('click', guard, true)
  document.removeEventListener('submit', guard, true)
  document.querySelectorAll('[data-office-disabled]').forEach(element => {
    const original = element.getAttribute('data-office-disabled')
    if (original) element.setAttribute('aria-disabled', original)
    else element.removeAttribute('aria-disabled')
    element.removeAttribute('data-office-disabled')
    element.removeAttribute('title')
  })
})
</script>
<style>
[data-office-disabled] { opacity: .45; cursor: not-allowed !important; }
</style>
