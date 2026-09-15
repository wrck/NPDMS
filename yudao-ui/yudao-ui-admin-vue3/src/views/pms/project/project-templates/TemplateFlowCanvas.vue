<template>
  <section class="flow-workspace" aria-label="项目交付流程画布">
    <header class="canvas-toolbar">
      <div class="palette" aria-label="拖拽节点工具箱">
        <button
          v-for="item in palette"
          :key="item.kind"
          type="button"
          :disabled="readonly"
          @mousedown="drag(item.kind, item.label)"
          @keydown.enter.prevent="emit('create', item.kind, undefined, { x: 240, y: 160 })"
          >{{ item.label }}</button
        >
      </div>
      <div
        ><el-button v-if="!readonly" text :disabled="!nodes.length" @click="arrange"
          >整理当前画布</el-button
        ><el-button text @click="flow?.fitView()">适应画布</el-button
        ><el-button text @click="flow?.zoom(true)">放大</el-button
        ><el-button text @click="flow?.zoom(false)">缩小</el-button></div
      >
    </header>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <div
      ref="container"
      class="flow-canvas"
      role="application"
      :aria-label="
        mode === 'STAGE'
          ? '阶段主画布，拖入阶段并连线配置依赖'
          : '任务子画布，拖入任务并连线配置依赖'
      "
    ></div>
    <p class="canvas-help"
      >拖入节点，点击配置；从节点连接点拖出依赖线。双击阶段进入任务子画布。整理仅调整当前画布位置，保存草稿后保留。</p
    >
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import LogicFlow from '@logicflow/core'
import { Dagre } from '@logicflow/layout'
import '@logicflow/core/lib/index.css'

export type DeliveryNodeKind = 'STAGE' | 'TASK' | 'MILESTONE' | 'DELIVERABLE' | 'GATE'
export interface DeliveryCanvasNode {
  key: string
  name: string
  code: string
  kind: DeliveryNodeKind
  reference?: boolean
  x?: number
  y?: number
}
export interface DeliveryCanvasEdge {
  key: string
  from: string
  to: string
  label?: string
}
const props = defineProps<{
  nodes: DeliveryCanvasNode[]
  edges: DeliveryCanvasEdge[]
  mode: 'STAGE' | 'TASK'
  readonly?: boolean
}>()
const emit = defineEmits<{
  create: [kind: DeliveryNodeKind, key: string | undefined, point: { x: number; y: number }]
  select: [key: string]
  enter: [key: string]
  move: [key: string, point: { x: number; y: number }]
  connect: [from: string, to: string]
  remove: [key: string]
  disconnect: [key: string]
}>()
const container = ref<HTMLElement>()
const failure = ref('')
let flow: LogicFlow | undefined
let observer: ResizeObserver | undefined
let synchronizing = false
const palette = computed<{ kind: DeliveryNodeKind; label: string }[]>(() => [
  props.mode === 'STAGE' ? { kind: 'STAGE', label: '＋ 阶段' } : { kind: 'TASK', label: '＋ 任务' },
  { kind: 'MILESTONE', label: '◇ 里程碑' },
  { kind: 'DELIVERABLE', label: '▤ 交付件' },
  { kind: 'GATE', label: '◆ 门禁' }
])
const shape = (kind: DeliveryNodeKind) =>
  kind === 'MILESTONE' || kind === 'GATE' ? 'diamond' : 'rect'
const drag = (kind: DeliveryNodeKind, label: string) => {
  if (!props.readonly) flow?.dnd.startDrag({ type: shape(kind), text: label, properties: { kind } })
}
const arrange = () => {
  if (props.readonly || !flow || !props.nodes.length) return
  synchronizing = true
  try {
    // Registered below as Dagre; use the plugin's own type, not the generic extension union.
    const dagre = flow.extension.dagre as Dagre
    dagre.layout({ rankdir: 'TB', nodesep: 80, ranksep: 100, isDefaultAnchor: true })
    for (const node of flow.getGraphRawData().nodes) emit('move', node.id, { x: node.x, y: node.y })
    failure.value = ''
    flow.fitView()
  } catch {
    failure.value = '画布整理失败，请重试；尚未保存的位置不会影响运行计划。'
  } finally {
    synchronizing = false
  }
}
const render = () => {
  if (!flow) return
  synchronizing = true
  try {
    flow.render({
      nodes: props.nodes.map((node, index) => ({
        id: node.key,
        type: shape(node.kind),
        x: node.x ?? 180 + (index % 3) * 240,
        y: node.y ?? 120 + Math.floor(index / 3) * 160,
        text: `${node.name}\n${node.code}`,
        properties: {
          kind: node.kind,
          ...(shape(node.kind) === 'rect' ? { width: 172, height: 72 } : { rx: 74, ry: 48 })
        }
      })),
      edges: props.edges.map((edge) => ({
        id: edge.key,
        type: 'polyline',
        sourceNodeId: edge.from,
        targetNodeId: edge.to,
        text: edge.label ?? ''
      }))
    })
    for (const node of props.nodes)
      if (node.reference)
        flow.graphModel
          .getNodeModelById(node.key)
          ?.getConnectedTargetRules()
          .push({ message: '引用节点只能作为依赖来源，请到所属阶段编辑', validate: () => false })
  } finally {
    synchronizing = false
  }
}
onMounted(() => {
  if (!container.value) return
  try {
    flow = new LogicFlow({
      container: container.value,
      plugins: [Dagre],
      grid: true,
      isSilentMode: !!props.readonly,
      keyboard: { enabled: !props.readonly },
      nodeTextEdit: false,
      edgeTextEdit: false,
      adjustEdge: false,
      guards: {
        beforeDelete: (data) => {
          if (synchronizing) return true
          if (!props.readonly && data.id) {
            if (props.nodes.some((node) => node.key === data.id)) emit('remove', data.id)
            else emit('disconnect', data.id)
          }
          return false
        }
      }
    })
    flow.setTheme({
      rect: { radius: 8, stroke: '#64748b', fill: '#ffffff' },
      diamond: { stroke: '#64748b', fill: '#ffffff' },
      nodeText: { fontSize: 12, overflowMode: 'autoWrap', textWidth: 140 },
      polyline: { stroke: '#64748b', strokeWidth: 1.5 }
    })
    flow.on('node:click', ({ data }: { data: { id: string } }) => emit('select', data.id))
    flow.on('node:dbclick', ({ data }: { data: { id: string } }) => emit('enter', data.id))
    flow.on('node:drop', ({ data }: { data: { id: string; x: number; y: number } }) => {
      if (!synchronizing && !props.readonly) emit('move', data.id, { x: data.x, y: data.y })
    })
    flow.on('node:dnd-add', ({ data }) => {
      const kind = data.properties?.kind
      if (
        !synchronizing &&
        !props.readonly &&
        typeof kind === 'string' &&
        ['STAGE', 'TASK', 'MILESTONE', 'DELIVERABLE', 'GATE'].includes(kind)
      )
        emit('create', kind as DeliveryNodeKind, data.id, { x: data.x, y: data.y })
    })
    flow.on('edge:add', ({ data }: { data: { sourceNodeId: string; targetNodeId: string } }) => {
      if (!synchronizing && !props.readonly) emit('connect', data.sourceNodeId, data.targetNodeId)
    })
    render()
    observer = new ResizeObserver(() => flow?.resize())
    observer.observe(container.value)
  } catch {
    failure.value = '流程画布加载失败，请重新打开设计器。'
  }
})
watch(() => [props.nodes, props.edges], render, { deep: true })
watch(
  () => props.readonly,
  (value) => flow?.updateEditConfig({ isSilentMode: !!value })
)
onBeforeUnmount(() => {
  observer?.disconnect()
  flow?.destroy()
})
</script>

<style scoped>
.flow-workspace {
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
}
.canvas-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
}
.palette {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.palette button {
  padding: 8px 12px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  cursor: grab;
}
.palette button:disabled {
  cursor: default;
  opacity: 0.6;
}
.palette button:focus-visible {
  outline: 2px solid var(--el-color-primary);
}
.flow-canvas {
  height: min(62vh, 680px);
  min-height: 400px;
  background: var(--el-bg-color);
}
.canvas-help {
  padding: 8px 12px;
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
