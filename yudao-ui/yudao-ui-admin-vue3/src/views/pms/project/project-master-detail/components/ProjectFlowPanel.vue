<template>
  <ContentWrap class="flow-panel">
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button link @click="reload">重新加载内容</el-button>
    </el-alert>
    <el-empty v-if="!selection" description="请在左侧交付流程中选择阶段或任务" />

    <template v-else-if="selection.kind === 'stage'">
      <div class="panel-header">
        <span class="panel-title">
          <Icon icon="ep:guide" /> {{ selection.stageCode }}
          {{ currentStage?.stageName || '' }}
        </span>
        <el-tag v-if="currentStage" size="small" effect="plain">
          任务数 {{ currentStage.taskCount }}
        </el-tag>
      </div>
      <el-descriptions v-if="currentStage" :column="descriptionColumns" border size="small">
        <el-descriptions-item label="阶段状态">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_STAGE_STATUS" :value="currentStage.stageStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="准入说明">
          {{ stageDetails?.entryCriteria || '未配置说明；按冻结规则判定' }}
        </el-descriptions-item>
        <el-descriptions-item label="准出说明">
          {{ stageDetails?.exitCriteria || '未配置说明；按冻结规则判定' }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title">阶段业务办理</div>
      <StageBusinessPanel ref="stageBusinessRef" :project="project" :stage-code="selection.stageCode" @changed="handleStageBusinessChanged" />

      <div class="section-title">阶段任务</div>
      <el-alert v-if="tasks.error.value" :title="tasks.error.value" type="error" :closable="false">
        <el-button link @click="tasks.retry">重试任务加载</el-button>
      </el-alert>
      <el-table
        v-loading="tasks.loading.value"
        :data="stageTree"
        row-key="taskId"
        :tree-props="{ children: 'children' }"
        default-expand-all
        :empty-text="tasks.error.value ? '任务尚未加载成功' : '本阶段暂无任务'"
      >
        <el-table-column prop="name" label="任务层级 / 名称" min-width="240">
          <template #default="{ row }">
            <span>{{ row.name || row.taskCode || `#${row.taskId}` }}</span>
            <span v-if="row.taskCode" class="row-code">{{ row.taskCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="120">
          <template #default="{ row }">{{ row.assigneeUserId || '未指派' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_TASK_STATUS" :value="row.status ?? ''" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="90">
          <template #default="{ row }">{{ row.progress ?? 0 }}%</template>
        </el-table-column>
        <el-table-column label="子任务" width="150">
          <template #default="{ row }">
            <el-button v-if="!tasks.childState(row.taskId)?.loaded || tasks.childState(row.taskId)?.cursor" link
              :loading="tasks.childState(row.taskId)?.loading" @click="tasks.loadChildren(row.taskId)">
              {{ tasks.childState(row.taskId)?.error ? '加载失败，重试' : tasks.childState(row.taskId)?.loaded ? '加载更多子任务' : '加载子任务' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="tasks.hasMore.value && !tasks.error.value" :loading="tasks.loading.value" @click="tasks.more">加载更多任务</el-button>
    </template>

    <template v-else>
      <div class="panel-header">
        <span class="panel-title">
          <Icon icon="ep:tickets" /> {{ workbench?.task.name || '任务详情' }}
        </span>
        <el-tag v-if="workbench?.task.status" size="small" effect="plain">
          {{ workbench?.task.status }}
        </el-tag>
      </div>
      <div v-if="workbench" class="task-breadcrumb">
        {{ selection.stageCode }} {{ currentStage?.stageName || '' }} /
        {{ workbench.task.taskCode || `#${workbench.task.taskId}` }}
      </div>

      <el-descriptions v-if="workbench" :column="descriptionColumns" border size="small">
        <el-descriptions-item label="状态">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_TASK_STATUS" :value="workbench.task.status ?? ''" />
        </el-descriptions-item>
        <el-descriptions-item label="计划开始">
          {{ workbench.task.planStartTime ? formatDate(workbench.task.planStartTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="计划结束">
          {{ workbench.task.planEndTime ? formatDate(workbench.task.planEndTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="进度">
          {{ workbench.task.progress ?? 0 }}%
        </el-descriptions-item>
        <el-descriptions-item label="任务版本">
          {{ workbench.task.version ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="workbench?.recoverableError"
        type="error"
        :closable="false"
        show-icon
        :title="`执行区暂不可用：${workbench.recoverableError}`"
      />

      <TaskStateActions v-if="workbench" ref="stateActionsRef" :workbench="workbench" :business-bound="businessBound"
        :business-fact-version="businessFactVersion" :before-action="requestLeave" @changed="handleBusinessChanged" />
      <TaskMaintenancePanel v-if="workbench?.task.version != null" ref="maintenanceRef" :project-id="projectId"
        :task-id="workbench.task.taskId" :task-version="workbench.task.version" @changed="handleBusinessChanged" />

      <div class="section-title">任务业务办理</div>
      <TaskBusinessPanel
        v-if="businessBound && workbench?.task.version != null"
        ref="businessRef"
        :key="`flow-task-${workbench.task.taskId}`"
        :task-id="workbench.task.taskId"
        :task-version="workbench.task.version"
        :readonly="['DONE', 'CLOSED', 'CANCELLED'].includes(workbench.task.status || '')"
        @changed="handleBusinessChanged"
        @fact-version="handleBusinessFactChanged"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        :title="workbench?.bindingType === 'TASK_NATIVE' ? '当前为通用任务，使用上方任务职责和状态操作办理，不挂载外部业务页面。' : '尚未取得可用的任务业务绑定；不能据此判断交付件或完成依据不适用，请重试或核对冻结契约。'"
      />
    </template>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteUpdate } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type {
  ProjectWorkspace,
  StageTaskNavigation,
  TaskWorkbench
} from '@/api/pms/project/task-workbench'
import type { ProjectMasterVO, ProjectInstancesVO } from '@/api/pms/project/projects'
import TaskBusinessPanel from './TaskBusinessPanel.vue'
import type { ProjectFlowSelection } from './project-flow'
import { taskForest, useFlowTaskPaging } from '@/views/pms/project/inheritance/detail/flowTaskPaging'
import StageBusinessPanel from '@/views/pms/project/inheritance/detail/StageBusinessPanel.vue'
import TaskStateActions from '@/views/pms/project/inheritance/detail/TaskStateActions.vue'
import TaskMaintenancePanel from '@/views/pms/project/inheritance/wbs/TaskMaintenancePanel.vue'

defineOptions({ name: 'ProjectFlowPanel' })

const props = defineProps<{
  projectId: number
  project: ProjectMasterVO
  instances?: ProjectInstancesVO | null
  selection?: ProjectFlowSelection
}>()

const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))
const workspace = ref<ProjectWorkspace>()
const tasks = useFlowTaskPaging(() => props.projectId, () => props.selection?.stageCode)
const stageTree = computed(() => taskForest(tasks.rows.value, props.selection?.stageCode || ''))
const stageDetails = computed(() => props.instances?.stages.find(stage => stage.stageCode === props.selection?.stageCode))
const workbench = ref<TaskWorkbench>()
const loadError = ref('')
const businessRef = ref<InstanceType<typeof TaskBusinessPanel>>()
const stageBusinessRef = ref<InstanceType<typeof StageBusinessPanel>>()
const stateActionsRef = ref<InstanceType<typeof TaskStateActions>>()
const maintenanceRef = ref<InstanceType<typeof TaskMaintenancePanel>>()
const emit = defineEmits<{ changed: [] }>()
// Await the existing Owner leave contract before the parent changes selection/unmounts content.
// https://vuejs.org/guide/essentials/template-refs.html#ref-on-component
const requestLeave = async () => !stateActionsRef.value?.isBusy() && maintenanceRef.value?.requestLeave() !== false
  && (await businessRef.value?.requestLeave()) !== false
  && (await stageBusinessRef.value?.requestLeave()) !== false
// BusinessViewHost already guards route leave; only reused-route project changes need this guard.
onBeforeRouteUpdate((to, from) => to.query.projectId === from.query.projectId || requestLeave())
const businessFactVersion = ref<string>()

let sequence = 0
const current = (value: number) => value === sequence

const currentStage = computed<StageTaskNavigation | undefined>(() =>
  workspace.value?.stageTaskNavigation.find(
    (stage) => stage.stageCode === props.selection?.stageCode
  )
)
const businessBound = computed(() =>
  ['BUSINESS_OBJECT', 'BUSINESS_COMPONENT', 'DYNAMIC_FORM', 'COMPOSITE'].includes(
    workbench.value?.bindingType || ''
  )
)

const loadWorkspace = async (token: number) => {
  const result = await TaskWorkbenchApi.getProjectWorkspace(props.projectId)
  if (current(token)) workspace.value = result
}

const loadTaskWorkbench = async (taskId: number, token: number) => {
  const result = await TaskWorkbenchApi.getTaskWorkbench(taskId)
  if (current(token)) workbench.value = result
}

const load = async () => {
  const token = ++sequence
  loadError.value = ''
  workbench.value = undefined
  businessFactVersion.value = undefined
  tasks.reset()
  if (!props.selection) return
  try {
    await loadWorkspace(token)
    if (!current(token)) return
    if (props.selection.kind === 'stage') {
      await tasks.reload()
    } else if (props.selection.taskId != null) {
      await loadTaskWorkbench(props.selection.taskId, token)
    }
  } catch {
    if (current(token)) loadError.value = '内容加载失败，请重新加载。'
  }
}
const reload = async () => { if (await requestLeave()) await load() }
const handleBusinessChanged = async () => {
  if (props.selection?.taskId == null) return
  try { await loadTaskWorkbench(props.selection.taskId, sequence) }
  catch { loadError.value = '业务已保存，任务详情刷新失败，请重新加载。' }
  emit('changed')
}
const handleStageBusinessChanged = async () => { await refreshSummary(); emit('changed') }
const handleBusinessFactChanged = (version?: string) => {
  if (!version) return
  const previous = businessFactVersion.value
  businessFactVersion.value = version
  if (previous && previous !== version) void handleBusinessChanged()
}
const refreshSummary = async () => {
  const token = sequence
  try {
    await loadWorkspace(token)
    if (current(token) && props.selection?.kind === 'stage') await tasks.reload()
  } catch {
    if (current(token)) loadError.value = '概要刷新失败，已加载内容保留，请重新加载。'
  }
}
defineExpose({ requestLeave, reload, refreshSummary })

watch(() => [props.projectId, props.selection?.kind, props.selection?.stageCode, props.selection?.taskId], load, { immediate: true })
onBeforeUnmount(() => {
  ++sequence
  tasks.reset()
})
</script>

<style scoped lang="scss">
.flow-panel {
  min-width: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-title {
  margin: 16px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-breadcrumb {
  margin: 12px 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.task-description {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}

.row-code {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.stage-business {
  margin-top: 4px;
}

@media (width <= 767px) {
  .panel-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
