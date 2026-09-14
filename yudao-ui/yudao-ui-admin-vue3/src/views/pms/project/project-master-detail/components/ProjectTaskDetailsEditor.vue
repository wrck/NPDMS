<template>
  <section aria-label="任务资料与进度" class="task-details-actions">
    <el-button v-if="allowed('UPDATE')" :disabled="saving" @click="openEdit"
      >编辑任务资料</el-button
    >
    <el-form v-if="allowed('UPDATE_PROGRESS')" label-position="top">
      <el-form-item label="执行进度（0～99）">
        <el-slider v-model="progress" :min="0" :max="99" show-input :disabled="saving" />
        <el-button :loading="saving" @click="saveProgress">保存进度</el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </section>
  <el-dialog
    v-model="editing"
    title="编辑任务资料"
    width="min(680px, 96vw)"
    :close-on-click-modal="false"
    :before-close="closeEdit"
  >
    <el-form :model="form" label-position="top" :disabled="saving">
      <el-form-item label="任务名称"><el-input v-model="form.name" maxlength="128" /></el-form-item>
      <el-form-item label="业务层级"
        ><el-input v-model="form.businessLevelCode" maxlength="64"
      /></el-form-item>
      <el-form-item label="计划开始"
        ><el-date-picker
          v-model="form.planStartTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
      /></el-form-item>
      <el-form-item label="计划结束"
        ><el-date-picker
          v-model="form.planEndTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
      /></el-form-item>
      <el-form-item label="优先级"
        ><el-input-number v-model="form.priority" :min="0"
      /></el-form-item>
      <el-form-item label="排序"
        ><el-input-number v-model="form.sortOrder" :min="0"
      /></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer
      ><el-button :disabled="saving" @click="editing = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveEdit">保存资料</el-button></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  updateTask,
  updateTaskProgress,
  type TaskWorkbench
} from '@/api/pms/project/task-workbench'
import {
  buildTaskUpdatePayload,
  snapshotTaskEdit,
  type TaskEditSnapshot
} from './project-task-update'
import { useMessage } from '@/hooks/web/useMessage'
const props = defineProps<{ workbench: TaskWorkbench; beforeAction: () => Promise<boolean> }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const saving = ref(false),
  editing = ref(false),
  error = ref(''),
  progress = ref(0)
const original = ref<TaskEditSnapshot>()
const editingVersion = ref<number>()
const form = reactive({
  name: '',
  businessLevelCode: '',
  planStartTime: undefined as string | undefined,
  planEndTime: undefined as string | undefined,
  priority: undefined as number | undefined,
  sortOrder: undefined as number | undefined,
  description: ''
})
const allowed = (action: string) => props.workbench.allowedActions?.includes(action) === true
const openEdit = async () => {
  if (saving.value || !allowed('UPDATE') || !(await props.beforeAction())) return
  original.value = snapshotTaskEdit(props.workbench.task)
  editingVersion.value = props.workbench.task.version
  Object.assign(form, {
    ...original.value,
    businessLevelCode: original.value.businessLevelCode || '',
    planStartTime: original.value.planStartTime || undefined,
    planEndTime: original.value.planEndTime || undefined,
    priority: original.value.priority ?? undefined,
    sortOrder: original.value.sortOrder ?? undefined,
    description: original.value.description || ''
  })
  error.value = ''
  editing.value = true
}
const saveEdit = async () => {
  if (
    !original.value ||
    editingVersion.value == null ||
    saving.value ||
    !allowed('UPDATE') ||
    !form.name.trim()
  )
    return
  saving.value = true
  error.value = ''
  try {
    const payload = buildTaskUpdatePayload(original.value, form)
    if (Object.keys(payload).length)
      await updateTask(props.workbench.task.taskId, payload, editingVersion.value)
    editing.value = false
    emit('changed')
  } catch {
    error.value = '资料保存失败，请核对版本和输入；编辑内容已保留。'
  } finally {
    saving.value = false
  }
}
const saveProgress = async () => {
  if (
    saving.value ||
    !allowed('UPDATE_PROGRESS') ||
    props.workbench.task.version == null ||
    !(await props.beforeAction())
  )
    return
  saving.value = true
  error.value = ''
  try {
    await updateTaskProgress(
      props.workbench.task.taskId,
      progress.value,
      props.workbench.task.version
    )
    emit('changed')
  } catch {
    error.value = '进度保存失败，请刷新任务版本后重试。'
  } finally {
    saving.value = false
  }
}
const closeEdit = (done: () => void) => {
  if (!saving.value) done()
}
const requestLeave = () => {
  if (saving.value || editing.value) {
    message.warning('请先保存或关闭任务资料编辑，再切换内容')
    return false
  }
  return true
}
watch(
  () => [props.workbench.task.taskId, props.workbench.task.version],
  () => {
    progress.value = Number(props.workbench.task.progress || 0)
  },
  { immediate: true }
)
defineExpose({ requestLeave })
</script>

<style scoped>
.task-details-actions { display: contents; }
.task-details-actions > .el-form,
.task-details-actions > .el-alert { width: 100%; }
</style>
