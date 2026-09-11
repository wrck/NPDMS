<template>
  <div v-loading="loading" class="flow-nav">
    <div v-if="!stages.length && !loading" class="flow-nav-empty">暂无阶段任务导航</div>
    <template v-for="stage in stages" :key="stage.stageCode">
      <div class="flow-row">
        <button
          v-if="stageTaskRows(stage.stageCode).length"
          type="button"
          class="flow-toggle"
          :aria-expanded="expandedStages.has(stage.stageCode)"
          :aria-label="`展开或收起${stage.stageName}`"
          @click="toggleStage(stage.stageCode)"
        >
          {{ expandedStages.has(stage.stageCode) ? '▾' : '▸' }}
        </button>
        <span v-else class="flow-toggle flow-toggle--empty" aria-hidden="true"></span>
        <button
          type="button"
          class="flow-label"
          :class="{ 'flow-label--active': isStageSelected(stage.stageCode) }"
          :aria-pressed="isStageSelected(stage.stageCode)"
          @click="selectStage(stage.stageCode)"
        >
          {{ stage.stageCode }} {{ stage.stageName }}
        </button>
        <span class="flow-count">{{ stage.taskCount }}</span>
      </div>
      <div v-show="expandedStages.has(stage.stageCode)" class="flow-branch">
        <div
          v-for="row in visibleTaskRows(stage.stageCode)"
          :key="row.node.taskId"
          class="flow-row flow-row--task"
          :style="{ paddingLeft: `${12 + row.depth * 12}px` }"
        >
          <button
            v-if="hasChildren(row.node.taskId)"
            type="button"
            class="flow-toggle"
            :aria-expanded="!collapsedTasks.has(row.node.taskId)"
            :aria-label="`展开或收起${row.node.name ?? '子任务'}`"
            @click="toggleTask(row.node.taskId)"
          >
            {{ collapsedTasks.has(row.node.taskId) ? '▸' : '▾' }}
          </button>
          <span v-else class="flow-toggle flow-toggle--empty" aria-hidden="true"></span>
          <button
            type="button"
            class="flow-label flow-label--task"
            :class="{ 'flow-label--active': isTaskSelected(row.node.taskId) }"
            :aria-pressed="isTaskSelected(row.node.taskId)"
            @click="selectTask(stage.stageCode, row.node.taskId)"
          >
            {{ row.node.name || row.node.taskCode || `#${row.node.taskId}` }}
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type { StageTaskNavigation, TaskNode } from '@/api/pms/project/task-workbench'

defineOptions({ name: 'ProjectFlowNavigation' })

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{
  select: [payload: { kind: 'stage' | 'task'; stageCode: string; taskId?: number }]
}>()

const message = useMessage()
const loading = ref(false)
const stages = ref<StageTaskNavigation[]>([])
const rows = ref<TaskNode[]>([])
const expandedStages = ref(new Set<string>())
const collapsedTasks = ref(new Set<number>())
const selectedStageCode = ref('')
const selectedTaskId = ref<number>()

const childrenMap = computed(() => {
  const map = new Map<number, TaskNode[]>()
  for (const row of rows.value) {
    if (row.parentTaskId == null) continue
    const list = map.get(row.parentTaskId) ?? []
    list.push(row)
    map.set(row.parentTaskId, list)
  }
  return map
})
const knownIds = computed(() => new Set(rows.value.map((row) => row.taskId)))
const rootRows = computed(() =>
  rows.value.filter((row) => row.parentTaskId == null || !knownIds.value.has(row.parentTaskId))
)
const stageTaskRows = (stageCode: string) =>
  rootRows.value.filter((row) => row.stageCode === stageCode)
const hasChildren = (taskId: number) => (childrenMap.value.get(taskId)?.length ?? 0) > 0
const visibleTaskRows = (stageCode: string) => {
  const out: { node: TaskNode; depth: number }[] = []
  const walk = (node: TaskNode, depth: number) => {
    out.push({ node, depth })
    if (collapsedTasks.value.has(node.taskId)) return
    for (const child of childrenMap.value.get(node.taskId) ?? []) walk(child, depth + 1)
  }
  for (const root of stageTaskRows(stageCode)) walk(root, 0)
  return out
}

const isStageSelected = (stageCode: string) =>
  selectedTaskId.value == null && selectedStageCode.value === stageCode
const isTaskSelected = (taskId: number) => selectedTaskId.value === taskId

const toggleStage = (stageCode: string) => {
  const next = new Set(expandedStages.value)
  if (next.has(stageCode)) next.delete(stageCode)
  else next.add(stageCode)
  expandedStages.value = next
}
const toggleTask = (taskId: number) => {
  const next = new Set(collapsedTasks.value)
  if (next.has(taskId)) next.delete(taskId)
  else next.add(taskId)
  collapsedTasks.value = next
}
const selectStage = (stageCode: string) => {
  selectedStageCode.value = stageCode
  selectedTaskId.value = undefined
  expandedStages.value = new Set([...expandedStages.value, stageCode])
  emit('select', { kind: 'stage', stageCode })
}
const selectTask = (stageCode: string, taskId: number) => {
  selectedStageCode.value = stageCode
  selectedTaskId.value = taskId
  emit('select', { kind: 'task', stageCode, taskId })
}

const load = async () => {
  loading.value = true
  try {
    const workspace = await TaskWorkbenchApi.getProjectWorkspace(props.projectId)
    stages.value = workspace.stageTaskNavigation || []
    const collected: TaskNode[] = []
    let cursor: string | undefined
    let guard = 0
    do {
      const page = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
        mode: 'ALL_DESCENDANTS',
        cursor,
        pageSize: 100
      })
      collected.push(...page.rows)
      cursor = page.nextCursor
    } while (cursor && ++guard < 10)
    rows.value = collected
    const openStage = stages.value.find((stage) => stage.taskCount > 0)?.stageCode
    expandedStages.value = new Set(openStage ? [openStage] : [])
  } catch {
    message.error('阶段任务导航加载失败，请稍后重试')
    stages.value = []
    rows.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.projectId, load)
onMounted(load)
defineExpose({ reload: load })
</script>

<style scoped lang="scss">
.flow-nav {
  min-height: 80px;
}

.flow-nav-empty {
  padding: 8px 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.flow-row {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.flow-row--task {
  padding-left: 12px;
}

.flow-toggle {
  flex: 0 0 auto;
  width: 16px;
  padding: 0;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.flow-toggle--empty {
  cursor: default;
}

.flow-label {
  flex: 1 1 auto;
  min-width: 0;
  padding: 6px 4px;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--el-border-radius-base);
}

.flow-label:hover {
  background: var(--el-fill-color-light);
}

.flow-label--task {
  font-size: 12px;
}

.flow-label--active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.flow-count {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
