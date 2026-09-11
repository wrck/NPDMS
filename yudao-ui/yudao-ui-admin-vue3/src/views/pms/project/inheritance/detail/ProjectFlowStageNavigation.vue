<template>
  <div class="flow-stage">
    <div class="flow-row">
      <button type="button" class="flow-toggle" :aria-expanded="expanded" :aria-label="`展开或收起${stage.stageName}`" @click="toggleStage">{{ expanded ? '▾' : '▸' }}</button>
      <button type="button" class="flow-label" :class="{ active: selection?.kind === 'stage' && selection.stageCode === stage.stageCode }"
        @click="emit('select', { kind: 'stage', stageCode: stage.stageCode })">{{ stage.stageCode }} {{ stage.stageName }}</button>
      <span class="flow-count">{{ stage.taskCount }}</span>
    </div>
    <div v-if="expanded" v-loading="tasks.loading.value">
      <div v-for="row in visibleRows" :key="row.node.taskId" :style="{ paddingLeft: `${12 + row.depth * 12}px` }">
        <div class="flow-row">
          <button v-if="mayHaveChildren(row.node)" type="button" class="flow-toggle" :aria-label="`展开或收起${row.node.name || '受限层级'}`"
            :aria-expanded="openTasks.has(row.node.taskId)" @click="toggleTask(row.node)">{{ openTasks.has(row.node.taskId) ? '▾' : '▸' }}</button>
          <span v-else class="flow-toggle" />
          <button type="button" class="flow-label" :disabled="row.node.placeholder" :class="{ active: selection?.taskId === row.node.taskId }"
            @click="emit('select', { kind: 'task', stageCode: stage.stageCode, taskId: row.node.taskId })">{{ row.node.placeholder ? '受限层级' : row.node.name || row.node.taskCode }}</button>
        </div>
        <el-button v-if="tasks.childState(row.node.taskId)?.error" link @click="tasks.loadChildren(row.node.taskId)">子任务加载失败，重试</el-button>
        <el-button v-else-if="openTasks.has(row.node.taskId) && tasks.childState(row.node.taskId)?.cursor" link :loading="tasks.childState(row.node.taskId)?.loading"
          @click="tasks.loadChildren(row.node.taskId)">加载更多子任务</el-button>
      </div>
      <el-alert v-if="tasks.error.value" :title="tasks.error.value" type="error" :closable="false">
        <el-button link @click="tasks.retry">重试任务加载</el-button>
      </el-alert>
      <el-button v-else-if="tasks.hasMore.value" link :loading="tasks.loading.value" @click="tasks.more">加载更多任务</el-button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { StageTaskNavigation, TaskNode } from '@/api/pms/project/task-workbench'
import type { ProjectFlowSelection } from '../../project-master-detail/components/project-flow'
import { taskForest, useFlowTaskPaging } from './flowTaskPaging'
const props = defineProps<{ projectId: number; stage: StageTaskNavigation; selection?: ProjectFlowSelection; refreshVersion: number }>()
const emit = defineEmits<{ select: [payload: ProjectFlowSelection] }>()
const tasks = useFlowTaskPaging(() => props.projectId, () => props.stage.stageCode)
const expanded = ref(false)
const openTasks = ref(new Set<number>())
let loaded = false
const roots = computed(() => taskForest(tasks.rows.value, props.stage.stageCode))
const visibleRows = computed(() => {
  const result: { node: TaskNode; depth: number }[] = []
  const visit = (nodes: TaskNode[], depth: number) => {
    for (const node of nodes) {
      result.push({ node, depth })
      if (openTasks.value.has(node.taskId)) visit(node.children || [], depth + 1)
    }
  }
  visit(roots.value, 0)
  return result
})
const mayHaveChildren = (node: TaskNode) => !tasks.childState(node.taskId)?.loaded || Boolean(node.children?.length)
const toggleStage = async () => {
  expanded.value = !expanded.value
  if (expanded.value && !loaded) { loaded = true; await tasks.reload() }
}
const toggleTask = async (node: TaskNode) => {
  const next = new Set(openTasks.value)
  if (next.has(node.taskId)) next.delete(node.taskId)
  else { next.add(node.taskId); void tasks.loadChildren(node.taskId) }
  openTasks.value = next
}
watch(() => props.refreshVersion, async () => {
  if (expanded.value) { await tasks.reload(); openTasks.value = new Set() }
  else { tasks.reset(); loaded = false }
})
onBeforeUnmount(tasks.reset)
</script>
<style scoped>
.flow-row { display: flex; align-items: center; gap: 4px; min-width: 0; }
.flow-toggle { width: 16px; flex: 0 0 16px; padding: 0; background: transparent; border: 0; color: var(--el-text-color-secondary); cursor: pointer; }
.flow-label { flex: 1; min-width: 0; padding: 6px 4px; border: 0; border-radius: var(--el-border-radius-base); background: transparent; color: var(--el-text-color-regular); text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.flow-label:hover { background: var(--el-fill-color-light); }
.flow-label.active { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.flow-label:disabled { color: var(--el-text-color-placeholder); cursor: default; }
.flow-count { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
