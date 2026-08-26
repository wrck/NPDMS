<template>
  <el-drawer v-model="visible" :size="drawerSize" destroy-on-close title="任务工作台">
    <div v-loading="loading" class="workbench">
      <el-alert
        v-if="workbench?.recoverableError"
        type="error"
        :title="`执行区暂不可用：${workbench.recoverableError}`"
        :closable="false"
        show-icon
      />
      <template v-if="task">
        <div class="workbench-header">
          <div>
            <div class="task-title">{{ task.name }}</div>
            <div class="task-code">{{ task.taskCode }} · {{ task.stageCode }}</div>
          </div>
          <el-tag :type="statusType(task.status)">{{ statusLabel(task.status) }}</el-tag>
        </div>

        <el-descriptions :column="descriptionColumns" border size="small">
          <el-descriptions-item label="业务层级">{{
            task.businessLevelCode || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{
            task.assigneeUserId || '未指派'
          }}</el-descriptions-item>
          <el-descriptions-item label="计划开始">{{
            formatDate(task.planStartTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="计划结束">{{
            formatDate(task.planEndTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="实际开始">{{
            formatDate(task.actualStartTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="实际结束">{{
            formatDate(task.actualEndTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ task.progress ?? 0 }}%</el-descriptions-item>
          <el-descriptions-item label="版本">{{ task.version }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="descriptionColumns">
            {{ task.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="action-bar" aria-label="任务允许操作">
          <el-button v-if="allowed('UPDATE')" @click="openEdit">编辑资料</el-button>
          <el-button v-if="allowed('ASSIGN')" @click="openAssign">指派/转派</el-button>
          <el-button v-if="allowed('MOVE')" @click="$emit('move', task)">移动</el-button>
          <el-button
            v-for="action in stateActions"
            :key="action"
            :type="action === 'CANCEL' ? 'danger' : 'primary'"
            :loading="submitting"
            @click="executeAction(action)"
          >
            {{ actionLabel(action) }}
          </el-button>
        </div>

        <el-form v-if="allowed('UPDATE_PROGRESS')" label-position="top" class="progress-form">
          <el-form-item label="执行进度（0～99）">
            <div class="progress-editor">
              <el-slider v-model="progress" :min="0" :max="99" show-input />
              <el-button type="primary" :loading="submitting" @click="saveProgress"
                >保存进度</el-button
              >
            </div>
          </el-form-item>
        </el-form>
      </template>
      <el-empty v-else-if="!loading" description="请选择任务" />
    </div>
  </el-drawer>

  <Dialog v-model="editVisible" title="编辑任务资料" width="min(680px, calc(100vw - 24px))">
    <el-form :model="editForm" label-position="top">
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12"
          ><el-form-item label="任务名称"
            ><el-input v-model="editForm.name" maxlength="128" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="业务层级"
            ><el-input v-model="editForm.businessLevelCode" maxlength="64" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="计划开始"
            ><el-date-picker
              v-model="editForm.planStartTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="计划结束"
            ><el-date-picker
              v-model="editForm.planEndTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="优先级"
            ><el-input-number v-model="editForm.priority" :min="0" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="排序"
            ><el-input-number v-model="editForm.sortOrder" :min="0" /></el-form-item
        ></el-col>
        <el-col :span="24"
          ><el-form-item label="任务说明"
            ><el-input
              v-model="editForm.description"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit /></el-form-item
        ></el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="saveEdit">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="assignVisible" title="指派任务负责人" width="min(760px, calc(100vw - 24px))">
    <el-form label-position="top">
      <el-form-item label="搜索候选">
        <el-input v-model="candidateQuery.keyword" clearable @keyup.enter="loadCandidates">
          <template #append
            ><el-button @click="loadCandidates"><Icon icon="ep:search" /></el-button
          ></template>
        </el-input>
      </el-form-item>
      <el-table
        v-loading="candidateLoading"
        :data="candidates"
        border
        size="small"
        @row-click="selectCandidate"
      >
        <el-table-column width="48"
          ><template #default="{ row }"
            ><el-radio
              v-model="selectedAssignee"
              :value="row.userId"
              aria-label="选择负责人" /></template
        ></el-table-column>
        <el-table-column prop="nickname" label="姓名" min-width="100" />
        <el-table-column prop="employeeNo" label="工号" min-width="100" />
        <el-table-column prop="departmentName" label="部门" min-width="140" />
      </el-table>
      <Pagination
        v-model:page="candidateQuery.pageNo"
        v-model:limit="candidateQuery.pageSize"
        :total="candidateTotal"
        @pagination="loadCandidates"
      />
      <el-form-item label="指派原因" required>
        <el-input
          v-model="assignReason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="assignVisible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="!selectedAssignee || !assignReason.trim()"
        :loading="submitting"
        @click="saveAssignment"
        >确认指派</el-button
      >
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate as formatDateValue } from '@/utils/formatTime'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type {
  TaskAction,
  TaskAssigneeCandidate,
  TaskCommandResult,
  TaskDetail,
  TaskWorkbench
} from '@/api/pms/project/task-workbench'

defineOptions({ name: 'ProjectTaskWorkbenchDrawer' })

const props = defineProps<{ modelValue: boolean; taskId?: number }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: [result: TaskCommandResult]
  move: [task: TaskDetail]
}>()

const message = useMessage()
const mobile = useMediaQuery('(max-width: 767px)')
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const drawerSize = computed(() => (mobile.value ? '100%' : 'min(720px, 72vw)'))
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))
const loading = ref(false)
const submitting = ref(false)
const workbench = ref<TaskWorkbench>()
const task = computed(() => workbench.value?.task)
const progress = ref(0)
const editVisible = ref(false)
const assignVisible = ref(false)
const candidateLoading = ref(false)
const candidates = ref<TaskAssigneeCandidate[]>([])
const candidateTotal = ref(0)
const selectedAssignee = ref<number>()
const assignReason = ref('')
const candidateQuery = reactive({ pageNo: 1, pageSize: 10, keyword: '' })
const editForm = reactive({
  name: '',
  businessLevelCode: '',
  planStartTime: undefined as string | undefined,
  planEndTime: undefined as string | undefined,
  priority: undefined as number | undefined,
  sortOrder: undefined as number | undefined,
  description: ''
})

const allowed = (action: string) => workbench.value?.allowedActions.includes(action) === true
const stateActions = computed(() =>
  (['START', 'SUBMIT', 'COMPLETE', 'CANCEL'] as TaskAction[]).filter(allowed)
)
const idempotencyKey = () => crypto.randomUUID()

const load = async () => {
  if (!props.taskId || !visible.value) return
  loading.value = true
  try {
    workbench.value = await TaskWorkbenchApi.getTaskWorkbench(props.taskId)
    progress.value = Number(workbench.value.task.progress || 0)
  } finally {
    loading.value = false
  }
}

const finishCommand = async (result: TaskCommandResult) => {
  message.success('任务操作成功')
  emit('changed', result)
  await load()
}

const executeAction = async (action: TaskAction) => {
  if (task.value?.version == null) return
  let reason = ''
  if (action === 'CANCEL') {
    const prompt = await message.prompt('请输入关闭原因', '关闭任务')
    reason = prompt.value
    if (!reason.trim()) return
  }
  submitting.value = true
  try {
    await finishCommand(
      await TaskWorkbenchApi.executeTaskAction(
        task.value.taskId,
        action,
        {
          reason: reason || undefined,
          executionContractId:
            action === 'COMPLETE' ? workbench.value?.executionContractId : undefined,
          contractVersion: action === 'COMPLETE' ? workbench.value?.contractVersion : undefined,
          factObjectKey: action === 'COMPLETE' ? String(task.value.taskId) : undefined,
          factVersion: action === 'COMPLETE' ? task.value.version : undefined
        },
        task.value.version,
        idempotencyKey()
      )
    )
  } finally {
    submitting.value = false
  }
}

const saveProgress = async () => {
  if (task.value?.version == null || progress.value < 0 || progress.value > 99) return
  submitting.value = true
  try {
    await finishCommand(
      await TaskWorkbenchApi.updateTaskProgress(
        task.value.taskId,
        progress.value,
        task.value.version
      )
    )
  } finally {
    submitting.value = false
  }
}

const openEdit = () => {
  if (!task.value) return
  Object.assign(editForm, {
    name: task.value.name || '',
    businessLevelCode: task.value.businessLevelCode || '',
    planStartTime: task.value.planStartTime,
    planEndTime: task.value.planEndTime,
    priority: task.value.priority,
    sortOrder: task.value.sortOrder,
    description: task.value.description || ''
  })
  editVisible.value = true
}

const saveEdit = async () => {
  if (task.value?.version == null || !editForm.name.trim()) return
  submitting.value = true
  try {
    const result = await TaskWorkbenchApi.updateTask(
      task.value.taskId,
      { ...editForm },
      task.value.version
    )
    editVisible.value = false
    await finishCommand(result)
  } finally {
    submitting.value = false
  }
}

const loadCandidates = async () => {
  if (!task.value) return
  candidateLoading.value = true
  try {
    const result = await TaskWorkbenchApi.getTaskAssigneeCandidates(
      task.value.taskId,
      candidateQuery
    )
    candidates.value = result.list
    candidateTotal.value = result.total
  } finally {
    candidateLoading.value = false
  }
}

const openAssign = async () => {
  selectedAssignee.value = undefined
  assignReason.value = ''
  candidateQuery.pageNo = 1
  assignVisible.value = true
  await loadCandidates()
}

const selectCandidate = (candidate: TaskAssigneeCandidate) => {
  selectedAssignee.value = candidate.userId
}
const saveAssignment = async () => {
  if (task.value?.version == null || !selectedAssignee.value || !assignReason.value.trim()) return
  submitting.value = true
  try {
    const result = await TaskWorkbenchApi.assignTask(
      task.value.taskId,
      selectedAssignee.value,
      assignReason.value,
      task.value.version,
      idempotencyKey()
    )
    assignVisible.value = false
    await finishCommand(result)
  } finally {
    submitting.value = false
  }
}

const formatDate = (value?: string) => (value ? formatDateValue(value) : '-')
const statusLabel = (status?: string) =>
  ({
    PENDING_ASSIGN: '待分配',
    PENDING_START: '待开始',
    IN_PROGRESS: '进行中',
    PENDING_ACCEPT: '待验收',
    DONE: '完成',
    CLOSED: '关闭'
  })[status || ''] ||
  status ||
  '未知'
const statusType = (status?: string) =>
  status === 'DONE'
    ? 'success'
    : status === 'CLOSED'
      ? 'info'
      : status === 'IN_PROGRESS'
        ? 'primary'
        : 'warning'
const actionLabel = (action: TaskAction) =>
  ({ START: '开始', SUBMIT: '提交验收', COMPLETE: '确认完成', CANCEL: '关闭' })[action]

watch(() => [props.taskId, props.modelValue], load)
</script>

<style scoped lang="scss">
.workbench {
  min-height: 320px;
}

.workbench-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.task-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-code {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.action-bar :deep(.el-button + .el-button) {
  margin-left: 0;
}

.progress-form {
  margin-top: 16px;
}

.progress-editor {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 16px;
}

.progress-editor :deep(.el-slider) {
  flex: 1;
  min-width: 0;
}

@media (width <= 767px) {
  .progress-editor {
    align-items: stretch;
    flex-direction: column;
  }

  :deep(.el-date-editor.el-input) {
    width: 100%;
  }
}
</style>
