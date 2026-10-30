<template>
  <div class="task-tree" :class="{ 'task-tree--compact': props.compact }" v-loading="loading">
    <el-tree
      ref="treeRef"
      :key="renderKey"
      :data="treeRows"
      :props="treeProps"
      :lazy="!keyword"
      :load="loadNode"
      node-key="taskId"
      highlight-current
      empty-text="当前阶段暂无任务"
      @node-click="selectTask"
    >
      <template #default="{ data }">
        <div class="task-node" :class="{ 'task-node--placeholder': data.placeholder }">
          <div class="task-node-main">
            <span class="task-node-name" :title="data.placeholder ? undefined : data.name">{{ data.placeholder ? '受限层级' : data.name }}</span>
          </div>
          <div v-if="!data.placeholder" class="task-node-meta">
            <el-tag size="small" effect="plain" :type="statusType(data.status)">
              {{ statusLabel(data.status) }}
            </el-tag>
            <el-tag v-if="data.requiredTask === false" size="small" type="info" effect="plain">按需</el-tag>
            <span>{{ data.progress ?? 0 }}%</span>
          </div>
        </div>
      </template>
    </el-tree>
    <el-button v-if="nextCursor && !keyword" class="load-more" text @click="loadMore">
      加载更多同级任务
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type { LoadFunction, TreeInstance } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type { TaskNode } from '@/api/pms/project/task-workbench'

defineOptions({ name: 'ProjectTaskTree' })

const props = defineProps<{
  projectId: number
  stageCode?: string
  keyword?: string
  refreshToken?: number
  /** 左侧导航内嵌使用：高度随内容收缩，不保留独立页签的 280px 最小高度 */
  compact?: boolean
}>()
const emit = defineEmits<{
  select: [task: TaskNode]
  version: [version: number]
}>()

const message = useMessage()
const loading = ref(false)
const treeRef = ref<TreeInstance>()
let sequence = 0
let contextSequence = 0
const renderKey = ref(0)
const treeRows = ref<TaskNode[]>([])
const nextCursor = ref<string>()
const rootRows = ref<TaskNode[]>([])
const treeProps = { label: 'name', children: 'children', isLeaf: 'placeholder' }

const loadNode: LoadFunction = async (node, resolve) => {
  if (props.keyword) return resolve([])
  const token = contextSequence
  try {
    const response = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
      mode: 'DIRECT_CHILDREN',
      stageCode: props.stageCode,
      parentTaskId: node.level === 0 ? undefined : (node.data as TaskNode).taskId,
      pageSize: 50
    })
    if (token !== contextSequence) return resolve([])
    emit('version', response.taskTreeVersion)
    const rows = response.rows
    if (node.level === 0) {
      rootRows.value = rows
      nextCursor.value = response.nextCursor
    }
    resolve(rows)
  } catch {
    if (token !== contextSequence) return resolve([])
    message.error('任务树加载失败，请稍后重试')
    resolve([])
  }
}

const buildLocatedTree = (rows: TaskNode[]) => {
  const nodes = new Map(rows.map((row) => [row.taskId, { ...row, children: [] as TaskNode[] }]))
  const roots: TaskNode[] = []
  nodes.forEach((node) => {
    const parent = node.parentTaskId ? nodes.get(node.parentTaskId) : undefined
    if (parent) parent.children?.push(node)
    else roots.push(node)
  })
  return roots
}

const search = async () => {
  const token = ++sequence
  if (!props.keyword) {
    if (!rootRows.value.length) {
      treeRows.value = []
      renderKey.value++
      return
    }
    try {
      // Preserve loaded nodes and selection while fetching fresh navigation facts.
      const refreshNodes = async (parentTaskId?: TaskNode['taskId']) => {
        const existing: TaskNode[] = parentTaskId == null ? rootRows.value
          : treeRef.value?.getNode(parentTaskId)?.childNodes.map(node => node.data as TaskNode) || []
        const response = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
          mode: 'DIRECT_CHILDREN', stageCode: props.stageCode, parentTaskId, pageSize: 50
        })
        while (response.nextCursor && response.rows.length < existing.length) {
          const page = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
            mode: 'DIRECT_CHILDREN', stageCode: props.stageCode, parentTaskId,
            cursor: response.nextCursor, pageSize: 50
          })
          if (token !== sequence) return
          response.rows.push(...page.rows)
          response.nextCursor = page.nextCursor
          response.taskTreeVersion = page.taskTreeVersion
        }
        if (token !== sequence) return
        const sameRows = existing.length === response.rows.length
          && existing.every((row, index) => String(row.taskId) === String(response.rows[index].taskId))
        if (sameRows) {
          existing.forEach((row, index) => Object.assign(row, response.rows[index]))
        } else if (parentTaskId == null) {
          rootRows.value = response.rows
          treeRows.value = response.rows
        } else {
          treeRef.value?.updateKeyChildren(parentTaskId, response.rows)
        }
        if (parentTaskId == null) nextCursor.value = response.nextCursor
        emit('version', response.taskTreeVersion)
        await Promise.all(response.rows.filter(row => {
          const node = treeRef.value?.getNode(row.taskId)
          return node?.loaded && (node.expanded || node.childNodes.length)
        }).map(row => refreshNodes(row.taskId)))
      }
      await refreshNodes()
    } catch {
      if (token === sequence) message.error('任务树刷新失败，已保留当前导航，请重试')
    }
    return
  }
  loading.value = !treeRows.value.length
  try {
    const response = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
      mode: 'LOCATE', keyword: props.keyword, pageSize: 50
    })
    if (token !== sequence) return
    emit('version', response.taskTreeVersion)
    treeRows.value = buildLocatedTree(response.rows)
    nextCursor.value = undefined
  } finally {
    if (token === sequence) loading.value = false
  }
}

const loadMore = async () => {
  if (!nextCursor.value) return
  const response = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
    mode: 'DIRECT_CHILDREN',
    stageCode: props.stageCode,
    cursor: nextCursor.value,
    pageSize: 50
  })
  emit('version', response.taskTreeVersion)
  rootRows.value.push(...response.rows)
  treeRows.value = [...rootRows.value]
  nextCursor.value = response.nextCursor
  renderKey.value++
}

const selectTask = (task: TaskNode) => {
  if (!task.placeholder) emit('select', task)
}

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

watch(() => [props.projectId, props.stageCode, props.keyword], () => {
  ++sequence
  ++contextSequence
  loading.value = false
  rootRows.value = []
  treeRows.value = []
  nextCursor.value = undefined
  void search()
}, { immediate: true })
watch(() => props.refreshToken, search)
onBeforeUnmount(() => { ++sequence; ++contextSequence })

defineExpose({ reload: search })
</script>

<style scoped lang="scss">
.task-tree {
  min-height: 280px;
}

.task-tree--compact {
  min-height: 0;
}

.task-node {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 8px;
}

.task-node-main {
  min-width: 0;
  flex: 1;
}

.task-node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-node-name { display: block; }
.task-tree :deep(.el-tree-node__content) { height: auto; }
.task-tree--compact .task-node { gap: 4px; padding-block: 5px; }
.task-tree--compact .task-node-meta { margin-left: auto; }

.task-node-meta {
  display: inline-flex;
  flex-shrink: 0;
  white-space: nowrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.task-node--placeholder {
  color: var(--el-text-color-placeholder);
}

.load-more {
  width: 100%;
  margin-top: 8px;
}

@media (width <= 767px) {
  .task-node-meta span {
    display: none;
  }
}
</style>
