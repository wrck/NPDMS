<template>
  <ContentWrap class="flow-panel">
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button link @click="reload">重新加载内容</el-button>
    </el-alert>
    <el-empty v-if="!selection" description="请在左侧交付流程中选择阶段或任务" />

    <template v-else-if="selection.kind === 'stage'">
      <div class="panel-header stage-head">
        <div class="stage-head-row">
          <span class="stage-code-chip">{{ selection.stageCode }}</span>
          <span class="stage-head-name">{{
            currentStage?.stageName || selection.stageCode
          }}</span>
          <dict-tag
            v-if="currentStage"
            :type="DICT_TYPE.PMS_PROJECT_STAGE_STATUS"
            :value="currentStage.stageStatus"
          />
        </div>
      </div>
      <el-descriptions v-if="currentStage" :column="descriptionColumns" border size="small">
        <el-descriptions-item label="准入说明">
          {{ stageDetails?.entryCriteria || '未配置说明；按冻结规则判定' }}
        </el-descriptions-item>
        <el-descriptions-item label="准出说明">
          {{ stageDetails?.exitCriteria || '未配置说明；按冻结规则判定' }}
        </el-descriptions-item>
        <el-descriptions-item label="建议开始">{{ stageTime(stageDetails?.suggestedStartTime) }}</el-descriptions-item>
        <el-descriptions-item label="建议结束">{{ stageTime(stageDetails?.suggestedEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ stageTime(stageDetails?.planStartTime) }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ stageTime(stageDetails?.planEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="实际开始（本轮）">{{ stageTime(stageDetails?.actualStartTime) }}</el-descriptions-item>
        <el-descriptions-item label="实际结束（本轮）">{{ stageTime(stageDetails?.actualEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="偏差原因">{{ stageDetails?.deviationReason || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="阶段进度" :span="descriptionColumns">
          <div v-if="stageProgress" class="stage-progress">
            <el-progress
              class="stage-progress-bar"
              :percentage="stageProgress.percent"
              :stroke-width="10"
            />
            <span class="progress-text num"
              >{{ stageProgress.percent }}%（{{ stageProgress.done }}/{{ stageProgress.total }}）</span
            >
          </div>
          <span v-else>—</span>
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title task-business-heading">
        <span>阶段业务办理</span>
        <el-button :disabled="stageBusinessRef?.isBusy()" @click="stageBusinessRef?.refresh()">刷新业务结果</el-button>
      </div>
      <StageBusinessPanel ref="stageBusinessRef" :project="project" :stage-code="selection.stageCode" @changed="handleStageBusinessChanged" />

      <StageGateResultsPanel
v-if="showStageGates && hasStageGates" ref="stageGatesRef" :project-id="projectId"
        :stage-code="selection.stageCode" :project-version="project.version" @changed="handleGateChanged" />

      <div class="section-title">阶段任务</div>
      <el-alert v-if="tasks.error.value" :title="tasks.error.value" type="error" :closable="false">
        <el-button link @click="tasks.retry">重试任务加载</el-button>
      </el-alert>
      <!-- 列结构与 /pms/project-detail 的阶段任务表同口径：名称/编码/状态/进度/负责人；子任务列保留本页懒加载能力 -->
      <el-table
        v-loading="tasks.loading.value"
        :data="stageTree"
        row-key="taskId"
        :tree-props="{ children: 'children' }"
        default-expand-all
        :empty-text="tasks.error.value ? '任务尚未加载成功' : '本阶段暂无任务'"
      >
        <el-table-column prop="name" label="任务名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || `#${row.taskId}` }}</template>
        </el-table-column>
        <el-table-column prop="taskCode" label="编码" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_TASK_STATUS" :value="row.status ?? ''" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress ?? 0"
              :stroke-width="6"
              :show-text="false"
            />
            <span class="progress-text">{{ row.progress ?? 0 }}%</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showResponsibilities" label="负责人" width="110">
          <template #default="{ row }">
            <UserTag v-if="row.assigneeUserId" :user-id="row.assigneeUserId" />
            <span v-else>未指派</span>
          </template>
        </el-table-column>
        <el-table-column label="子任务" width="150">
          <template #default="{ row }">
            <el-button
v-if="!tasks.childState(row.taskId)?.loaded || tasks.childState(row.taskId)?.cursor" link
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
          <span v-if="workbench?.task.taskCode" class="task-code-chip">{{
            workbench.task.taskCode
          }}</span>
        </span>
        <el-tag v-if="workbench?.task.status" size="small" effect="plain">
          {{ statusLabel(workbench.task.status) }}
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
        <el-descriptions-item label="业务层级">{{ workbench.task.businessLevelCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ workbench.task.actualStartTime ? formatDate(workbench.task.actualStartTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{ workbench.task.actualEndTime ? formatDate(workbench.task.actualEndTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务说明" :span="descriptionColumns">
          <div class="description-heading">
            <el-button v-if="maintenanceRef?.canEditDescription" link type="primary" @click="maintenanceRef?.openDescription()">编辑说明</el-button>
          </div>
          <div
v-if="maintenanceRef?.description?.descriptionFormat === 'HTML'"
            v-dompurify-html="maintenanceRef.description.description || ''" class="task-description-html"></div>
          <div v-else class="task-description">{{ maintenanceRef?.description ? maintenanceRef.description.description || '暂无任务说明' : '任务说明尚未加载' }}</div>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="workbench?.recoverableError"
        type="error"
        :closable="false"
        show-icon
        :title="`执行区暂不可用：${workbench.recoverableError}`"
      />

      <TaskStateActions
v-if="workbench" ref="stateActionsRef" :workbench="workbench" :business-bound="businessBound"
        :business-fact-version="businessFactVersion" :before-action="requestLeave" @changed="handleBusinessChanged">
        <ProjectTaskDetailsEditor ref="detailsRef" :workbench="workbench" :before-action="requestLeave" @changed="handleBusinessChanged" />
      </TaskStateActions>
      <TaskMaintenancePanel
v-if="workbench?.task.version != null" ref="maintenanceRef" :project-id="projectId"
        :task-id="workbench.task.taskId" :task-version="workbench.task.version" :show-description="false" :show-responsibilities="showResponsibilities" @changed="handleBusinessChanged" />

      <div class="section-title task-business-heading">
        <span>任务业务办理</span>
        <el-button
          v-if="businessBound && workbench?.task.version != null"
          :loading="businessRef?.loading" :disabled="!businessRef" @click="businessRef?.refresh()">刷新业务结果</el-button>
        <el-button v-else-if="workbench?.bindingType === 'APPROVAL'" :disabled="approvalRef?.isBusy()" @click="reload">刷新审批结果</el-button>
      </div>
      <TaskBusinessPanel
        v-if="businessBound && workbench?.task.version != null"
        ref="businessRef"
        :key="`flow-task-${workbench.task.taskId}`"
        :task-id="workbench.task.taskId"
        :task-version="workbench.task.version"
        :initial-project="project"
        :readonly="['DONE', 'CLOSED', 'CANCELLED'].includes(workbench.task.status || '')"
        @changed="handleBusinessChanged"
        @fact-version="handleBusinessFactChanged"
      />
      <TaskApprovalPanel
v-else-if="workbench?.bindingType === 'APPROVAL'" ref="approvalRef"
        :key="`approval-${workbench.task.taskId}`" :workbench="workbench" @changed="handleBusinessChanged" />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        :title="workbench?.bindingType === 'TASK_NATIVE' ? '当前为通用任务，请通过任务状态操作办理，不挂载外部业务页面。' : '尚未取得可用的任务业务绑定；不能据此判断交付件或完成依据不适用，请重试或核对冻结契约。'"
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
import StageGateResultsPanel from './StageGateResultsPanel.vue'
import TaskStateActions from '@/views/pms/project/inheritance/detail/TaskStateActions.vue'
import TaskApprovalPanel from '@/views/pms/project/inheritance/detail/TaskApprovalPanel.vue'
import TaskMaintenancePanel from '@/views/pms/project/inheritance/wbs/TaskMaintenancePanel.vue'
import ProjectTaskDetailsEditor from './ProjectTaskDetailsEditor.vue'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'ProjectFlowPanel' })

const props = withDefaults(defineProps<{
  projectId: number
  project: ProjectMasterVO
  instances?: ProjectInstancesVO | null
  selection?: ProjectFlowSelection
  showResponsibilities?: boolean
  /** 页面工作区把门禁面板放到右栏时关闭内嵌渲染，避免同一门禁出现两份审批入口 */
  showStageGates?: boolean
}>(), { showResponsibilities: false, showStageGates: true })

const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))
const workspace = ref<ProjectWorkspace>()
const tasks = useFlowTaskPaging(() => props.projectId, () => props.selection?.stageCode)
const stageTree = computed(() => taskForest(tasks.rows.value, props.selection?.stageCode || ''))
const stageDetails = computed(() => props.instances?.stages.find(stage => stage.stageCode === props.selection?.stageCode))
// 阶段进度：DONE 任务比例（与推进轨/头部统计同口径；实例视图任务无 progress 字段），无任务时不展示
const stageProgress = computed(() => {
  const stageCode = props.selection?.stageCode
  if (props.selection?.kind !== 'stage' || !stageCode) return undefined
  const list = (props.instances?.tasks || []).filter((task) => task.stageCode === stageCode)
  if (!list.length) return undefined
  const done = list.filter((task) => task.status === 'DONE').length
  return { done, total: list.length, percent: Math.round((done / list.length) * 100) }
})
const stageTime = (value?: string) => value ? formatDate(value) : '—'
const statusLabel = (status?: string) =>
  ({
    PENDING_ASSIGN: '待分配',
    PENDING_START: '待开始',
    IN_PROGRESS: '进行中',
    PENDING_ACCEPT: '待验收',
    DONE: '完成',
    CLOSED: '关闭',
    CANCELLED: '已取消'
  })[status || ''] ||
  status ||
  '未知'
const hasStageGates = computed(() => props.instances?.gates.some(gate => gate.stageCode === props.selection?.stageCode))
const workbench = ref<TaskWorkbench>()
const loadError = ref('')
const businessRef = ref<InstanceType<typeof TaskBusinessPanel>>()
const stageBusinessRef = ref<InstanceType<typeof StageBusinessPanel>>()
const stageGatesRef = ref<InstanceType<typeof StageGateResultsPanel>>()
const stateActionsRef = ref<InstanceType<typeof TaskStateActions>>()
const approvalRef = ref<InstanceType<typeof TaskApprovalPanel>>()
const maintenanceRef = ref<InstanceType<typeof TaskMaintenancePanel>>()
const detailsRef = ref<InstanceType<typeof ProjectTaskDetailsEditor>>()
const emit = defineEmits<{ changed: [] }>()
// Await the existing Owner leave contract before the parent changes selection/unmounts content.
// https://vuejs.org/guide/essentials/template-refs.html#ref-on-component
const requestLeave = async () => !stateActionsRef.value?.isBusy() && maintenanceRef.value?.requestLeave() !== false
  && detailsRef.value?.requestLeave() !== false
  && (await businessRef.value?.requestLeave()) !== false
  && (await stageBusinessRef.value?.requestLeave()) !== false
  && (await approvalRef.value?.requestLeave()) !== false
  && (await stageGatesRef.value?.requestLeave()) !== false
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

const loadTaskWorkbench = async (taskId: number | string, token: number) => {
  const result = await TaskWorkbenchApi.getTaskWorkbench(taskId)
  if (current(token)) workbench.value = result
}

const load = async () => {
  const token = ++sequence
  loadError.value = ''
  workbench.value = undefined
  businessFactVersion.value = undefined
  tasks.reset()
  const selection = props.selection
  if (!selection) return
  try {
    if (selection.kind === 'stage') {
      await Promise.all([loadWorkspace(token), tasks.reload()])
    } else if (selection.taskId != null) {
      // Both endpoints authorize independently. Publish together so a failed
      // workspace read cannot expose a partially loaded task action panel.
      const [nextWorkspace, nextWorkbench] = await Promise.all([
        TaskWorkbenchApi.getProjectWorkspace(props.projectId),
        TaskWorkbenchApi.getTaskWorkbench(selection.taskId)
      ])
      if (!current(token)) return
      workspace.value = nextWorkspace
      workbench.value = nextWorkbench
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
const handleStageBusinessChanged = async () => { await refreshSummary(); await stageGatesRef.value?.refresh(); emit('changed') }
const handleGateChanged = async () => { await refreshSummary(); emit('changed') }
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

/* 设计稿 wb-head：阶段码徽标 + 名称 + 状态 */
.stage-head-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.stage-code-chip {
  flex: none;
  padding: 2px 8px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 4px;
}

.stage-head-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-code-chip {
  margin-left: 6px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.task-business-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
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
  overflow-wrap: anywhere;
}

.description-heading {
  display: flex;
  justify-content: flex-end;
}

.task-description-html {
  overflow-wrap: anywhere;
}

.task-description-html :deep(img) {
  max-width: 100%;
}

/* 阶段进度：进度条 + 等宽百分比文本（与 /pms/project-detail 阶段进度同款） */
.stage-progress {
  display: flex;
  align-items: center;
  gap: 4px;
}

.stage-progress-bar {
  flex: 1;
  max-width: 260px;
}

.progress-text {
  flex: none;
  margin-left: 6px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--el-text-color-secondary);
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
