<template>
  <div class="task-tree" v-loading="loading">
    <el-tree
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
            <span class="task-node-name">{{ data.placeholder ? '受限层级' : data.name }}</span>
            <span v-if="!data.placeholder" class="task-node-code">{{ data.taskCode }}</span>
          </div>
          <div v-if="!data.placeholder" class="task-node-meta">
            <el-tag size="small" effect="plain" :type="statusType(data.status)">
              {{ statusLabel(data.status) }}
            </el-tag>
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
import { ref, watch } from 'vue'
import type { LoadFunction } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import type { TaskNode } from '@/api/pms/project/task-workbench'

defineOptions({ name: 'ProjectTaskTree' })

const props = defineProps<{
  projectId: number
  stageCode?: string
  keyword?: string
  refreshToken?: number
}>()
const emit = defineEmits<{
  select: [task: TaskNode]
  version: [version: number]
}>()

const message = useMessage()
const loading = ref(false)
const renderKey = ref(0)
const treeRows = ref<TaskNode[]>([])
const nextCursor = ref<string>()
const rootRows = ref<TaskNode[]>([])
const treeProps = { label: 'name', children: 'children', isLeaf: 'placeholder' }

const loadNode: LoadFunction = async (node, resolve) => {
  if (props.keyword) return resolve([])
  try {
    const response = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
      mode: 'DIRECT_CHILDREN',
      stageCode: props.stageCode,
      parentTaskId: node.level === 0 ? undefined : (node.data as TaskNode).taskId,
      pageSize: 50
    })
    emit('version', response.taskTreeVersion)
    const rows = response.rows
    if (node.level === 0) {
      rootRows.value = rows
      nextCursor.value = response.nextCursor
    }
    resolve(rows)
  } catch {
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
  if (!props.keyword) {
    treeRows.value = []
    renderKey.value++
    return
  }
  loading.value = true
  try {
    const response = await TaskWorkbenchApi.getProjectTasks(props.projectId, {
      mode: 'LOCATE',
      keyword: props.keyword,
      pageSize: 50
    })
    emit('version', response.taskTreeVersion)
    treeRows.value = buildLocatedTree(response.rows)
    nextCursor.value = undefined
    renderKey.value++
  } finally {
    loading.value = false
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

watch(() => [props.stageCode, props.keyword, props.refreshToken], search, { immediate: true })

defineExpose({ reload: search })
</script>

<style scoped lang="scss">
.task-tree {
  min-height: 280px;
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
}

.task-node-name,
.task-node-code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-node-code {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.task-node-meta {
  display: inline-flex;
  flex-shrink: 0;
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
  .task-node-code,
  .task-node-meta span {
    display: none;
  }
}
</style>
