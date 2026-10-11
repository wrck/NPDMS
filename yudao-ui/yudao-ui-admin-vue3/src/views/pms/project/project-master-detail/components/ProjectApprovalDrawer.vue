<template>
  <el-drawer v-model="visible" title="审批记录" size="min(1200px, 100%)" append-to-body destroy-on-close>
    <div class="project-approval-detail">
      <ProcessInstanceDetail v-if="visible && instanceId" :key="instanceId" :id="instanceId" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'

const ProcessInstanceDetail = defineAsyncComponent(() => import('@/views/bpm/processInstance/detail/index.vue').then(module => module.default))
const visible = ref(false)
const instanceId = ref('')
const open = (id: string) => {
  instanceId.value = id
  visible.value = true
}
defineExpose({ open })
</script>

<style scoped>
.project-approval-detail :deep(.processInstance-wrap-main) {
  height: auto;
  max-height: none;
}
.project-approval-detail :deep(.form-scroll-area) {
  height: calc(100dvh - 320px);
  min-height: 260px;
  max-height: none;
}
</style>
