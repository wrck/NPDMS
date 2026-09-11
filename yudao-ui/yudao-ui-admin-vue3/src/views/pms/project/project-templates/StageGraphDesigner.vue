<template>
  <section class="stage-graph-designer" aria-label="阶段关系可视化设计器">
    <div class="graph-toolbar">
      <div>
        <strong>阶段关系画布</strong>
        <span v-if="linking && !linkSource">选择来源阶段</span>
        <span v-else-if="linking">来源 {{ linkSource }} · 再选择目标阶段</span>
        <span v-else>点击关系线配置条件、优先级与默认分支；排序不会生成关系。</span>
      </div>
      <el-button v-if="!readonly" :type="linking ? 'primary' : 'default'" @click="toggleLinking">
        {{ linking ? '取消连线' : '新增关系' }}
      </el-button>
    </div>

    <el-alert v-if="failure" :title="failure" type="warning" :closable="false" class="mb-12px" />
    <div v-if="nodes.length" class="graph-layout">
      <div class="graph-canvas" role="application" aria-label="模板阶段关系图">
        <svg :width="layout.width" :height="layout.height" :viewBox="`0 0 ${layout.width} ${layout.height}`">
          <defs>
            <marker id="stage-designer-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 z" class="graph-arrow" />
            </marker>
          </defs>
          <g v-for="edge in layout.edges" :key="edge.key" class="edge-group" @click.stop="selectEdge(edge.source)">
            <path :d="edge.path" class="edge-hit" />
            <path :d="edge.path" :class="['graph-edge', { selected: selectedEdge === edge.source, default: edge.source.defaultBranch }]" marker-end="url(#stage-designer-arrow)" />
            <rect :x="edge.labelX - 34" :y="edge.labelY - 13" width="68" height="20" rx="10" class="edge-label-bg" />
            <text :x="edge.labelX" :y="edge.labelY + 1" class="graph-edge-label">{{ edge.label }}</text>
          </g>
          <g
            v-for="node in nodes"
            :key="node.key"
            :transform="`translate(${node.x}, ${node.y})`"
            :class="['node-group', { linking, source: linkSource === node.code }]"
            role="button"
            tabindex="0"
            :aria-label="`${node.code} ${node.name}`"
            @click="selectNode(node.code)"
            @keydown.enter.prevent="selectNode(node.code)"
            @keydown.space.prevent="selectNode(node.code)"
          >
            <rect :class="['graph-node', { start: node.start, terminal: node.terminal, cyclic: node.cyclic }]" :width="nodeWidth" :height="nodeHeight" rx="8" />
            <text :x="14" :y="21" class="graph-node-code">{{ node.code }}</text>
            <text :x="14" :y="42" class="graph-node-name">{{ node.name || (node.start ? '开始' : node.terminal ? '收口' : '未命名阶段') }}</text>
            <text :x="nodeWidth - 12" :y="20" class="graph-node-badge" text-anchor="end">{{ node.start ? 'START' : node.terminal ? 'END' : '' }}</text>
          </g>
        </svg>
      </div>

      <aside class="edge-inspector" aria-label="关系属性">
        <template v-if="selectedEdge">
          <div class="inspector-heading">
            <div><strong>{{ selectedEdge.fromStageCode }} → {{ selectedEdge.toStageCode }}</strong><span>{{ selectedEdge.code }}</span></div>
            <el-button v-if="!readonly" link type="danger" @click="removeSelected">删除</el-button>
          </div>
          <el-form label-position="top" :disabled="readonly">
            <el-form-item label="关系编码"><el-input v-model="selectedEdge.code" /></el-form-item>
            <el-form-item label="优先级（小者优先）"><el-input-number v-model="selectedEdge.priority" :precision="0" controls-position="right" class="!w-full" /></el-form-item>
            <el-form-item label="默认分支"><el-switch :model-value="selectedEdge.defaultBranch" @update:model-value="setDefault" /></el-form-item>
          </el-form>
          <div v-if="!selectedEdge.defaultBranch" class="condition-box">
            <div class="condition-title"><strong>条件规则</strong><el-button v-if="!readonly && selectedEdge.condition" link type="danger" @click="selectedEdge!.condition = undefined">清除</el-button></div>
            <el-button v-if="!selectedEdge.condition && !readonly" @click="createCondition">添加条件</el-button>
            <RuleDecisionDesigner
              v-if="selectedEdge.condition"
              :model-value="selectedEdge.condition.expression"
              :disabled="readonly"
              @update:model-value="selectedEdge!.condition!.expression = $event"
            />
            <span v-if="selectedEdge.defaultBranch" class="field-hint">默认分支不能配置条件。</span>
          </div>
        </template>
        <template v-else><div class="empty-inspector"><strong>关系属性</strong><p>点击关系线查看并编辑。条件直接写入 DesignerDocument，不创建规则修订。</p></div></template>
      </aside>
    </div>
    <el-alert v-else title="尚未配置阶段。先在“阶段与任务”中选择阶段，再建立显式关系。" type="info" :closable="false" />

    <p class="graph-legend">
      <span class="legend-start">开始</span><span class="legend-terminal">正常收口</span><span>虚线＝默认分支</span>
      <span v-if="cyclic.length" class="legend-cyclic">循环受阻：{{ cyclic.join('、') }}</span>
      <span aria-live="polite">{{ linking ? (linkSource ? `已选择来源 ${linkSource}` : '正在建立关系') : '' }}</span>
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DesignerTransitionNode, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import RuleDecisionDesigner from './RuleDecisionDesigner.vue'

const props = defineProps<{ content: TemplateDesignerDocument; readonly?: boolean }>()
const nodeWidth = 164
const nodeHeight = 58
const colGap = 86
const rowGap = 24
const padding = 18
const linking = ref(false)
const linkSource = ref('')
const failure = ref('')
const selectedEdge = ref<DesignerTransitionNode>()

interface GraphNode { key: string; code: string; name: string; start?: boolean; terminal?: boolean; cyclic: boolean; x: number; y: number }

const graph = computed(() => {
  const stages = props.content.stages
  const edges = props.content.transitions
  const outgoing = new Map<string, string[]>()
  const indegree = new Map<string, number>()
  stages.forEach((stage) => { outgoing.set(stage.code, []); indegree.set(stage.code, 0) })
  edges.forEach((edge) => {
    if (!outgoing.has(edge.fromStageCode) || !indegree.has(edge.toStageCode)) return
    outgoing.get(edge.fromStageCode)!.push(edge.toStageCode)
    indegree.set(edge.toStageCode, (indegree.get(edge.toStageCode) ?? 0) + 1)
  })
  const layers: string[][] = []
  const placed = new Set<string>()
  let frontier = stages.filter((stage) => (indegree.get(stage.code) ?? 0) === 0).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).map((stage) => stage.code)
  while (frontier.length) {
    layers.push(frontier)
    frontier.forEach((code) => placed.add(code))
    const next: string[] = []
    frontier.forEach((from) => outgoing.get(from)!.forEach((to) => {
      indegree.set(to, (indegree.get(to) ?? 0) - 1)
      if ((indegree.get(to) ?? 0) === 0 && !placed.has(to) && !next.includes(to)) next.push(to)
    }))
    frontier = next
  }
  const cyclic = stages.filter((stage) => !placed.has(stage.code)).map((stage) => stage.code)
  if (cyclic.length) layers.push(cyclic)
  return { stages, edges, layers, cyclic }
})

const nodes = computed<GraphNode[]>(() => {
  const byCode = new Map(graph.value.stages.map((stage) => [stage.code, stage]))
  const result: GraphNode[] = []
  graph.value.layers.forEach((layer, columnIndex) => layer.forEach((code, rowIndex) => {
    const stage = byCode.get(code)
    result.push({ key: stage?.nodeKey ?? code, code, name: stage?.name ?? '', start: stage?.start, terminal: stage?.terminal,
      cyclic: graph.value.cyclic.includes(code), x: padding + columnIndex * (nodeWidth + colGap), y: padding + rowIndex * (nodeHeight + rowGap) })
  }))
  return result
})

const layout = computed(() => {
  const position = new Map(nodes.value.map((node) => [node.code, node]))
  const edges = graph.value.edges.map((edge) => {
    const from = position.get(edge.fromStageCode); const to = position.get(edge.toStageCode)
    if (!from || !to) return null
    const x1 = from.x + nodeWidth; const y1 = from.y + nodeHeight / 2; const x2 = to.x; const y2 = to.y + nodeHeight / 2
    const bend = Math.max(42, Math.abs(x2 - x1) / 2)
    return { key: edge.edgeKey, source: edge, path: `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`,
      labelX: (x1 + x2) / 2, labelY: (y1 + y2) / 2 - 5,
      label: edge.defaultBranch ? '默认' : edge.condition ? '条件' : `P${edge.priority ?? 0}` }
  }).filter((edge): edge is NonNullable<typeof edge> => edge !== null)
  const width = Math.max(640, padding * 2 + graph.value.layers.length * nodeWidth + Math.max(graph.value.layers.length - 1, 0) * colGap)
  const maxRows = Math.max(...graph.value.layers.map((layer) => layer.length), 1)
  const height = Math.max(220, padding * 2 + maxRows * nodeHeight + Math.max(maxRows - 1, 0) * rowGap)
  return { edges, width, height }
})

const cyclic = computed(() => graph.value.cyclic)
const toggleLinking = () => { linking.value = !linking.value; linkSource.value = ''; failure.value = '' }
const selectNode = (code: string) => {
  if (props.readonly || !linking.value) return
  failure.value = ''
  if (!linkSource.value) { linkSource.value = code; return }
  if (linkSource.value === code) { failure.value = '来源阶段和目标阶段不能相同。'; return }
  const transitions = props.content.transitions
  if (transitions.some((edge) => edge.fromStageCode === linkSource.value && edge.toStageCode === code)) {
    failure.value = `${linkSource.value} → ${code} 已存在关系，不重复创建。`; return
  }
  const priorities = transitions.filter((edge) => edge.fromStageCode === linkSource.value).map((edge) => edge.priority ?? 0)
  const nextPriority = priorities.length ? Math.max(...priorities) + 1 : 0
  const baseCode = `TR_${linkSource.value}_${code}`
  let transitionCode = baseCode; let suffix = 2
  while (transitions.some((edge) => edge.code === transitionCode)) transitionCode = `${baseCode}_${suffix++}`
  const edge: DesignerTransitionNode = {
    edgeKey: `transition:${crypto.randomUUID()}`,
    code: transitionCode,
    fromStageCode: linkSource.value,
    toStageCode: code,
    priority: nextPriority,
    defaultBranch: false
  }
  transitions.push(edge); selectedEdge.value = edge; linkSource.value = ''
}
const selectEdge = (edge: DesignerTransitionNode) => { selectedEdge.value = edge; failure.value = '' }
const createCondition = () => { if (selectedEdge.value) selectedEdge.value.condition = { expression: { predicate: 'TASK', parameters: { refCode: '' } } } }
const setDefault = (value: boolean) => {
  if (!selectedEdge.value || props.readonly) return
  if (value) {
    const conflict = props.content.transitions.find((edge) => edge !== selectedEdge.value && edge.fromStageCode === selectedEdge.value!.fromStageCode && edge.defaultBranch)
    if (conflict) { failure.value = `来源阶段 ${selectedEdge.value.fromStageCode} 已有默认分支 ${conflict.code}，请先取消原默认分支。`; return }
    selectedEdge.value.condition = undefined
  }
  selectedEdge.value.defaultBranch = value
}
const removeSelected = () => {
  if (!selectedEdge.value || props.readonly) return
  const index = props.content.transitions.indexOf(selectedEdge.value)
  if (index >= 0) props.content.transitions.splice(index, 1)
  selectedEdge.value = undefined
}
watch(() => props.content, () => { selectedEdge.value = undefined; linking.value = false; linkSource.value = ''; failure.value = '' })
</script>

<style scoped>
.stage-graph-designer { min-width: 0; }
.graph-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.graph-toolbar strong { display: block; font-size: 14px; }.graph-toolbar span { display: block; margin-top: 5px; color: var(--el-text-color-secondary); font-size: 12px; }
.graph-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.graph-canvas { overflow: auto; min-height: 240px; background: linear-gradient(var(--el-border-color-extra-light) 1px, transparent 1px), linear-gradient(90deg, var(--el-border-color-extra-light) 1px, transparent 1px); background-size: 24px 24px; }
.edge-inspector { padding: 16px; border-left: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color); overflow: auto; max-height: 620px; }
.inspector-heading, .condition-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 16px; }
.inspector-heading strong { display: block; font-size: 14px; }.inspector-heading span { display: block; margin-top: 5px; color: var(--el-text-color-secondary); font-size: 11px; }
.condition-box { border-top: 1px solid var(--el-border-color-lighter); padding-top: 14px; }.field-hint { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 11px; }
.empty-inspector p { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
.graph-node { fill: var(--el-bg-color); stroke: var(--el-border-color); stroke-width: 1.2; }.graph-node.start { stroke: var(--el-color-success); }.graph-node.terminal { stroke: var(--el-color-warning); }.graph-node.cyclic { stroke: var(--el-color-danger); fill: var(--el-color-danger-light-9); }
.node-group { cursor: default; outline: none; }.node-group.linking { cursor: crosshair; }.node-group.source .graph-node { stroke-width: 2; stroke: var(--el-color-primary); }
.graph-node-code { font-size: 12px; font-weight: 600; fill: var(--el-color-primary); }.graph-node-name { font-size: 11px; fill: var(--el-text-color-primary); }.graph-node-badge { font-size: 9px; fill: var(--el-text-color-secondary); }
.edge-group { cursor: pointer; }.edge-hit { fill: none; stroke: transparent; stroke-width: 14; }.graph-edge { fill: none; stroke: var(--el-border-color-darker); stroke-width: 1.5; }.graph-edge.default { stroke-dasharray: 5 4; }.graph-edge.selected { stroke: var(--el-color-primary); stroke-width: 2; }.graph-arrow { fill: var(--el-border-color-darker); }
.edge-label-bg { fill: var(--el-bg-color); stroke: var(--el-border-color-lighter); }.graph-edge-label { font-size: 10px; text-anchor: middle; fill: var(--el-text-color-secondary); }
.graph-legend { display: flex; flex-wrap: wrap; gap: 14px; margin: 10px 0 0; font-size: 12px; color: var(--el-text-color-secondary); }.legend-start::before { content: '●'; color: var(--el-color-success); margin-right: 4px; }.legend-terminal::before { content: '●'; color: var(--el-color-warning); margin-right: 4px; }.legend-cyclic { color: var(--el-color-danger); }
@media (max-width: 900px) { .graph-layout { grid-template-columns: 1fr; }.edge-inspector { border-left: 0; border-top: 1px solid var(--el-border-color-lighter); } }
</style>