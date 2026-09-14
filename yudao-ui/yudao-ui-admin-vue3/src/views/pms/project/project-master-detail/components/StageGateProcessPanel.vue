<template>
  <section class="gate-process" :aria-label="`${reference.refCode} 门禁流程办理`">
    <p>{{ reference.refCode }}：{{ statusLabel }}</p>
    <el-alert v-if="!reference.process || reference.process.status === 'UNKNOWN'" type="warning" :closable="false"
      title="本轮流程结果暂不可用，请刷新；不会按未发起处理。" />
    <div class="process-actions">
      <el-button v-if="reference.process?.processInstanceId" :disabled="isBusy()" @click="openDetail">查看／办理审批</el-button>
      <el-button v-if="canStart && !editing" :disabled="disabled" @click="emit('edit')">填写审批表单</el-button>
    </div>
    <TaskApprovalForm v-if="editing && canStart" ref="formRef" :key="formKey"
      :definition-id="reference.refVersion!" :definition-key="reference.refCode"
      :disabled="disabled || busy" :submit-approval="submit" @submitted="emit('changed')" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { checkPermi } from '@/utils/permission'
import { startProjectStageGateProcess } from '@/api/pms/project/projects'
import type { StageGateReference, StageGateWorkbench } from '@/api/pms/project/stage-gates'
import type { TaskApprovalSubmission } from '@/api/pms/project/task-workbench'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
import TaskApprovalForm from '@/views/pms/project/inheritance/detail/TaskApprovalForm.vue'
const props = defineProps<{ workbench: StageGateWorkbench; reference: StageGateReference; editing: boolean; disabled?: boolean }>()
const emit = defineEmits<{ edit: []; changed: [] }>()
const router = useRouter()
const formRef = ref<{ requestLeave: () => Promise<boolean>; isBusy: () => boolean }>()
const busy = ref(false)
const intent = createSubmissionIdempotencyState()
const canStart = computed(() => props.reference.canStart && !!props.reference.refVersion
  && ['NOT_STARTED', 'REJECTED', 'CANCELLED'].includes(props.reference.process?.status || '')
  && checkPermi(['pms:project:update']))
const formKey = computed(() => JSON.stringify([props.workbench.planVersionId, props.workbench.executionId,
  props.reference.gateReferenceId, props.reference.refVersion, props.reference.process?.processInstanceId]))
const statusLabel = computed(() => ({ NOT_STARTED: '未发起', RUNNING: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回', CANCELLED: '已取消', UNKNOWN: '未知' }[props.reference.process?.status || 'UNKNOWN']))
const isBusy = () => busy.value || !!formRef.value?.isBusy()
const requestLeave = async () => !busy.value && (await formRef.value?.requestLeave()) !== false
const submit = async (approval: TaskApprovalSubmission) => {
  const w = props.workbench, reference = props.reference
  // Recheck permission at the action boundary, including changes while the form was open.
  if (busy.value || props.disabled || !props.editing || !canStart.value || !checkPermi(['pms:project:update'])
    || w.recoverableError || w.projectVersion == null || w.executionId == null) throw new Error('Gate approval unavailable')
  busy.value = true
  try {
    const result = await startProjectStageGateProcess(w.projectId, reference.gateReferenceId, w.projectVersion,
      intent.keyFor({ formKey: formKey.value, version: w.projectVersion, approval }), reference.refVersion!, approval)
    if (!['STARTED', 'REPLAYED'].includes(result.outcome) || !result.processInstanceId
      || result.processDefinitionId !== reference.refVersion) throw new Error('Gate approval not confirmed')
  } finally { busy.value = false }
}
const openDetail = async () => {
  const id = props.reference.process?.processInstanceId
  if (id && !isBusy()) await router.push({ name: 'BpmProcessInstanceDetail', query: { id } })
}
defineExpose({ requestLeave, isBusy })
</script>

<style scoped>
.gate-process { margin-top: 12px; min-width: 0; }
.process-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
</style>
