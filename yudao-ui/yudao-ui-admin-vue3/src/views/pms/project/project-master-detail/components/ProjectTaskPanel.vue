<template>
  <ContentWrap class="task-panel">
    <div class="panel-header">
      <div>
        <div class="panel-title"><Icon icon="ep:list" />项目任务</div>
        <div class="panel-subtitle">Stage 导航与任务树均来自服务端当前投影</div>
      </div>
      <div class="panel-actions">
        <el-button
          v-if="workspace?.allowedActions.includes('MANAGE_PLAN')"
          type="primary"
          @click="planVisible = true"
        >
          <Icon icon="ep:plus" />新建／调整任务
        </el-button>
        <el-button :loading="loading" @click="reload"><Icon icon="ep:refresh" />刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="workspace"
      type="info"
      :closable="false"
      :title="`任务树版本 ${workspace.taskTreeVersion} · 投影水位 ${workspace.projectionWatermark}`"
      class="watermark"
    />

    <div v-if="workspace" class="task-layout">
      <nav class="stage-nav" aria-label="项目阶段任务导航">
        <button
          v-for="stage in workspace.stageTaskNavigation"
          :key="stage.stageCode"
          class="stage-item"
          :class="{ 'stage-item--active': selectedStage === stage.stageCode }"
          @click="selectedStage = stage.stageCode"
        >
          <span class="stage-code">{{ stage.stageCode }}</span>
          <span class="stage-name">{{ stage.stageName }}</span>
          <el-badge :value="stage.taskCount" :max="9999" />
        </button>
      </nav>

      <section class="tree-panel" aria-label="项目任务树">
        <div class="tree-toolbar">
          <el-input
            v-model="keywordInput"
            clearable
            placeholder="按任务名称或编码定位"
            @keyup.enter="search"
          >
            <template #append
              ><el-button aria-label="搜索任务" @click="search"><Icon icon="ep:search" /></el-button
            ></template>
          </el-input>
          <el-button v-if="keyword" @click="clearSearch">返回按需树</el-button>
        </div>
        <ProjectTaskTree
          :project-id="projectId"
          :stage-code="keyword ? undefined : selectedStage"
          :keyword="keyword"
          :refresh-token="refreshToken"
          @select="openWorkbench"
          @version="handleTreeVersion"
        />
      </section>
    </div>
    <el-empty v-else-if="!loading" description="暂无项目任务工作区" />
  </ContentWrap>

  <ProjectNodeWorkbenchDrawer
    v-model="drawerVisible"
    :project="project"
    :selection="selectedTaskId == null ? undefined : { kind: 'task', stageCode: selectedTaskStage, taskId: selectedTaskId }"
    :show-responsibilities="showResponsibilities"
    @changed="handleCommandChanged"
  />

  <ProjectPlanEditor
    v-model="planVisible"
    :project-id="projectId"
    @changed="handleCommandChanged"
  />
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type {
  ProjectWorkspace,
  TaskCommandResult,
  TaskNode
} from '@/api/pms/project/task-workbench'
import ProjectTaskTree from './ProjectTaskTree.vue'
import ProjectPlanEditor from './ProjectPlanEditor.vue'
import ProjectNodeWorkbenchDrawer from './ProjectNodeWorkbenchDrawer.vue'
import type { ProjectMasterVO } from '@/api/pms/project/projects'

defineOptions({ name: 'ProjectTaskPanel' })
const props = withDefaults(
  defineProps<{ projectId: number; project: ProjectMasterVO; showResponsibilities?: boolean }>(),
  { showResponsibilities: false }
)
const emit = defineEmits<{ 'tree-version': [version: number]; updated: [] }>()
const message = useMessage()
const loading = ref(false)
const workspace = ref<ProjectWorkspace>()
const selectedStage = ref('')
const keywordInput = ref('')
const keyword = ref('')
const refreshToken = ref(0)
const drawerVisible = ref(false)
const selectedTaskId = ref<number>()
const selectedTaskStage = ref('')
const planVisible = ref(false)

let workspaceRequest = 0
const loadWorkspace = async () => {
  const request = ++workspaceRequest
  loading.value = true
  try {
    const result = await TaskWorkbenchApi.getProjectWorkspace(props.projectId)
    if (request !== workspaceRequest) return false
    workspace.value = result
    if (
      !workspace.value.stageTaskNavigation.some((stage) => stage.stageCode === selectedStage.value)
    ) {
      selectedStage.value = workspace.value.stageTaskNavigation[0]?.stageCode || ''
    }
    emit('tree-version', workspace.value.taskTreeVersion)
    return true
  } finally {
    if (request === workspaceRequest) loading.value = false
  }
}

const reload = async () => {
  if (await loadWorkspace()) refreshToken.value++
}
const search = () => {
  keyword.value = keywordInput.value.trim()
}
const clearSearch = () => {
  keywordInput.value = ''
  keyword.value = ''
}
const openWorkbench = (task: TaskNode) => {
  if (task.placeholder || !task.stageCode) {
    message.warning('任务执行上下文不完整，请刷新任务树后重试')
    return
  }
  selectedTaskId.value = task.taskId
  selectedTaskStage.value = task.stageCode
  drawerVisible.value = true
}
const handleTreeVersion = (version: number) => {
  if (workspace.value) workspace.value.taskTreeVersion = version
  emit('tree-version', version)
}
const handleCommandChanged = async (_result?: TaskCommandResult) => {
  emit('updated')
  await reload()
}

watch([() => props.projectId, () => props.project.version], ([projectId], [previousProjectId]) => {
  if (projectId !== previousProjectId) {
    workspace.value = undefined
    drawerVisible.value = false
    planVisible.value = false
    selectedTaskId.value = undefined
    selectedTaskStage.value = ''
  }
  void reload()
})
onMounted(loadWorkspace)
</script>

<style scoped lang="scss">
.panel-header {
  display: flex;
  align-items: flex-start;
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

.panel-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.panel-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.watermark {
  margin: 12px 0;
}

.task-layout {
  display: grid;
  grid-template-columns: minmax(150px, 220px) minmax(0, 1fr);
  min-height: 420px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.stage-nav {
  padding: 8px;
  overflow-y: auto;
  background: var(--el-fill-color-lighter);
  border-right: 1px solid var(--el-border-color-lighter);
}

.stage-item {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 10px;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--el-border-radius-base);
}

.stage-item:hover {
  background: var(--el-fill-color-light);
}

.stage-item--active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.stage-code {
  font-weight: 600;
}

.stage-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-panel {
  min-width: 0;
  padding: 12px;
}

.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.form-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (width <= 767px) {
  .panel-header,
  .tree-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .task-layout {
    display: block;
    min-height: 360px;
  }

  .stage-nav {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .stage-item {
    flex: 0 0 auto;
    width: auto;
  }

  .stage-name {
    max-width: 120px;
  }

  :deep(.el-date-editor.el-input) {
    width: 100%;
  }
}
</style>
