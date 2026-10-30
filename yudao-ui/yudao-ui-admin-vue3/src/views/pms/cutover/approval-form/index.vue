<template>
  <section class="cutover-task-workbench">
    <el-result v-if="decided" icon="success" title="审批决定已提交" sub-title="当前待办已办理，可返回待办列表。" />
    <template v-else>
      <CutoverApprovalPanel :key="String(id)" :approval-id="id" complete-after-decision @decided="decided = true" />
    </template>
  </section>
</template>
<script setup lang="ts">
import '../shared/cutover.css'
import CutoverApprovalPanel from '../components/CutoverApprovalPanel.vue'
import type { WireLong } from '@/api/pms/cutover'
const decided = ref(false)
const props = defineProps<{ id: WireLong }>()
watch(() => props.id, () => { decided.value = false })
</script>
