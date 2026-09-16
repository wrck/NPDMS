<script setup lang="ts">
/**
 * 低代码微流设计器（三栏 DAG 可视化画布，借鉴 Mendix Microflows）。
 *
 * <p>布局：</p>
 * <ul>
 *   <li>顶栏：微流选择 / 新建 / 保存 / 执行 / 清空 / 自动布局 / 缩放适配 / 删除选中</li>
 *   <li>左栏 NodePalette：11 种节点类型按分组拖拽到画布</li>
 *   <li>中栏 X6 画布：节点 + 连线，点击节点配置参数，点击空白编辑微流元信息</li>
 *   <li>右栏：选中节点 → NodeParamPanel；未选中 → MicroflowMetaPanel + VariablePanel</li>
 *   <li>底部 ExecutionLogPanel：执行轨迹时间轴，点击日志项高亮画布节点</li>
 * </ul>
 *
 * <p>definition JSON 结构与后端 MicroflowEngine 对齐：{nodes:[{id,type,config,x,y}],edges:[{source,target}]}。
 * 节点 config 字段名与各 MicroflowNodeExecutor 对齐（见 NodeParamPanel）。
 * 执行结果不含 executionId，故执行后通过 getRecentExecutionLogs 取最新轨迹。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { Graph, type NodeMetadata } from '@antv/x6'
import { register } from '@antv/x6-vue-shape'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  continueMicroflowDebug,
  executeMicroflow,
  getExecutionLogs,
  getMicroflowList,
  getRecentExecutionLogs,
  saveMicroflow,
  startMicroflowDebug,
  stepOverMicroflowDebug,
  terminateMicroflowDebug,
  type LowCodeMicroflow,
  type MicroflowDebugStepResult,
  type MicroflowDefinition,
  type MicroflowExecutionLog,
  type MicroflowNode,
  type MicroflowNodeType,
  type MicroflowVariable
} from '@/api/lowcode-microflow'
import { getRuleList, type LowCodeRule } from '@/api/lowcode-rule'
import { getConnectorList, type LowCodeConnector } from '@/api/lowcode-connector'
import NodePalette from '@/components/MicroflowDesigner/NodePalette.vue'
import NodeParamPanel from '@/components/MicroflowDesigner/NodeParamPanel.vue'
import MicroflowMetaPanel from '@/components/MicroflowDesigner/MicroflowMetaPanel.vue'
import VariablePanel from '@/components/MicroflowDesigner/VariablePanel.vue'
import ExecutionLogPanel from '@/components/MicroflowDesigner/ExecutionLogPanel.vue'
import MicroflowNodeComp from '@/components/MicroflowDesigner/MicroflowNode.vue'
import type { MicroflowNodeData } from '@/components/MicroflowDesigner/MicroflowNode.vue'

defineOptions({ name: 'MicroflowDesignerView' })

/** 自定义节点 shape 名（通过 x6-vue-shape 注册） */
const MICROFLOW_NODE_SHAPE = 'microflow-node'
/** 节点尺寸 */
const NODE_WIDTH = 168
const NODE_HEIGHT = 48

// ===================== 状态 =====================

const microflowList = ref<LowCodeMicroflow[]>([])
const selectedMicroflowId = ref<number | undefined>(undefined)
const currentMicroflow = ref<LowCodeMicroflow | null>(null)
const definition = ref<MicroflowDefinition>(emptyDefinition())
const selectedNodeId = ref<string | null>(null)
/** 当前选中的画布元素 ID（节点或边，用于 Delete 删除） */
const selectedCellId = ref<string | null>(null)
const latestExecutionId = ref<string | undefined>(undefined)
const logCollapsed = ref(false)

/** 下拉数据（CALL_MICROFLOW / CALL_RULE / CALL_CONNECTOR 用） */
const ruleOptions = ref<LowCodeRule[]>([])
const connectorOptions = ref<LowCodeConnector[]>([])

/** 执行输入对话框 */
const execDialogVisible = ref(false)
const execInputs = ref('')
/** 对话框模式：execute（执行）/ debug（调试） */
const dialogMode = ref<'execute' | 'debug'>('execute')

// ===================== 断点调试状态 =====================

/** 断点节点 ID 集合 */
const breakpoints = ref<Set<string>>(new Set())
/** 是否处于调试会话中 */
const isDebugging = ref(false)
/** 调试会话 ID */
const debugSessionId = ref<string | null>(null)
/** 当前变量快照 */
const debugVariables = ref<Record<string, unknown>>({})
/** 当前步骤状态：PAUSED/COMPLETED/FAILED */
const debugStatus = ref<string | null>(null)
/** 当前暂停节点 ID（下一个待执行节点） */
const debugCurrentNodeId = ref<string | null>(null)
/** 调试最终结果 */
const debugResult = ref<unknown>(undefined)
/** 变量监视抽屉可见性 */
const debugVariablesVisible = ref(false)
/** 调试操作进行中（禁用按钮防止重复点击） */
const debugLoading = ref(false)

const graphRef = shallowRef<Graph | null>(null)
const canvasContainer = ref<HTMLDivElement>()

// ===================== 工具函数 =====================

function emptyDefinition(): MicroflowDefinition {
  return { nodes: [], edges: [], variables: { inputs: [], locals: [], returnType: 'Object' } }
}

/** 节点类型默认显示名 */
const DEFAULT_LABELS: Record<MicroflowNodeType, string> = {
  START: '开始',
  END: '结束',
  ASSIGN: '赋值',
  CONDITION: '条件',
  LOOP: '循环',
  CALL_SERVICE: '调用服务',
  CALL_MICROFLOW: '调用子微流',
  CALL_RULE: '调用规则',
  CALL_CONNECTOR: '调用连接器',
  THROW_EXCEPTION: '抛出异常',
  RETURN: '返回'
}

/** 解析 definition JSON，容错处理缺失字段 */
function parseDefinition(json?: string): MicroflowDefinition {
  if (!json) return emptyDefinition()
  try {
    const p = JSON.parse(json) as Partial<MicroflowDefinition>
    return {
      nodes: (p.nodes || []).map((n) => ({
        id: n.id,
        type: n.type,
        label: n.label || DEFAULT_LABELS[n.type] || n.type,
        x: n.x ?? 0,
        y: n.y ?? 0,
        config: n.config || {}
      })),
      edges: (p.edges || []).map((e) => ({
        id: e.id || `e_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        source: e.source,
        target: e.target,
        sourcePort: e.sourcePort || '',
        targetPort: e.targetPort || ''
      })),
      variables: p.variables || { inputs: [], locals: [], returnType: 'Object' }
    }
  } catch {
    return emptyDefinition()
  }
}

/** 输入端口组（左侧，蓝色圆点） */
function buildInPortGroup() {
  return {
    position: 'left' as const,
    attrs: { circle: { r: 4, magnet: true, stroke: '#409eff', fill: '#fff', strokeWidth: 1 } }
  }
}

/** 普通输出端口组（右侧，绿色圆点） */
function buildOutPortGroup() {
  return {
    position: 'right' as const,
    attrs: { circle: { r: 4, magnet: true, stroke: '#67c23a', fill: '#fff', strokeWidth: 1 } }
  }
}

/**
 * 带文字标签的输出端口组（CONDITION 真/假、LOOP 循环/退出）。
 * 通过 markup 同时渲染圆点与文字，使分支语义在画布上可视化。
 */
function buildLabeledOutPortGroup(label: string, color: string, dy: number = 0) {
  return {
    position: {
      name: 'right' as const,
      args: { dy }
    },
    markup: [
      { tagName: 'circle', selector: 'portBody' },
      { tagName: 'text', selector: 'portLabel' }
    ],
    attrs: {
      portBody: { r: 6, magnet: true, stroke: color, fill: '#fff', strokeWidth: 2 },
      portLabel: {
        text: label,
        fill: color,
        fontSize: 11,
        ref: 'portBody',
        refX: -8,
        textAnchor: 'end'
      }
    }
  }
}

/**
 * 构造 X6 节点 addNode 配置（含输入/输出端口）。
 *
 * <p>端口语义：</p>
 * <ul>
 *   <li>CONDITION：true（绿）/ false（红）双输出端口，对应 config.trueBranch/falseBranch</li>
 *   <li>LOOP：body 循环体（蓝）/ exit 退出（灰）双输出端口，对应 config.bodyNodeId</li>
 *   <li>其他节点：单 in/out 端口</li>
 * </ul>
 * <p>端口 ID 规范：`${nodeId}-in` / `-true` / `-false` / `-body` / `-exit` / `-out`，
 * 便于 renderGraph 按 sourcePort 推断连线语义。CONDITION/LOOP 的 config 跳转字段仍保留（后端用）。</p>
 */
function nodeAddConfig(node: MicroflowNode): NodeMetadata {
  const base = {
    shape: MICROFLOW_NODE_SHAPE,
    id: node.id,
    x: node.x,
    y: node.y,
    width: NODE_WIDTH,
    height: NODE_HEIGHT,
    data: {
      nodeId: node.id,
      type: node.type,
      label: node.label
    } as MicroflowNodeData
  }
  if (node.type === 'CONDITION') {
    return {
      ...base,
      ports: {
        groups: {
          in: buildInPortGroup(),
          outTrue: buildLabeledOutPortGroup('真', '#67c23a', -15),
          outFalse: buildLabeledOutPortGroup('假', '#f56c6c', 15)
        },
        items: [
          { id: `${node.id}-in`, group: 'in' },
          { id: `${node.id}-true`, group: 'outTrue' },
          { id: `${node.id}-false`, group: 'outFalse' }
        ]
      }
    }
  }
  if (node.type === 'LOOP') {
    return {
      ...base,
      ports: {
        groups: {
          in: buildInPortGroup(),
          outBody: buildLabeledOutPortGroup('循环体', '#67c23a', -15),
          outExit: buildLabeledOutPortGroup('退出', '#f56c6c', 15)
        },
        items: [
          { id: `${node.id}-in`, group: 'in' },
          { id: `${node.id}-body`, group: 'outBody' },
          { id: `${node.id}-exit`, group: 'outExit' }
        ]
      }
    }
  }
  return {
    ...base,
    ports: {
      groups: {
        in: buildInPortGroup(),
        out: buildOutPortGroup()
      },
      items: [
        { id: `${node.id}-in`, group: 'in' },
        { id: `${node.id}-out`, group: 'out' }
      ]
    }
  }
}

// ===================== 计算属性 =====================

const selectedNode = computed(
  () => definition.value.nodes.find((n) => n.id === selectedNodeId.value) || null
)

/** 传给 NodeParamPanel / ExpressionEditor 的变量列表（输入 + 局部） */
const allVariables = computed<MicroflowVariable[]>(() => [
  ...(definition.value.variables.inputs || []),
  ...(definition.value.variables.locals || [])
])

/** ExecutionLogPanel 的 key，切换微流时强制重置其内部状态 */
const logPanelKey = computed(() => currentMicroflow.value?.id ?? 'new')

// ===================== 数据加载 =====================

async function loadMicroflowList() {
  try {
    microflowList.value = await getMicroflowList()
  } catch {
    ElMessage.error('加载微流列表失败')
  }
}

async function loadOptions() {
  try {
    const [rules, connectors] = await Promise.all([getRuleList(), getConnectorList()])
    ruleOptions.value = rules || []
    connectorOptions.value = connectors || []
  } catch {
    // 下拉数据加载失败不阻塞设计器
  }
}

function selectMicroflow(item: LowCodeMicroflow) {
  currentMicroflow.value = { ...item }
  definition.value = parseDefinition(item.definition)
  selectedNodeId.value = null
  selectedCellId.value = null
  latestExecutionId.value = undefined
  nextTick(() => renderGraph())
}

function onMicroflowChange(id: number | undefined) {
  const item = microflowList.value.find((m) => m.id === id)
  if (item) selectMicroflow(item)
}

function newMicroflow() {
  currentMicroflow.value = {
    code: '',
    name: '',
    description: '',
    definition: '',
    status: 'DRAFT'
  }
  definition.value = emptyDefinition()
  selectedNodeId.value = null
  selectedCellId.value = null
  selectedMicroflowId.value = undefined
  latestExecutionId.value = undefined
  graphRef.value?.clearCells()
}

// ===================== 画布渲染 =====================

/**
 * 根据源端口 ID 推断连线语义（标签 + 颜色）。
 *
 * <p>端口 ID 规范见 nodeAddConfig：`xxx-true` / `xxx-false` / `xxx-body` / `xxx-exit`。
 * CONDITION 真→绿、假→红；LOOP 循环→蓝、退出→灰；其他→默认蓝、无标签。</p>
 */
function edgeSemantic(sourcePort: string): { label: string; color: string } {
  if (sourcePort.endsWith('-true')) return { label: '真', color: '#67c23a' }
  if (sourcePort.endsWith('-false')) return { label: '假', color: '#f56c6c' }
  if (sourcePort.endsWith('-body')) return { label: '循环', color: '#409eff' }
  if (sourcePort.endsWith('-exit')) return { label: '退出', color: '#909399' }
  return { label: '', color: '#409eff' }
}

function renderGraph() {
  const g = graphRef.value
  if (!g) return
  g.clearCells()
  // 若存在无坐标节点，渲染后自动布局
  const needsLayout = definition.value.nodes.some((n) => !n.x && !n.y)
  for (const n of definition.value.nodes) {
    g.addNode(nodeAddConfig(n))
  }
  for (const e of definition.value.edges) {
    const source: { cell: string; port?: string } = { cell: e.source }
    const target: { cell: string; port?: string } = { cell: e.target }
    if (e.sourcePort) source.port = e.sourcePort
    if (e.targetPort) target.port = e.targetPort
    // 按源端口语义着色与标注，使 CONDITION/LOOP 分支在画布上可视化区分
    const { label: edgeLabel, color: edgeColor } = edgeSemantic(e.sourcePort || '')
    g.addEdge({
      id: e.id,
      source,
      target,
      attrs: {
        line: { stroke: edgeColor, strokeWidth: 2, targetMarker: { name: 'classic', size: 6 } }
      },
      labels: edgeLabel
        ? [{ attrs: { label: { text: edgeLabel, fill: '#606266', fontSize: 11 } } }]
        : []
    })
  }
  if (needsLayout && definition.value.nodes.length > 0) {
    autoLayout()
  } else if (definition.value.nodes.length > 0) {
    g.zoomToFit({ padding: 20, maxScale: 1 })
  }
}

// ===================== 节点 / 边操作 =====================

function addNodeToCanvas(type: MicroflowNodeType, x?: number, y?: number) {
  const id = `node_${Date.now()}_${Math.floor(Math.random() * 1000)}`
  const count = definition.value.nodes.length
  // Fix: use distinct x/y formulas so nodes don't stack on the diagonal
  const defaultX = 120 + (count % 6) * 40
  const defaultY = 120 + Math.floor(count / 6) * 100 + (count % 3) * 20
  const node: MicroflowNode = {
    id,
    type,
    label: DEFAULT_LABELS[type],
    x: x ?? defaultX,
    y: y ?? defaultY,
    config: {}
  }
  definition.value.nodes.push(node)
  // Fix: wrap addNode in try/catch to prevent UI freeze on X6 rendering errors
  const g = graphRef.value
  if (g) {
    try {
      g.addNode(nodeAddConfig(node))
    } catch (e) {
      console.error('[Microflow] Failed to add node to canvas:', e)
    }
  } else {
    console.warn('[Microflow] Graph not initialized, node added to data only')
  }
  selectedNodeId.value = id
  selectedCellId.value = id
  setHighlighted(id)
}

function onPaletteAdd(type: MicroflowNodeType) {
  addNodeToCanvas(type)
}

function onCanvasDrop(e: DragEvent) {
  e.preventDefault()
  const type = e.dataTransfer?.getData('microflow-node-type') as MicroflowNodeType | ''
  if (!type) return
  const rect = canvasContainer.value?.getBoundingClientRect()
  const x = (e.clientX - (rect?.left || 0)) - NODE_WIDTH / 2
  const y = (e.clientY - (rect?.top || 0)) - NODE_HEIGHT / 2
  addNodeToCanvas(type, Math.max(0, x), Math.max(0, y))
}

/** NodeParamPanel 编辑回写：更新 definition 节点并同步画布节点 label */
function onUpdateNode(updated: MicroflowNode) {
  const idx = definition.value.nodes.findIndex((n) => n.id === updated.id)
  if (idx >= 0) definition.value.nodes[idx] = updated
  const g = graphRef.value
  if (g) {
    const cell = g.getCellById(updated.id)
    if (cell && cell.isNode()) {
      const data = (cell.getData() || {}) as MicroflowNodeData
      cell.setData({ ...data, label: updated.label })
    }
  }
}

/** 高亮指定节点（画布点击 / 日志点击共用） */
function setHighlighted(nodeId: string | null) {
  const g = graphRef.value
  if (!g) return
  g.getNodes().forEach((n) => {
    const data = (n.getData() || {}) as MicroflowNodeData
    n.setData({ ...data, highlighted: !!nodeId && data.nodeId === nodeId })
  })
}

function deleteSelected() {
  const g = graphRef.value
  if (!g || !selectedCellId.value) return
  const cell = g.getCellById(selectedCellId.value)
  if (!cell) {
    selectedCellId.value = null
    return
  }
  if (cell.isNode()) {
    const id = cell.id
    definition.value.nodes = definition.value.nodes.filter((n) => n.id !== id)
    if (selectedNodeId.value === id) selectedNodeId.value = null
  }
  g.removeCell(cell)
  selectedCellId.value = null
}

// ===================== 自动布局（按 DAG 层级） =====================

function autoLayout() {
  const g = graphRef.value
  if (!g || definition.value.nodes.length === 0) return
  const nodes = definition.value.nodes

  // 邻接表：综合画布边与 CONDITION/LOOP 的 config 跳转目标
  const adj = new Map<string, string[]>()
  nodes.forEach((n) => adj.set(n.id, []))
  g.getEdges().forEach((e) => {
    const s = e.getSourceCellId()
    const t = e.getTargetCellId()
    if (s && t && adj.has(s) && !adj.get(s)!.includes(t)) adj.get(s)!.push(t)
  })
  for (const n of nodes) {
    const cfg = n.config || {}
    for (const k of ['trueBranch', 'falseBranch', 'bodyNodeId'] as const) {
      const v = cfg[k]
      if (typeof v === 'string' && adj.has(n.id) && adj.has(v) && !adj.get(n.id)!.includes(v)) {
        adj.get(n.id)!.push(v)
      }
    }
  }

  // BFS 层级分配（从 START 出发）
  const layer = new Map<string, number>()
  const start = nodes.find((n) => n.type === 'START')?.id || nodes[0].id
  layer.set(start, 0)
  const queue: string[] = [start]
  while (queue.length) {
    const cur = queue.shift()!
    const curLayer = layer.get(cur) || 0
    for (const nb of adj.get(cur) || []) {
      const nl = curLayer + 1
      if ((layer.get(nb) ?? -1) < nl) {
        layer.set(nb, nl)
        queue.push(nb)
      }
    }
  }
  // 未到达节点追加到末尾
  let maxLayer = 0
  layer.forEach((l) => (maxLayer = Math.max(maxLayer, l)))
  nodes.forEach((n) => {
    if (!layer.has(n.id)) {
      maxLayer += 1
      layer.set(n.id, maxLayer)
    }
  })

  // 按层分组排布（左→右）
  const byLayer = new Map<number, string[]>()
  layer.forEach((l, id) => {
    if (!byLayer.has(l)) byLayer.set(l, [])
    byLayer.get(l)!.push(id)
  })
  const gapX = 230
  const gapY = 90
  const startX = 40
  const startY = 40
  byLayer.forEach((ids, l) => {
    ids.forEach((id, i) => {
      const cell = g.getCellById(id)
      if (cell && cell.isNode()) cell.position(startX + l * gapX, startY + i * gapY)
    })
  })
  g.zoomToFit({ padding: 20, maxScale: 1 })
}

function zoomToFit() {
  graphRef.value?.zoomToFit({ padding: 20, maxScale: 1 })
}

async function clearCanvas() {
  try {
    await ElMessageBox.confirm('确认清空画布上所有节点与连线？', '确认', { type: 'warning' })
  } catch {
    return
  }
  graphRef.value?.clearCells()
  definition.value = emptyDefinition()
  selectedNodeId.value = null
  selectedCellId.value = null
}

// ===================== 保存 / 执行 =====================

/**
 * 校验所有节点必填字段（config 字段名与 NodeParamPanel / 后端 MicroflowNodeExecutor 对齐）。
 *
 * <p>校验项：</p>
 * <ul>
 *   <li>ASSIGN：target（目标变量）+ expression（赋值表达式）</li>
 *   <li>CONDITION：expression（条件表达式）</li>
 *   <li>LOOP：iterableExpression（可迭代对象表达式）+ bodyNodeId（循环体起点）</li>
 *   <li>CALL_SERVICE：beanName + methodName</li>
 *   <li>CALL_MICROFLOW：microflowCode</li>
 *   <li>CALL_RULE：ruleCode</li>
 *   <li>CALL_CONNECTOR：connectorCode</li>
 *   <li>THROW_EXCEPTION：errorMessage</li>
 *   <li>RETURN：expression（返回值表达式）</li>
 *   <li>结构校验：必须含 START，且含 END 或 RETURN</li>
 * </ul>
 */
function validateNodes(): string[] {
  const errors: string[] = []
  for (const node of definition.value.nodes) {
    const cfg = node.config || {}
    switch (node.type) {
      case 'ASSIGN':
        if (!cfg.target) errors.push(`节点 ${node.label}：缺少目标变量名`)
        if (!cfg.expression) errors.push(`节点 ${node.label}：缺少赋值表达式`)
        break
      case 'CONDITION':
        if (!cfg.expression) errors.push(`节点 ${node.label}：缺少条件表达式`)
        break
      case 'LOOP':
        if (!cfg.iterableExpression) errors.push(`节点 ${node.label}：缺少可迭代对象表达式`)
        if (!cfg.bodyNodeId) errors.push(`节点 ${node.label}：缺少循环体起点节点`)
        break
      case 'CALL_SERVICE':
        if (!cfg.beanName) errors.push(`节点 ${node.label}：缺少 Bean 名称`)
        if (!cfg.methodName) errors.push(`节点 ${node.label}：缺少方法名`)
        break
      case 'CALL_MICROFLOW':
        if (!cfg.microflowCode) errors.push(`节点 ${node.label}：缺少微流编码`)
        break
      case 'CALL_RULE':
        if (!cfg.ruleCode) errors.push(`节点 ${node.label}：缺少规则编码`)
        break
      case 'CALL_CONNECTOR':
        if (!cfg.connectorCode) errors.push(`节点 ${node.label}：缺少连接器编码`)
        break
      case 'THROW_EXCEPTION':
        if (!cfg.errorMessage) errors.push(`节点 ${node.label}：缺少错误消息`)
        break
      case 'RETURN':
        if (!cfg.expression) errors.push(`节点 ${node.label}：缺少返回值表达式`)
        break
    }
  }
  // 校验必须有 START 节点
  if (!definition.value.nodes.some((n) => n.type === 'START')) {
    errors.push('微流必须包含一个开始节点')
  }
  // 校验必须有 END 或 RETURN 节点
  if (!definition.value.nodes.some((n) => n.type === 'END' || n.type === 'RETURN')) {
    errors.push('微流必须包含结束或返回节点')
  }
  return errors
}

async function save() {
  if (!currentMicroflow.value) {
    ElMessage.warning('请先选择或新建微流')
    return
  }
  if (!currentMicroflow.value.code || !currentMicroflow.value.name) {
    ElMessage.warning('请填写微流编码和名称')
    return
  }
  // 保存前校验节点必填字段
  const errors = validateNodes()
  if (errors.length > 0) {
    ElMessage.warning(`节点配置不完整：\n${errors.join('\n')}`)
    return
  }
  const g = graphRef.value
  if (g) {
    // 同步画布节点位置回 definition
    for (const n of definition.value.nodes) {
      const cell = g.getCellById(n.id)
      if (cell && cell.isNode()) {
        const pos = cell.position()
        n.x = pos.x
        n.y = pos.y
      }
    }
    // 序列化画布边
    definition.value.edges = g.getEdges().map((e) => ({
      id: e.id,
      source: e.getSourceCellId() ?? '',
      target: e.getTargetCellId() ?? '',
      sourcePort: e.getSourcePortId() ?? '',
      targetPort: e.getTargetPortId() ?? ''
    }))
  }
  currentMicroflow.value.definition = JSON.stringify(definition.value)
  try {
    const saved = await saveMicroflow(currentMicroflow.value)
    currentMicroflow.value = saved
    selectedMicroflowId.value = saved.id
    ElMessage.success('保存成功')
    await loadMicroflowList()
  } catch {
    ElMessage.error('保存失败')
  }
}

function openExec() {
  if (!currentMicroflow.value?.id) {
    ElMessage.warning('请先保存微流')
    return
  }
  dialogMode.value = 'execute'
  execInputs.value = ''
  execDialogVisible.value = true
}

/** 对话框确认：按 dialogMode 分发到执行或调试 */
async function doExecute() {
  if (!currentMicroflow.value?.id) return
  let inputs: Record<string, unknown> = {}
  try {
    inputs = execInputs.value ? JSON.parse(execInputs.value) : {}
  } catch {
    ElMessage.error('输入参数 JSON 解析失败')
    return
  }
  execDialogVisible.value = false
  if (dialogMode.value === 'debug') {
    await doStartDebug(inputs)
    return
  }
  logCollapsed.value = false
  try {
    const result = await executeMicroflow(currentMicroflow.value.code, inputs)
    ElMessage.success('执行完成')
    // 展开日志面板并拉取最新轨迹
    await fetchLatestExecutionLogs(currentMicroflow.value.id)
    // result 不含 executionId，仅作提示
    void result
  } catch (e) {
    ElMessage.error('执行失败：' + (e as Error).message)
    // 执行失败时 FAILED 轨迹已记录，仍拉取展示
    await fetchLatestExecutionLogs(currentMicroflow.value.id)
  }
}

// ===================== 断点调试 =====================

/** 切换选中节点的断点（无选中则提示） */
function toggleBreakpoint() {
  const nodeId = selectedNodeId.value
  if (!nodeId) {
    ElMessage.warning('请先选中一个节点')
    return
  }
  toggleBreakpointAt(nodeId)
}

/** 切换指定节点的断点 */
function toggleBreakpointAt(nodeId: string) {
  const set = new Set(breakpoints.value)
  if (set.has(nodeId)) {
    set.delete(nodeId)
  } else {
    set.add(nodeId)
  }
  breakpoints.value = set
  refreshDebugMarkers()
}

/** 打开调试输入对话框 */
function openDebug() {
  if (!currentMicroflow.value?.id) {
    ElMessage.warning('请先保存微流')
    return
  }
  if (isDebugging.value) {
    ElMessage.warning('当前已在调试中，请先终止')
    return
  }
  dialogMode.value = 'debug'
  execInputs.value = ''
  execDialogVisible.value = true
}

/** 启动调试会话 */
async function doStartDebug(inputs: Record<string, unknown>) {
  if (!currentMicroflow.value) return
  debugLoading.value = true
  try {
    const session = await startMicroflowDebug(currentMicroflow.value.code, {
      inputs,
      breakpointNodeIds: Array.from(breakpoints.value)
    })
    debugSessionId.value = session.sessionId
    isDebugging.value = true
    debugVariables.value = session.variables || {}
    debugStatus.value = 'PAUSED'
    debugCurrentNodeId.value = session.currentNodeId
    debugResult.value = undefined
    debugVariablesVisible.value = true
    clearAllNodeStatuses()
    refreshDebugMarkers()
    centerOnNode(session.currentNodeId)
    ElMessage.success('调试会话已启动')
  } catch (e) {
    ElMessage.error('启动调试失败：' + (e as Error).message)
  } finally {
    debugLoading.value = false
  }
}

/** 单步执行 */
async function doStepOver() {
  if (!debugSessionId.value || debugLoading.value) return
  debugLoading.value = true
  try {
    const result = await stepOverMicroflowDebug(debugSessionId.value)
    applyStepResult(result)
  } catch (e) {
    ElMessage.error('单步执行失败：' + (e as Error).message)
  } finally {
    debugLoading.value = false
  }
}

/** 继续执行到下一断点 */
async function doContinueDebug() {
  if (!debugSessionId.value || debugLoading.value) return
  debugLoading.value = true
  try {
    const result = await continueMicroflowDebug(debugSessionId.value)
    applyStepResult(result)
  } catch (e) {
    ElMessage.error('继续执行失败：' + (e as Error).message)
  } finally {
    debugLoading.value = false
  }
}

/** 终止调试会话 */
async function doTerminateDebug() {
  if (!debugSessionId.value) {
    resetDebugState()
    return
  }
  try {
    await terminateMicroflowDebug(debugSessionId.value)
  } catch {
    // 忽略终止失败
  }
  resetDebugState()
  ElMessage.info('调试会话已终止')
}

/** 重置调试本地状态 */
function resetDebugState() {
  isDebugging.value = false
  debugSessionId.value = null
  debugVariables.value = {}
  debugStatus.value = null
  debugCurrentNodeId.value = null
  debugResult.value = undefined
  debugLoading.value = false
  refreshDebugMarkers()
}

/** 应用单步/继续结果到调试状态与画布 */
function applyStepResult(result: MicroflowDebugStepResult) {
  debugVariables.value = result.variables || {}
  debugStatus.value = result.status
  debugResult.value = result.result
  // 已执行节点标记 SUCCESS / FAILED
  setNodeStatus(result.nodeId, result.status === 'FAILED' ? 'FAILED' : 'SUCCESS')
  if (result.status === 'COMPLETED' || result.status === 'FAILED') {
    debugCurrentNodeId.value = null
    if (result.status === 'COMPLETED') {
      ElMessage.success('微流执行完成')
    } else {
      ElMessage.error('节点执行失败：' + (result.errorMessage || '未知错误'))
    }
  } else {
    debugCurrentNodeId.value = result.nextNodeId
  }
  refreshDebugMarkers()
  centerOnNode(debugCurrentNodeId.value)
}

/** 将调试标记（断点 / 当前节点）同步到画布所有节点 */
function refreshDebugMarkers() {
  const g = graphRef.value
  if (!g) return
  g.getNodes().forEach((node) => {
    const data = (node.getData() || {}) as MicroflowNodeData
    node.setData({
      ...data,
      breakpoint: breakpoints.value.has(data.nodeId),
      debugCurrent: data.nodeId === debugCurrentNodeId.value
    })
  })
}

/** 设置某节点的执行状态 */
function setNodeStatus(nodeId: string | null, status: string | null) {
  const g = graphRef.value
  if (!g || !nodeId) return
  const cell = g.getCellById(nodeId)
  if (cell && cell.isNode()) {
    const data = (cell.getData() || {}) as MicroflowNodeData
    cell.setData({ ...data, status: status || undefined })
  }
}

/** 清空所有节点执行状态（调试开始时重置画布） */
function clearAllNodeStatuses() {
  const g = graphRef.value
  if (!g) return
  g.getNodes().forEach((node) => {
    const data = (node.getData() || {}) as MicroflowNodeData
    node.setData({ ...data, status: undefined })
  })
}

/** 居中到指定节点 */
function centerOnNode(nodeId: string | null) {
  if (!nodeId) return
  const g = graphRef.value
  if (!g) return
  const cell = g.getCellById(nodeId)
  if (cell && cell.isNode()) g.centerCell(cell)
}

/** 调试状态标签类型 */
const debugStatusTagType = computed<
  'success' | 'warning' | 'danger' | 'info'
>(() => {
  if (debugStatus.value === 'COMPLETED') return 'success'
  if (debugStatus.value === 'FAILED') return 'danger'
  if (debugStatus.value === 'PAUSED') return 'warning'
  return 'info'
})

/** 调试状态文案 */
const debugStatusText = computed(() => {
  if (!isDebugging.value) return ''
  if (debugStatus.value === 'COMPLETED') return '已完成'
  if (debugStatus.value === 'FAILED') return '失败'
  if (debugStatus.value === 'PAUSED') return '已暂停'
  return '调试中'
})

/** 变量监视面板：将变量转为可展示的键值对列表 */
const debugVariableEntries = computed(() => {
  return Object.entries(debugVariables.value).map(([k, v]) => ({
    key: k,
    value: formatDebugValue(v)
  }))
})

/** 格式化调试变量值（对象/数组转 JSON，其余转字符串） */
function formatDebugValue(v: unknown): string {
  if (v === null) return 'null'
  if (v === undefined) return 'undefined'
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

/** 执行后取最新一次执行的 executionId，交给 ExecutionLogPanel 加载并高亮画布 */
async function fetchLatestExecutionLogs(microflowId: number) {
  try {
    const recent = await getRecentExecutionLogs(microflowId, 50)
    if (recent && recent.length > 0) {
      latestExecutionId.value = recent[0].executionId
    }
  } catch {
    // 忽略轨迹查询失败
  }
}

/** ExecutionLogPanel 加载完日志后，按 status 同步画布节点状态 */
function onLogsLoaded(logs: MicroflowExecutionLog[]) {
  const g = graphRef.value
  if (!g) return
  const statusMap = new Map<string, string>()
  for (const l of logs) statusMap.set(l.nodeId, l.status)
  g.getNodes().forEach((node) => {
    const data = (node.getData() || {}) as MicroflowNodeData
    const status = statusMap.get(data.nodeId)
    node.setData({ ...data, status })
  })
}

/** 点击日志条目 → 高亮并居中对应画布节点 */
function onHighlightNode(nodeId: string) {
  setHighlighted(nodeId)
  const g = graphRef.value
  if (!g) return
  const cell = g.getCellById(nodeId)
  if (cell && cell.isNode()) g.centerCell(cell)
}

// ===================== 键盘删除 =====================

function onKeyDown(e: KeyboardEvent) {
  if (e.key !== 'Delete' && e.key !== 'Backspace') return
  const el = e.target as HTMLElement | null
  const tag = el?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || el?.isContentEditable) return
  if (selectedCellId.value) {
    e.preventDefault()
    deleteSelected()
  } else if (e.key === 'Backspace') {
    // Prevent browser "back" navigation when no cell is selected
    e.preventDefault()
  }
}

// ===================== Graph 初始化 =====================

function initGraph() {
  if (!canvasContainer.value) return
  const g = new Graph({
    container: canvasContainer.value,
    background: { color: '#f7f8fa' },
    grid: { visible: true, size: 10, type: 'dot' },
    interacting: { nodeMovable: true, edgeMovable: true },
    panning: true,
    mousewheel: { enabled: true, modifiers: ['ctrl'] },
    connecting: {
      allowBlank: false,
      allowLoop: false,
      allowMulti: true,
      router: 'orth',
      connector: 'rounded',
      createEdge() {
        return this.createEdge({
          shape: 'edge',
          attrs: { line: { stroke: '#409eff', strokeWidth: 2, targetMarker: { name: 'classic', size: 6 } } }
        })
      }
    }
  })

  g.on('node:click', ({ node }) => {
    const data = (node.getData() || {}) as MicroflowNodeData
    selectedNodeId.value = data.nodeId ?? null
    selectedCellId.value = node.id
    setHighlighted(data.nodeId ?? null)
  })
  g.on('edge:click', ({ edge }) => {
    selectedNodeId.value = null
    selectedCellId.value = edge.id
    setHighlighted(null)
  })
  g.on('blank:click', () => {
    selectedNodeId.value = null
    selectedCellId.value = null
    setHighlighted(null)
  })

  graphRef.value = g
}

onMounted(async () => {
  // Register microflow custom Vue node (wrap in try/catch: re-entering the page
  // may cause x6-vue-shape to throw "shape already registered")
  try {
    register({
      shape: MICROFLOW_NODE_SHAPE,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
      component: MicroflowNodeComp
    })
  } catch (e) {
    console.warn('[Microflow] Node shape registration skipped (may already be registered):', e)
  }
  try {
    initGraph()
  } catch (e) {
    console.error('[Microflow] Failed to initialize graph:', e)
  }
  await Promise.all([loadMicroflowList(), loadOptions()])
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  graphRef.value?.dispose()
  graphRef.value = null
})

// 显式标注 getExecutionLogs 已在面板内使用，避免摇树告警（保留导入以便后续直接调用）
void getExecutionLogs
</script>

<template>
  <div class="microflow-designer">
    <!-- 顶栏：微流管理 + 画布工具 -->
    <div class="top-bar">
      <el-select
        :model-value="selectedMicroflowId"
        placeholder="选择微流"
        filterable
        clearable
        class="microflow-select"
        @change="onMicroflowChange"
      >
        <el-option
          v-for="m in microflowList"
          :key="m.id"
          :label="`${m.name} (${m.code})`"
          :value="m.id"
        />
      </el-select>
      <el-button size="small" @click="newMicroflow">新建</el-button>
      <el-divider direction="vertical" />
      <el-button size="small" type="primary" @click="save">保存</el-button>
      <el-button size="small" type="success" @click="openExec">执行</el-button>
      <el-button size="small" @click="clearCanvas">清空</el-button>
      <el-divider direction="vertical" />
      <el-button size="small" @click="autoLayout">自动布局</el-button>
      <el-button size="small" @click="zoomToFit">缩放适配</el-button>
      <el-button size="small" type="warning" @click="deleteSelected">删除选中</el-button>

      <!-- 调试工具组：断点切换 / 开始调试 / 单步 / 继续 / 终止 / 变量监视 -->
      <el-divider direction="vertical" />
      <el-button
        size="small"
        :disabled="!selectedNodeId"
        @click="toggleBreakpoint"
      >切换断点</el-button>
      <template v-if="!isDebugging">
        <el-button size="small" type="primary" @click="openDebug">开始调试</el-button>
      </template>
      <template v-else>
        <el-tag :type="debugStatusTagType" size="small">{{ debugStatusText }}</el-tag>
        <el-button
          size="small"
          type="primary"
          :loading="debugLoading"
          :disabled="debugStatus !== 'PAUSED'"
          @click="doStepOver"
        >单步</el-button>
        <el-button
          size="small"
          type="success"
          :loading="debugLoading"
          :disabled="debugStatus !== 'PAUSED'"
          @click="doContinueDebug"
        >继续</el-button>
        <el-button
          size="small"
          type="danger"
          :loading="debugLoading"
          @click="doTerminateDebug"
        >终止</el-button>
        <el-button size="small" @click="debugVariablesVisible = true">变量</el-button>
      </template>
    </div>

    <!-- 主体：三栏 -->
    <div class="main">
      <!-- 左栏：节点面板 -->
      <NodePalette @add-node="onPaletteAdd" />

      <!-- 中栏：画布 -->
      <div class="canvas-panel">
        <div
          ref="canvasContainer"
          class="canvas-container"
          @drop="onCanvasDrop"
          @dragover.prevent
        />
      </div>

      <!-- 右栏：属性面板 -->
      <div class="right-panel">
        <NodeParamPanel
          v-if="selectedNode"
          :key="selectedNode.id"
          :node="selectedNode"
          :nodes="definition.nodes"
          :microflow-options="microflowList"
          :rule-options="ruleOptions"
          :connector-options="connectorOptions"
          :variables="allVariables"
          @update:node="onUpdateNode"
        />
        <template v-else>
          <MicroflowMetaPanel
            :microflow="currentMicroflow"
            @update:microflow="currentMicroflow = $event"
          />
          <div class="right-section">
            <div class="right-section-title">变量</div>
            <VariablePanel
              :variables="definition.variables"
              @update:variables="definition.variables = $event"
            />
          </div>
        </template>
      </div>
    </div>

    <!-- 底部：执行日志面板 -->
    <ExecutionLogPanel
      :key="logPanelKey"
      :microflow-id="currentMicroflow?.id"
      :execution-id="latestExecutionId"
      v-model:collapsed="logCollapsed"
      @highlight-node="onHighlightNode"
      @logs-loaded="onLogsLoaded"
    />

    <!-- 执行/调试输入对话框（按 dialogMode 切换标题与确认按钮文案） -->
    <el-dialog
      v-model="execDialogVisible"
      :title="dialogMode === 'debug' ? '调试输入' : '执行输入'"
      width="600px"
    >
      <el-input
        v-model="execInputs"
        type="textarea"
        :rows="6"
        placeholder='{"key":"value"}'
      />
      <template #footer>
        <el-button @click="execDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doExecute">
          {{ dialogMode === 'debug' ? '开始调试' : '执行' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 调试变量监视抽屉：显示当前状态、当前节点、变量快照、最终结果 -->
    <el-drawer
      v-model="debugVariablesVisible"
      title="变量监视"
      direction="rtl"
      size="440px"
    >
      <div class="debug-panel">
        <div class="debug-section">
          <span class="debug-label">状态：</span>
          <el-tag :type="debugStatusTagType" size="small">{{ debugStatusText }}</el-tag>
        </div>
        <div v-if="debugCurrentNodeId" class="debug-section">
          <span class="debug-label">当前节点：</span>
          <span class="debug-value">{{ debugCurrentNodeId }}</span>
        </div>
        <div class="debug-section">
          <div class="debug-label">变量：</div>
          <el-table
            :data="debugVariableEntries"
            border
            size="small"
            empty-text="暂无变量"
          >
            <el-table-column prop="key" label="名称" width="140" />
            <el-table-column prop="value" label="值" show-overflow-tooltip />
          </el-table>
        </div>
        <div v-if="debugResult !== undefined" class="debug-section">
          <div class="debug-label">结果：</div>
          <pre class="debug-result">{{ formatDebugValue(debugResult) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.microflow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  background: #f0f2f5;
}

.top-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;

  .microflow-select {
    width: 240px;
  }
}

.main {
  flex: 1;
  display: flex;
  min-height: 0;
  gap: 1px;
  background: #dcdfe6;
}

.canvas-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  display: flex;
  flex-direction: column;

  .canvas-container {
    flex: 1;
    overflow: hidden;
  }
}

.right-panel {
  width: 480px;
  min-width: 360px;
  background: #fff;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.right-section {
  border-top: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}

.right-section-title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
}

// 调试变量监视抽屉内容样式
.debug-panel {
  padding: 0 12px;

  .debug-section {
    margin-bottom: 16px;
    font-size: 13px;
    color: #303133;
  }

  .debug-label {
    font-weight: 600;
    color: #606266;
    margin-right: 4px;
  }

  .debug-value {
    color: #303133;
  }

  .debug-result {
    margin: 4px 0 0;
    padding: 8px;
    background: #f5f7fa;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    font-size: 12px;
    color: #303133;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 240px;
    overflow: auto;
  }
}
</style>
