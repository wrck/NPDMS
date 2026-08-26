<template>
  <ContentWrap class="task-panel">
    <div class="panel-header">
      <div>
        <div class="panel-title"><Icon icon="ep:list" />项目任务</div>
        <div class="panel-subtitle">Stage 导航与任务树均来自服务端当前投影</div>
      </div>
      <div class="panel-actions">
        <el-button
          v-if="workspace?.allowedActions.includes('CREATE')"
          type="primary"
          @click="openCreate"
        >
          <Icon icon="ep:plus" />新建任务
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

  <ProjectTaskWorkbenchDrawer
    v-model="drawerVisible"
    :task-id="selectedTaskId"
    @changed="handleCommandChanged"
    @move="openMove"
  />

  <Dialog
    v-model="createVisible"
    title="新建 TASK_NATIVE 任务"
    width="min(720px, calc(100vw - 24px))"
  >
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12"
          ><el-form-item label="任务编码" prop="taskCode"
            ><el-input v-model="createForm.taskCode" maxlength="64" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="任务名称" prop="name"
            ><el-input v-model="createForm.name" maxlength="128" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="所属阶段" prop="stageCode"
            ><el-select v-model="createForm.stageCode"
              ><el-option
                v-for="stage in workspace?.stageTaskNavigation || []"
                :key="stage.stageCode"
                :label="`${stage.stageCode} ${stage.stageName}`"
                :value="stage.stageCode" /></el-select></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="父任务 ID"
            ><el-input
              v-model="createForm.parentTaskId"
              inputmode="numeric"
              maxlength="20"
              placeholder="留空表示根任务" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="业务层级"
            ><el-input v-model="createForm.businessLevelCode" maxlength="64" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="优先级"
            ><el-input-number v-model="createForm.priority" :min="0" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="计划开始"
            ><el-date-picker
              v-model="createForm.planStartTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="计划结束"
            ><el-date-picker
              v-model="createForm.planEndTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
        ></el-col>
        <el-col :span="24"
          ><el-form-item label="任务说明"
            ><el-input
              v-model="createForm.description"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit /></el-form-item
        ></el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="create">创建任务</el-button>
    </template>
  </Dialog>

  <Dialog v-model="moveVisible" title="移动任务" width="min(560px, calc(100vw - 24px))">
    <el-form :model="moveForm" label-position="top">
      <el-form-item label="目标父任务 ID">
        <el-input
          v-model="moveForm.targetParentTaskId"
          inputmode="numeric"
          maxlength="20"
          placeholder="留空表示根任务"
        />
        <span class="form-hint">留空表示移动为根任务</span>
      </el-form-item>
      <el-form-item label="移动原因" required>
        <el-input
          v-model="moveForm.reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="moveVisible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="!moveForm.reason.trim()"
        :loading="submitting"
        @click="move"
        >确认移动</el-button
      >
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type { FormInstance, FormRules } from 'element-plus'
import type {
  ProjectWorkspace,
  TaskCommandResult,
  TaskDetail,
  TaskNode
} from '@/api/pms/project/task-workbench'
import ProjectTaskTree from './ProjectTaskTree.vue'
import ProjectTaskWorkbenchDrawer from './ProjectTaskWorkbenchDrawer.vue'

defineOptions({ name: 'ProjectTaskPanel' })
const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ 'tree-version': [version: number] }>()
const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const workspace = ref<ProjectWorkspace>()
const selectedStage = ref('')
const keywordInput = ref('')
const keyword = ref('')
const refreshToken = ref(0)
const drawerVisible = ref(false)
const selectedTaskId = ref<number>()
const createVisible = ref(false)
const moveVisible = ref(false)
const movingTask = ref<TaskDetail>()
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  taskCode: '',
  name: '',
  stageCode: '',
  parentTaskId: undefined as string | undefined,
  businessLevelCode: '',
  planStartTime: undefined as string | undefined,
  planEndTime: undefined as string | undefined,
  priority: 0,
  sortOrder: 0,
  description: ''
})
const moveForm = reactive({ targetParentTaskId: undefined as string | undefined, reason: '' })
const createRules: FormRules = {
  taskCode: [{ required: true, message: '请输入任务编码' }],
  name: [{ required: true, message: '请输入任务名称' }],
  stageCode: [{ required: true, message: '请选择所属阶段' }]
}
const idempotencyKey = () => crypto.randomUUID()
const optionalTaskId = (value?: string) => value?.trim() || undefined

const loadWorkspace = async () => {
  loading.value = true
  try {
    workspace.value = await TaskWorkbenchApi.getProjectWorkspace(props.projectId)
    if (
      !workspace.value.stageTaskNavigation.some((stage) => stage.stageCode === selectedStage.value)
    ) {
      selectedStage.value = workspace.value.stageTaskNavigation[0]?.stageCode || ''
    }
    emit('tree-version', workspace.value.taskTreeVersion)
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  await loadWorkspace()
  refreshToken.value++
}
const search = () => {
  keyword.value = keywordInput.value.trim()
}
const clearSearch = () => {
  keywordInput.value = ''
  keyword.value = ''
}
const openWorkbench = (task: TaskNode) => {
  selectedTaskId.value = task.taskId
  drawerVisible.value = true
}
const handleTreeVersion = (version: number) => {
  if (workspace.value) workspace.value.taskTreeVersion = version
  emit('tree-version', version)
}
const handleCommandChanged = async (_result: TaskCommandResult) => {
  await reload()
}

const openCreate = () => {
  Object.assign(createForm, {
    taskCode: '',
    name: '',
    stageCode: selectedStage.value,
    parentTaskId: undefined,
    businessLevelCode: '',
    planStartTime: undefined,
    planEndTime: undefined,
    priority: 0,
    sortOrder: 0,
    description: ''
  })
  createVisible.value = true
}

const create = async () => {
  if (!(await createFormRef.value?.validate())) return
  submitting.value = true
  try {
    await TaskWorkbenchApi.createTask(
      props.projectId,
      { ...createForm, parentTaskId: optionalTaskId(createForm.parentTaskId) },
      idempotencyKey()
    )
    message.success('任务创建成功')
    createVisible.value = false
    await reload()
  } finally {
    submitting.value = false
  }
}

const openMove = (task: TaskDetail) => {
  movingTask.value = task
  Object.assign(moveForm, {
    targetParentTaskId: task.parentTaskId == null ? undefined : String(task.parentTaskId),
    reason: ''
  })
  moveVisible.value = true
}

const move = async () => {
  if (
    !movingTask.value ||
    movingTask.value.version == null ||
    !workspace.value ||
    !moveForm.reason.trim()
  )
    return
  submitting.value = true
  try {
    await TaskWorkbenchApi.moveTask(
      movingTask.value.taskId,
      {
        ...moveForm,
        targetParentTaskId: optionalTaskId(moveForm.targetParentTaskId),
        expectedTaskTreeVersion: workspace.value.taskTreeVersion
      },
      movingTask.value.version,
      idempotencyKey()
    )
    message.success('任务移动成功')
    moveVisible.value = false
    drawerVisible.value = false
    await reload()
  } finally {
    submitting.value = false
  }
}

watch(() => props.projectId, reload)
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
