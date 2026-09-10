<template>
  <section class="stage-graph" aria-label="阶段关系图预览">
    <template v-if="nodes.length">
      <svg :width="layout.width" :height="layout.height" :viewBox="`0 0 ${layout.width} ${layout.height}`" role="img">
        <defs>
          <marker id="stage-graph-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
            <path d="M0,0 L8,4 L0,8 z" class="graph-arrow" />
          </marker>
        </defs>
        <path
          v-for="edge in layout.edges"
          :key="edge.key"
          :d="edge.path"
          :class="['graph-edge', { 'graph-edge-default': edge.default }]"
          marker-end="url(#stage-graph-arrow)"
        />
        <text v-for="edge in layout.edges" :key="`${edge.key}-label`" :x="edge.labelX" :y="edge.labelY" class="graph-edge-label">
          {{ edge.label }}
        </text>
        <g v-for="node in nodes" :key="node.code" :transform="`translate(${node.x}, ${node.y})`">
          <rect
            :class="['graph-node', { 'graph-node-start': node.start, 'graph-node-terminal': node.terminal, 'graph-node-cyclic': node.cyclic }]"
            :width="nodeWidth"
            :height="nodeHeight"
            rx="6"
          />
          <text :x="nodeWidth / 2" :y="20" class="graph-node-code">{{ node.code }}</text>
          <text :x="nodeWidth / 2" :y="37" class="graph-node-name">{{ node.name || (node.start ? '开始' : node.terminal ? '收口' : '') }}</text>
        </g>
      </svg>
      <p class="graph-legend">
        <span class="legend-start">开始</span><span class="legend-terminal">正常收口</span>
        <span class="legend-default">— — 默认分支</span>
        <span v-if="cyclic.length" class="legend-cyclic">循环受阻：{{ cyclic.join('、') }}</span>
        <span class="legend-hint">仅按显式关系渲染；排序不生成边。</span>
      </p>
    </template>
    <el-alert v-else title="尚未配置阶段，无图可预览。" type="info" :closable="false" />
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { TemplateDefinitionContent } from '@/api/pms/project/project-templates'

// Read-only projection of the explicit transition graph; never mutates or infers edges.
const props = defineProps<{ content: TemplateDefinitionContent }>()
const nodeWidth = 150
const nodeHeight = 48
const colGap = 70
const rowGap = 16
const padding = 12

const graph = computed(() => {
  const stages = props.content.stages
  const edges = props.content.transitions ?? []
  const outgoing = new Map<string, string[]>()
  const indegree = new Map<string, number>()
  stages.forEach((stage) => {
    outgoing.set(stage.stageCode, [])
    indegree.set(stage.stageCode, 0)
  })
  edges.forEach((edge) => {
    if (!outgoing.has(edge.fromStageCode) || !indegree.has(edge.toStageCode)) return
    outgoing.get(edge.fromStageCode)!.push(edge.toStageCode)
    indegree.set(edge.toStageCode, (indegree.get(edge.toStageCode) ?? 0) + 1)
  })
  // Kahn layering: invariant DAG part first; leftovers are cyclic or unreachable-by-order nodes.
  const layers: string[][] = []
  const placed = new Set<string>()
  let frontier = stages.filter((stage) => (indegree.get(stage.stageCode) ?? 0) === 0).map((stage) => stage.stageCode)
  while (frontier.length) {
    layers.push(frontier)
    frontier.forEach((code) => placed.add(code))
    const next: string[] = []
    frontier.forEach((from) =>
      outgoing.get(from)!.forEach((to) => {
        indegree.set(to, (indegree.get(to) ?? 0) - 1)
        if ((indegree.get(to) ?? 0) === 0 && !placed.has(to) && !next.includes(to)) next.push(to)
      })
    )
    frontier = next
  }
  const cyclic = stages.filter((stage) => !placed.has(stage.stageCode)).map((stage) => stage.stageCode)
  if (cyclic.length) layers.push(cyclic)
  return { stages, edges, layers, cyclic }
})

interface GraphNode {
  code: string
  name: string
  start?: boolean
  terminal?: boolean
  cyclic: boolean
  x: number
  y: number
}
const nodes = computed<GraphNode[]>(() => {
  const byCode = new Map(graph.value.stages.map((stage) => [stage.stageCode, stage]))
  const result: GraphNode[] = []
  const columnCount = Math.max(...graph.value.layers.map((layer) => layer.length), 1)
  graph.value.layers.forEach((layer, columnIndex) => {
    layer.forEach((code, rowIndex) => {
      const stage = byCode.get(code)
      result.push({
        code,
        name: stage?.name ?? '',
        start: stage?.start,
        terminal: stage?.terminal,
        cyclic: graph.value.cyclic.includes(code),
        x: padding + columnIndex * (nodeWidth + colGap),
        y: padding + rowIndex * (nodeHeight + rowGap)
      })
    })
  })
  return result
})

const layout = computed(() => {
  const position = new Map(nodes.value.map((node) => [node.code, node]))
  const edges = graph.value.edges.map((edge, index) => {
    const from = position.get(edge.fromStageCode)
    const to = position.get(edge.toStageCode)
    if (!from || !to) return null
    const x1 = from.x + nodeWidth
    const y1 = from.y + nodeHeight / 2
    const x2 = to.x
    const y2 = to.y + nodeHeight / 2
    const bend = Math.max(36, Math.abs(x2 - x1) / 2)
    return {
      key: edge.transitionCode || `edge-${index}`,
      path: `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`,
      labelX: (x1 + x2) / 2,
      labelY: (y1 + y2) / 2 - 4,
      label: edge.default ? '默认' : `P${edge.priority ?? 0}`,
      default: edge.default
    }
  }).filter((edge): edge is NonNullable<typeof edge> => edge !== null)
  const width = padding * 2 + graph.value.layers.length * nodeWidth + Math.max(graph.value.layers.length - 1, 0) * colGap
  const maxRows = Math.max(...graph.value.layers.map((layer) => layer.length), 1)
  const height = padding * 2 + maxRows * nodeHeight + Math.max(maxRows - 1, 0) * rowGap
  return { edges, width, height }
})
const cyclic = computed(() => graph.value.cyclic)
</script>
<style scoped>
.stage-graph { overflow-x: auto; padding: 8px 0; }
.graph-node { fill: var(--el-bg-color); stroke: var(--el-border-color); stroke-width: 1.2; }
.graph-node-start { stroke: var(--el-color-success); }
.graph-node-terminal { stroke: var(--el-color-warning); }
.graph-node-cyclic { stroke: var(--el-color-danger); fill: var(--el-color-danger-light-9); }
.graph-node-code { font-size: 12px; font-weight: 600; text-anchor: middle; fill: var(--el-color-primary); }
.graph-node-name { font-size: 11px; text-anchor: middle; fill: var(--el-text-color-primary); }
.graph-edge { fill: none; stroke: var(--el-border-color-darker); stroke-width: 1.4; }
.graph-edge-default { stroke-dasharray: 5 4; stroke: var(--el-color-primary-light-5); }
.graph-arrow { fill: var(--el-border-color-darker); }
.graph-edge-label { font-size: 10px; text-anchor: middle; fill: var(--el-text-color-secondary); }
.graph-legend { display: flex; flex-wrap: wrap; gap: 14px; margin: 8px 0 0; font-size: 12px; color: var(--el-text-color-secondary); }
.legend-start::before { content: '●'; color: var(--el-color-success); margin-right: 4px; }
.legend-terminal::before { content: '●'; color: var(--el-color-warning); margin-right: 4px; }
.legend-cyclic { color: var(--el-color-danger); }
</style>
