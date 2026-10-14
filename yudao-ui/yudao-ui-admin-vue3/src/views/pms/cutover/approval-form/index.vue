<template>
  <section class="cutover-task-workbench">
    <el-result v-if="decided" icon="success" title="审批决定已提交" sub-title="当前待办已办理，可返回待办列表。" />
    <template v-else>
      <el-alert title="请在下方完成五项评审并提交，审批结果将同步到当前流程待办。" type="info" :closable="false" class="mb-12px" />
      <CutoverApprovalPanel :approval-id="id" complete-after-decision @decided="decided = true" />
    </template>
  </section>
</template>
<script setup lang="ts">
import '../workbench/cutoverWorkbench.css'
import CutoverApprovalPanel from '../workbench/components/CutoverApprovalPanel.vue'
import type { WireLong } from '@/api/pms/cutover/workbench'
const decided = ref(false)
defineProps<{ id: WireLong }>()
</script>
