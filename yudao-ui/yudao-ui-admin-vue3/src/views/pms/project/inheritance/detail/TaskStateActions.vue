<template>
  <section aria-label="任务状态操作" class="task-state-actions">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button v-for="action in actions" :key="action" :type="action === 'CANCEL' ? 'danger' : 'primary'"
      :loading="busy" :disabled="busy" @click="execute(action)">{{ labels[action] }}</el-button>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { executeTaskAction, type TaskAction, type TaskWorkbench, type TaskCommandResult } from '@/api/pms/project/task-workbench'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
const props = defineProps<{ workbench: TaskWorkbench; businessBound: boolean; businessFactVersion?: string; beforeAction: () => Promise<boolean> }>()
const emit = defineEmits<{ changed: [result: TaskCommandResult] }>()
const labels: Record<TaskAction, string> = { START: '开始任务', SUBMIT: '提交任务', COMPLETE: '校验并完成任务', CANCEL: '关闭任务' }
const actions = computed(() => (Object.keys(labels) as TaskAction[]).filter(action => props.workbench.allowedActions?.includes(action)))
const message = useMessage(), busy = ref(false), error = ref('')
const intent = createSubmissionIdempotencyState()
const execute = async (action: TaskAction) => {
  if (busy.value || !actions.value.includes(action) || props.workbench.task.version == null) return
  const taskId = props.workbench.task.taskId, version = props.workbench.task.version
  if (action === 'COMPLETE' && props.businessBound && !props.businessFactVersion) {
    error.value = '请先读取有效业务结果，不能用任务状态代替Owner完成事实'; return
  }
  if (!(await props.beforeAction())) return
  let reason: string | undefined
  if (action === 'CANCEL') {
    try { reason = (await message.prompt('请输入关闭原因', '关闭任务')).value?.trim() } catch { return }
    if (!reason) return
  }
  if (taskId !== props.workbench.task.taskId || version !== props.workbench.task.version || !actions.value.includes(action)) return
  const body = { reason,
    executionContractId: action === 'COMPLETE' ? props.workbench.executionContractId : undefined,
    contractVersion: action === 'COMPLETE' ? props.workbench.contractVersion : undefined,
    factObjectKey: action === 'COMPLETE' ? String(taskId) : undefined,
    factVersion: action === 'COMPLETE' && !props.businessBound ? version : undefined,
    expectedBusinessFactVersion: action === 'COMPLETE' && props.businessBound ? props.businessFactVersion : undefined }
  busy.value = true; error.value = ''
  try {
    const result = await executeTaskAction(taskId, action, body, version, intent.keyFor({ taskId, action, version, body }))
    if (action === 'COMPLETE' && result.status !== 'DONE') message.warning('完成条件尚未满足，任务状态未改变')
    else message.success('任务操作已保存')
    emit('changed', result)
  } catch (failure: any) { error.value = failure?.message || '任务操作未成功，可刷新核对或重试' }
  finally { busy.value = false }
}
defineExpose({ isBusy: () => busy.value })
</script>
<style scoped>
.task-state-actions { margin: 12px 0; }
</style>
