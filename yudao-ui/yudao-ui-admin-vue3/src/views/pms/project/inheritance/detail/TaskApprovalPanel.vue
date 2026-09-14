<template>
  <section aria-label="任务审批办理">
    <el-alert v-if="!workbench.approval || workbench.recoverableError || workbench.approval.current.outcome === 'UNKNOWN'"
      title="当前轮次审批结果暂不可用，请刷新；不会按未发起处理。" type="warning" :closable="false" />
    <template v-else>
      <p>审批状态：{{ statusLabel }}</p>
      <el-button v-if="workbench.approval.current.processInstanceId" @click="openDetail">查看／办理审批</el-button>
      <TaskApprovalForm v-if="action" ref="formRef" :key="formKey"
        :definition-id="workbench.approval.definitionId" :definition-key="workbench.approval.definitionKey"
        :disabled="busy" :submit-approval="submit" />
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { executeTaskAction, type TaskApprovalSubmission, type TaskWorkbench } from '@/api/pms/project/task-workbench'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
import TaskApprovalForm from './TaskApprovalForm.vue'
const props = defineProps<{ workbench: TaskWorkbench }>()
const emit = defineEmits<{ changed: [] }>()
const router = useRouter()
const formRef = ref<InstanceType<typeof TaskApprovalForm>>()
const busy = ref(false)
const intent = createSubmissionIdempotencyState()
const formKey = computed(() => JSON.stringify([props.workbench.task.taskId, props.workbench.executionContractId,
  props.workbench.contractVersion, props.workbench.approval?.executionId, props.workbench.approval?.current.processInstanceId]))
const action = computed(() => {
  const w = props.workbench, fact = w.approval?.current
  if (w.bindingType !== 'APPROVAL' || w.recoverableError || !fact || fact.outcome === 'UNKNOWN' || w.task.version == null) return undefined
  if (fact.status === 'NOT_STARTED' && w.allowedActions.includes('START')) return 'START'
  if (['REJECTED', 'CANCELLED', 'NOT_STARTED'].includes(fact.status) && w.task.status === 'IN_PROGRESS'
    && w.allowedActions.includes('APPROVAL')) return 'APPROVAL'
  return undefined
})
const statusLabel = computed(() => ({ NOT_STARTED: '未发起', RUNNING: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回，可重新发起', CANCELLED: '已取消，可重新发起' }[props.workbench.approval?.current.status || ''] || '结果未知'))
const submit = async (approval: TaskApprovalSubmission) => {
  const w = props.workbench, operation = action.value
  if (!operation || busy.value || w.task.version == null) throw new Error('Task approval unavailable')
  const body = { executionContractId: w.executionContractId, contractVersion: w.contractVersion, approval }
  busy.value = true
  try {
    await executeTaskAction(w.task.taskId, operation, body, w.task.version,
      intent.keyFor({ taskId: w.task.taskId, version: w.task.version, operation, body, formKey: formKey.value }))
    emit('changed')
  } finally { busy.value = false }
}
const requestLeave = async () => !busy.value && (await formRef.value?.requestLeave()) !== false
const openDetail = async () => {
  const id = props.workbench.approval?.current.processInstanceId
  // The route leave guard below owns confirmation, so opening details does not prompt twice.
  if (id) await router.push({ name: 'BpmProcessInstanceDetail', query: { id } })
}
onBeforeRouteLeave(requestLeave)
defineExpose({ requestLeave, isBusy: () => busy.value || !!formRef.value?.isBusy() })
</script>
