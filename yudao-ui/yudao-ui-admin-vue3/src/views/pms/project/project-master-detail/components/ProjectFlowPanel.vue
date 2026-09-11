<template>
  <ContentWrap class="flow-panel">
    <el-empty v-if="!selection" description="请在左侧交付流程中选择阶段或任务" />

    <template v-else-if="selection.kind === 'stage'">
      <div class="panel-header">
        <span class="panel-title">
          <Icon icon="ep:guide" /> 阶段视图 · {{ selection.stageCode }}
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
        <el-descriptions-item label="准入 / 准出">
          按冻结模板与门禁配置判定，阶段推进走原流程
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title">阶段业务办理 · Stage WorkBinding</div>
      <div v-if="selection.stageCode === 'S2'" class="stage-business">
        <ProjectDurationPanel :project="project" />
      </div>
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        title="该阶段的业务办理界面按冻结绑定接入；本轮未接通的阶段不展示替代页面，待按实际绑定接通后收口。"
      />

      <div class="section-title">阶段任务 · TreeTable</div>
      <el-table
        v-loading="stageLoading"
        :data="stageTree"
        row-key="taskId"
        :tree-props="{ children: 'children' }"
        default-expand-all
        empty-text="项目基本管理 · 无交付任务"
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
      </el-table>
    </template>

    <template v-else>
      <div class="panel-header">
        <span class="panel-title">
          <Icon icon="ep:tickets" /> 任务视图 · 直接展示 {{ workbench?.task.name || '' }}
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
        <el-descriptions-item label="负责人">
          {{ workbench.task.assigneeUserId || '未指派' }}
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

      <div class="section-title">任务说明</div>
      <div class="task-description">{{ workbench?.task.description || '暂无任务说明' }}</div>

      <div class="section-title">任务业务办理 · Task WorkBinding</div>
      <TaskBusinessPanel
        v-if="businessBound && workbench?.task.version != null"
        :key="`flow-task-${workbench.task.taskId}`"
        :task-id="workbench.task.taskId"
        :task-version="workbench.task.version"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        title="该任务当前未绑定业务办理对象，交付件与完成依据不适用；绑定与完成判定仍由原任务办理入口与业务 Owner 决定。"
      />
    </template>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type {
  ProjectWorkspace,
  StageTaskNavigation,
  TaskNode,
  TaskWorkbench
} from '@/api/pms/project/task-workbench'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectDurationPanel from './ProjectDurationPanel.vue'
import TaskBusinessPanel from './TaskBusinessPanel.vue'
import type { ProjectFlowSelection } from './project-flow'

defineOptions({ name: 'ProjectFlowPanel' })

const props = defineProps<{
  projectId: number
  project: ProjectMasterVO
  selection?: ProjectFlowSelection
}>()

const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))
const workspace = ref<ProjectWorkspace>()
const stageTree = ref<TaskNode[]>([])
const stageLoading = ref(false)
const workbench = ref<TaskWorkbench>()

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

const buildStageTree = (rows: TaskNode[], stageCode: string): TaskNode[] => {
  const scoped = rows.filter((row) => row.stageCode === stageCode)
  const ids = new Set(scoped.map((row) => row.taskId))
  const nodes = new Map(scoped.map((row) => [row.taskId, { ...row, children: [] as TaskNode[] }]))
  const roots: TaskNode[] = []
  for (const node of nodes.values()) {
    const parent =
      node.parentTaskId != null && ids.has(node.parentTaskId)
        ? nodes.get(node.parentTaskId)
        : undefined
    if (parent) parent.children?.push(node)
    else roots.push(node)
  }
  const prune = (items: TaskNode[]) =>
    items.forEach((item) => {
      if (item.children?.length) prune(item.children)
      else delete (item as { children?: TaskNode[] }).children
    })
  prune(roots)
  return roots
}

const loadWorkspace = async (token: number) => {
  const result = await TaskWorkbenchApi.getProjectWorkspace(props.projectId)
  if (current(token)) workspace.value = result
}

const loadStageTasks = async (stageCode: string, token: number) => {
  stageLoading.value = true
  try {
    const collected: TaskNode[] = []
    let cursor: string | undefined
    let guard = 0
    do {
      const page = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
        mode: 'ALL_DESCENDANTS',
        stageCode,
        cursor,
        pageSize: 100
      })
      collected.push(...page.rows)
      cursor = page.nextCursor
    } while (cursor && ++guard < 10)
    if (current(token)) stageTree.value = buildStageTree(collected, stageCode)
  } finally {
    if (current(token)) stageLoading.value = false
  }
}

const loadTaskWorkbench = async (taskId: number, token: number) => {
  const result = await TaskWorkbenchApi.getTaskWorkbench(taskId)
  if (current(token)) workbench.value = result
}

const load = async () => {
  const token = ++sequence
  workbench.value = undefined
  stageTree.value = []
  if (!props.selection) return
  await loadWorkspace(token)
  if (!current(token)) return
  if (props.selection.kind === 'stage') {
    await loadStageTasks(props.selection.stageCode, token)
  } else if (props.selection.taskId != null) {
    await loadTaskWorkbench(props.selection.taskId, token)
  }
}

watch(() => [props.projectId, props.selection?.kind, props.selection?.stageCode, props.selection?.taskId], load, { immediate: true })
onBeforeUnmount(() => {
  ++sequence
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
