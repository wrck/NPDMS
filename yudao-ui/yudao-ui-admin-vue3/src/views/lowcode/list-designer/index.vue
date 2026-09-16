<script setup lang="ts">
/**
 * 低代码列表设计器。
 *
 * <p>三栏布局：</p>
 * <ul>
 *   <li>左侧：组件库面板（列组件 / 筛选组件 / 操作组件 三大类）</li>
 *   <li>中间：画布（4 个 Tab：列配置 / 筛选配置 / 操作配置 / 工具栏配置），
 *       每个 Tab 内字段可拖拽排序、点击选中编辑</li>
 *   <li>右侧：属性面板（按当前 Tab 与选中项展示对应属性编辑表单）</li>
 * </ul>
 *
 * <p>顶部操作栏：保存草稿 / 发布 / 导入 / 导出 / 预览 / 重置，以及列表元信息编辑。</p>
 *
 * <p>使用原生 HTML5 拖拽 API（draggable + dragstart/dragover/drop）实现，
 * 支持组件库 → 画布拖拽、画布内排序拖拽两种交互。字段 ID 自动生成
 * （col_N / filter_N / op_N），点击"预览"打开 LowCodeListRenderer 弹窗。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  ActionType,
  ButtonType,
  ColumnType,
  createList,
  deleteList,
  exportList,
  FilterType,
  getList,
  importList,
  ListLayout,
  publishList,
  updateList,
  archiveList,
  type ListColumnConfig,
  type ListConfig,
  type ListFilterConfig,
  type ListOperationConfig,
  type LowCodeListConfig,
  type ResponsiveSpan
} from '@/api/lowcode'
import LowCodeListRenderer from '@/components/LowCodeListRenderer/index.vue'
import LowCodeComponentRegistry, {
  initBuiltinComponents
} from '@/components/LowCodeComponentRegistry'
import type { ComponentMeta } from '@/components/LowCodeComponentRegistry/types'
import { useUndoRedo } from '@/composables/useUndoRedo'

const route = useRoute()
const router = useRouter()

// ===================== 组件库定义 =====================

interface ComponentDef {
  /** 组件 type，与 ListConfigSchema 常量对应 */
  type: string
  /** 显示标签 */
  label: string
  /** Element Plus 图标名 */
  icon: string
  /** 默认属性 */
  defaultProps?: Record<string, unknown>
  /** 适用的 Tab：column / filter / operation */
  category: 'column' | 'filter' | 'operation'
}

/** 组件库分组 */
const componentGroups = ref<Array<{ title: string; items: ComponentDef[] }>>([
  {
    title: '列组件',
    items: [
      { type: ColumnType.TEXT, label: '文本', icon: 'Document', category: 'column', defaultProps: {} },
      { type: ColumnType.IMAGE, label: '图片', icon: 'Picture', category: 'column', defaultProps: { imageWidth: 60, imageHeight: 60 } },
      { type: ColumnType.TAG, label: '标签', icon: 'PriceTag', category: 'column', defaultProps: { tagType: 'primary' } },
      { type: ColumnType.DATE, label: '日期', icon: 'Calendar', category: 'column', defaultProps: {} },
      { type: ColumnType.DATETIME, label: '日期时间', icon: 'Clock', category: 'column', defaultProps: {} },
      { type: ColumnType.CURRENCY, label: '货币', icon: 'Money', category: 'column', defaultProps: {} },
      { type: ColumnType.PERCENT, label: '百分比', icon: 'DataLine', category: 'column', defaultProps: {} },
      { type: ColumnType.LINK, label: '链接', icon: 'Link', category: 'column', defaultProps: { linkUrl: '' } },
      { type: ColumnType.DICT, label: '字典', icon: 'Collection', category: 'column', defaultProps: { dictCode: '' } },
      { type: ColumnType.CUSTOM, label: '自定义', icon: 'Setting', category: 'column', defaultProps: {} }
    ]
  },
  {
    title: '筛选组件',
    items: [
      { type: FilterType.INPUT, label: '输入框', icon: 'EditPen', category: 'filter', defaultProps: { span: 6 } },
      { type: FilterType.SELECT, label: '下拉选择', icon: 'ArrowDown', category: 'filter', defaultProps: { span: 6, options: [] } },
      { type: FilterType.DATE, label: '日期', icon: 'Calendar', category: 'filter', defaultProps: { span: 6 } },
      { type: FilterType.DATERANGE, label: '日期范围', icon: 'Calendar', category: 'filter', defaultProps: { span: 12 } },
      { type: FilterType.CASCADER, label: '级联选择', icon: 'Share', category: 'filter', defaultProps: { span: 6, options: [] } }
    ]
  },
  {
    title: '操作组件',
    items: [
      { type: ActionType.EDIT, label: '编辑', icon: 'Edit', category: 'operation', defaultProps: { type: ButtonType.PRIMARY, action: ActionType.EDIT, url: '' } },
      { type: ActionType.VIEW, label: '查看', icon: 'View', category: 'operation', defaultProps: { type: ButtonType.TEXT, action: ActionType.VIEW, url: '' } },
      { type: ActionType.DELETE, label: '删除', icon: 'Delete', category: 'operation', defaultProps: { type: ButtonType.DANGER, action: ActionType.DELETE, api: '', method: 'DELETE', confirm: '确认删除？' } },
      { type: ActionType.CUSTOM, label: '自定义按钮', icon: 'Operation', category: 'operation', defaultProps: { type: ButtonType.PRIMARY, action: ActionType.CUSTOM } },
      { type: ActionType.CREATE, label: '新增（工具栏）', icon: 'Plus', category: 'operation', defaultProps: { type: ButtonType.PRIMARY, action: ActionType.CREATE, url: '' } }
    ]
  }
])

/** 注册中心业务组件（供"自定义列"绑定 componentName） */
const registryComponents = ref<ComponentMeta[]>([])

// ===================== 元信息 + 配置状态 =====================

/** 列表元信息（对应 LowCodeListConfig 的非 listConfig 字段） */
const metaForm = reactive<LowCodeListConfig>({
  code: '',
  name: '',
  description: '',
  listConfig: '',
  status: 'DRAFT',
  bizType: '',
  version: 1
})

/** 设计器内部维护的 ListConfig 对象 */
const listConfig = reactive<ListConfig>({
  title: '',
  description: '',
  searchApi: '',
  method: 'GET',
  pageSize: 20,
  pageSizes: [10, 20, 50, 100],
  layout: ListLayout.TABLE,
  stripe: true,
  border: true,
  showSelection: false,
  showIndex: true,
  showPagination: true,
  columns: [],
  filters: [],
  operations: [
    { id: 'op_1', label: '查看', type: ButtonType.TEXT, action: ActionType.VIEW, icon: 'View' },
    { id: 'op_2', label: '编辑', type: ButtonType.PRIMARY, action: ActionType.EDIT, icon: 'Edit' },
    { id: 'op_3', label: '删除', type: ButtonType.DANGER, action: ActionType.DELETE, icon: 'Delete', confirm: '确认删除？' }
  ],
  toolbar: [
    { id: 'op_4', label: '新建', type: ButtonType.PRIMARY, action: ActionType.CREATE, icon: 'Plus' }
  ],
  export: { enabled: false }
})

/** 当前 Tab */
const activeTab = ref<'column' | 'filter' | 'operation' | 'toolbar'>('column')

/** 当前选中项 id（列/筛选/操作的 id） */
const selectedId = ref<string>('')

/** 各类型 ID 计数器 */
let colSeq = 0
let filterSeq = 0
let opSeq = 4

/** 元信息表单 ref */
const metaFormRef = ref<FormInstance>()
const metaRules: FormRules = {
  code: [{ required: true, message: '请输入列表编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入列表名称', trigger: 'blur' }]
}

const loading = ref(false)
const previewVisible = ref(false)

// ===================== 选中项计算 =====================

/** 当前选中的列 */
const selectedColumn = computed(() =>
  listConfig.columns.find((c) => c.id === selectedId.value) || null
)
const isSelectedRegistryColumn = computed(
  () => !!selectedColumn.value &&
    selectedColumn.value.type === ColumnType.CUSTOM &&
    !!selectedColumn.value.componentName
)
const selectedColumnComponentMeta = computed<ComponentMeta | null>(() => {
  const col = selectedColumn.value
  if (!col?.componentName) return null
  return LowCodeComponentRegistry.get(col.componentName)?.meta || null
})
/** 当前选中的筛选项 */
const selectedFilter = computed(() =>
  listConfig.filters?.find((f) => f.id === selectedId.value) || null
)

// ===================== 响应式栅格断点（xs/sm/md/lg/xl） =====================
// 断点类型与顺序从统一 SSOT 导入（批次4-T9），避免本地重复定义导致口径漂移
import {
  BREAKPOINT_ORDER as breakpointOrder,
  type Breakpoint
} from '@/styles/breakpoints'

/** 响应式断点折叠面板激活项（默认展开） */
const filterResponsiveCollapse = ref<string[]>(['resp'])

/** 当前选中筛选项是否启用响应式断点（span 为对象） */
const isFilterResponsive = computed<boolean>({
  get: () => !!selectedFilter.value && typeof selectedFilter.value.span === 'object',
  set: (val: boolean) => {
    const filter = selectedFilter.value
    if (!filter) return
    if (val) {
      const cur = typeof filter.span === 'number' ? filter.span : 6
      filter.span = { xs: cur, sm: cur, md: cur, lg: cur, xl: cur }
    } else {
      const obj = filter.span
      filter.span = typeof obj === 'object' && obj ? (obj.md ?? 6) : 6
    }
  }
})

/** 非响应式模式下的栅格宽度（数字） */
const filterSpan = computed<number>({
  get: () => (typeof selectedFilter.value?.span === 'number' ? selectedFilter.value.span : 6),
  set: (v: number) => {
    if (selectedFilter.value) selectedFilter.value.span = v
  }
})

/** 读取指定断点值（undefined 表示留空继承更小断点，与 form-designer 对齐） */
function getFilterBreakpoint(k: Breakpoint): number | undefined {
  const s = selectedFilter.value?.span
  if (typeof s !== 'object' || !s) return undefined
  const v = s[k]
  return typeof v === 'number' && !Number.isNaN(v) ? v : undefined
}

/** 设置指定断点值（v=undefined 时删除该 key，体现"留空即继承"语义） */
function setFilterBreakpoint(k: Breakpoint, v: number | undefined): void {
  const filter = selectedFilter.value
  if (!filter) return
  const s = filter.span
  const obj: ResponsiveSpan = typeof s === 'object' && s ? { ...s } : {}
  if (v === undefined || v === null || Number.isNaN(v)) {
    delete obj[k]
  } else {
    obj[k] = v
  }
  // 若所有断点都被清除，回退为数字 span（简化数据结构）
  const hasAny = (['xs', 'sm', 'md', 'lg', 'xl'] as Breakpoint[]).some(
    (bp) => obj[bp] !== undefined
  )
  filter.span = hasAny ? obj : 6
}
/** 当前选中的行操作 */
const selectedOperation = computed(() =>
  listConfig.operations?.find((o) => o.id === selectedId.value) || null
)
/** 当前选中的工具栏操作 */
const selectedToolbar = computed(() =>
  listConfig.toolbar?.find((o) => o.id === selectedId.value) || null
)

/** 当前 Tab 对应的选中项 */
const selectedItem = computed(() => {
  switch (activeTab.value) {
    case 'column':
      return selectedColumn.value
    case 'filter':
      return selectedFilter.value
    case 'operation':
      return selectedOperation.value
    case 'toolbar':
      return selectedToolbar.value
    default:
      return null
  }
})

// ===================== 创建新项 =====================

/** 生成列对象 */
function createColumn(type: string, label: string, extraProps: Record<string, unknown> = {}): ListColumnConfig {
  colSeq++
  return {
    id: `col_${colSeq}`,
    prop: `col${colSeq}`,
    label,
    type,
    width: 120,
    align: 'left',
    hidden: false,
    editable: false,
    ...extraProps
  }
}

/** 生成筛选项对象 */
function createFilter(type: string, label: string, extraProps: Record<string, unknown> = {}): ListFilterConfig {
  filterSeq++
  return {
    id: `filter_${filterSeq}`,
    prop: `filter${filterSeq}`,
    label,
    type,
    placeholder: `请输入${label}`,
    clearable: true,
    span: 6,
    ...extraProps
  }
}

/** 生成操作按钮对象 */
function createOperation(extraProps: Record<string, unknown> = {}): ListOperationConfig {
  opSeq++
  return {
    id: `op_${opSeq}`,
    label: '操作',
    type: ButtonType.PRIMARY,
    action: ActionType.CUSTOM,
    ...extraProps
  }
}

// ===================== 添加 / 删除 / 复制 / 移动 =====================

/** 添加项（点击组件库或拖拽放置） */
function addComponent(comp: ComponentDef) {
  if (comp.category === 'column') {
    const col = createColumn(comp.type, comp.label, comp.defaultProps || {})
    listConfig.columns.push(col)
    selectedId.value = col.id
  } else if (comp.category === 'filter') {
    const f = createFilter(comp.type, comp.label, comp.defaultProps || {})
    listConfig.filters = listConfig.filters || []
    listConfig.filters.push(f)
    selectedId.value = f.id
  } else if (comp.category === 'operation') {
    const op = createOperation(comp.defaultProps || {})
    if (comp.type === ActionType.CREATE) {
      listConfig.toolbar = listConfig.toolbar || []
      listConfig.toolbar.push(op)
      activeTab.value = 'toolbar'
    } else {
      listConfig.operations = listConfig.operations || []
      listConfig.operations.push(op)
      activeTab.value = 'operation'
    }
    selectedId.value = op.id
  }
}

/** 通用删除 */
function removeItem(id: string) {
  listConfig.columns = listConfig.columns.filter((c) => c.id !== id)
  listConfig.filters = (listConfig.filters || []).filter((f) => f.id !== id)
  listConfig.operations = (listConfig.operations || []).filter((o) => o.id !== id)
  listConfig.toolbar = (listConfig.toolbar || []).filter((o) => o.id !== id)
  if (selectedId.value === id) selectedId.value = ''
}

/** 复制项（按 id 在对应 Tab 中复制） */
function duplicateItem(id: string) {
  const col = listConfig.columns.find((c) => c.id === id)
  if (col) {
    colSeq++
    const copy: ListColumnConfig = JSON.parse(JSON.stringify(col))
    copy.id = `col_${colSeq}`
    copy.prop = `${col.prop}_copy`
    copy.label = `${col.label}_副本`
    const idx = listConfig.columns.findIndex((c) => c.id === id)
    listConfig.columns.splice(idx + 1, 0, copy)
    selectedId.value = copy.id
    return
  }
  const f = listConfig.filters?.find((x) => x.id === id)
  if (f) {
    filterSeq++
    const copy: ListFilterConfig = JSON.parse(JSON.stringify(f))
    copy.id = `filter_${filterSeq}`
    copy.prop = `${f.prop}_copy`
    copy.label = `${f.label}_副本`
    const idx = listConfig.filters!.findIndex((x) => x.id === id)
    listConfig.filters!.splice(idx + 1, 0, copy)
    selectedId.value = copy.id
    return
  }
  const op = listConfig.operations?.find((x) => x.id === id)
  if (op) {
    opSeq++
    const copy: ListOperationConfig = JSON.parse(JSON.stringify(op))
    copy.id = `op_${opSeq}`
    copy.label = `${op.label}_副本`
    const idx = listConfig.operations!.findIndex((x) => x.id === id)
    listConfig.operations!.splice(idx + 1, 0, copy)
    selectedId.value = copy.id
    return
  }
  const tb = listConfig.toolbar?.find((x) => x.id === id)
  if (tb) {
    opSeq++
    const copy: ListOperationConfig = JSON.parse(JSON.stringify(tb))
    copy.id = `op_${opSeq}`
    copy.label = `${tb.label}_副本`
    const idx = listConfig.toolbar!.findIndex((x) => x.id === id)
    listConfig.toolbar!.splice(idx + 1, 0, copy)
    selectedId.value = copy.id
  }
}

/** 通用上下移动（按当前 Tab 操作） */
function moveItem(id: string, direction: -1 | 1) {
  let arr: Array<{ id: string }> | undefined
  switch (activeTab.value) {
    case 'column':
      arr = listConfig.columns
      break
    case 'filter':
      arr = listConfig.filters
      break
    case 'operation':
      arr = listConfig.operations
      break
    case 'toolbar':
      arr = listConfig.toolbar
      break
  }
  if (!arr) return
  const idx = arr.findIndex((x) => x.id === id)
  if (idx < 0) return
  const newIdx = idx + direction
  if (newIdx < 0 || newIdx >= arr.length) return
  const tmp = arr[idx]
  arr[idx] = arr[newIdx]
  arr[newIdx] = tmp
}

/** 选中项 */
function selectItem(id: string) {
  selectedId.value = id
}

// ===================== 拖拽 =====================

let dragType = ''
let dragItemId = ''

/** 组件库 dragstart */
function onCompDragStart(event: DragEvent, comp: ComponentDef) {
  dragType = comp.type
  dragItemId = ''
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData('text/plain', `comp:${comp.type}`)
  }
}

/** 画布项 dragstart（用于排序） */
function onItemDragStart(event: DragEvent, id: string) {
  dragItemId = id
  dragType = ''
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `item:${id}`)
  }
}

/** 画布 dragover */
function onCanvasDragOver(event: DragEvent) {
  if (event.dataTransfer) event.dataTransfer.dropEffect = dragType ? 'copy' : 'move'
  event.preventDefault()
}

/** 画布 drop：组件库 → 添加；画布项 → 不变（排序通过 drop 到目标 item 实现） */
function onCanvasDrop(event: DragEvent) {
  event.preventDefault()
  const raw = event.dataTransfer?.getData('text/plain') || ''
  if (raw.startsWith('comp:')) {
    const type = raw.slice(5)
    for (const g of componentGroups.value) {
      const comp = g.items.find((c) => c.type === type)
      if (comp) {
        addComponent(comp)
        return
      }
    }
  }
}

/** 项 drop：将 dragItemId 移动到 targetId 之前 */
function onItemDrop(event: DragEvent, targetId: string) {
  event.preventDefault()
  if (!dragItemId || dragItemId === targetId) return
  let arr: Array<{ id: string }> | undefined
  switch (activeTab.value) {
    case 'column':
      arr = listConfig.columns
      break
    case 'filter':
      arr = listConfig.filters
      break
    case 'operation':
      arr = listConfig.operations
      break
    case 'toolbar':
      arr = listConfig.toolbar
      break
  }
  if (!arr) return
  const fromIdx = arr.findIndex((x) => x.id === dragItemId)
  const toIdx = arr.findIndex((x) => x.id === targetId)
  if (fromIdx < 0 || toIdx < 0) return
  const [moved] = arr.splice(fromIdx, 1)
  arr.splice(toIdx, 0, moved)
  dragItemId = ''
}

// ===================== 选项编辑（select 筛选） =====================

function addFilterOption(f: ListFilterConfig) {
  if (!f.options) f.options = []
  f.options.push({ label: '新选项', value: `option_${Date.now()}` })
}

function removeFilterOption(f: ListFilterConfig, idx: number) {
  if (f.options) f.options.splice(idx, 1)
}

// ===================== 元信息 & 配置序列化 =====================

function syncListConfigToStr() {
  metaForm.listConfig = JSON.stringify(listConfig, null, 2)
}

function parseListConfigFromStr() {
  try {
    if (!metaForm.listConfig) {
      listConfig.columns = []
      listConfig.filters = []
      listConfig.operations = []
      listConfig.toolbar = []
      return
    }
    const parsed = JSON.parse(metaForm.listConfig) as ListConfig
    listConfig.title = parsed.title ?? ''
    listConfig.description = parsed.description ?? ''
    listConfig.searchApi = parsed.searchApi ?? ''
    listConfig.method = parsed.method ?? 'GET'
    listConfig.pageSize = parsed.pageSize ?? 20
    listConfig.pageSizes = parsed.pageSizes ?? [10, 20, 50, 100]
    listConfig.layout = parsed.layout ?? ListLayout.TABLE
    listConfig.stripe = parsed.stripe ?? true
    listConfig.border = parsed.border ?? true
    listConfig.showSelection = parsed.showSelection ?? false
    listConfig.showIndex = parsed.showIndex ?? true
    listConfig.showPagination = parsed.showPagination ?? true
    listConfig.columns = parsed.columns || []
    listConfig.filters = parsed.filters || []
    listConfig.operations = parsed.operations || []
    listConfig.toolbar = parsed.toolbar || []
    listConfig.export = parsed.export || { enabled: false }
    // 重置计数器
    colSeq = 0
    filterSeq = 0
    opSeq = 0
    const bump = (prefix: string, seqRef: { v: number }) => (item: { id: string }) => {
      const m = new RegExp(`${prefix}_(\\d+)`).exec(item.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > seqRef.v) seqRef.v = n
      }
    }
    listConfig.columns.forEach(bump('col', { get v() { return colSeq }, set v(n) { colSeq = n } }))
    ;(listConfig.filters || []).forEach(bump('filter', { get v() { return filterSeq }, set v(n) { filterSeq = n } }))
    ;(listConfig.operations || []).forEach(bump('op', { get v() { return opSeq }, set v(n) { opSeq = n } }))
    ;(listConfig.toolbar || []).forEach(bump('op', { get v() { return opSeq }, set v(n) { opSeq = n } }))
  } catch (e) {
    ElMessage.error('列表配置 JSON 解析失败：' + (e as Error).message)
  }
}

// ===================== 撤销/重做 =====================

/**
 * 撤销/重做历史栈：对整个 listConfig 做 JSON 快照。
 *
 * <p>采用 watch 深度监听 listConfig 自动推历史（400ms 防抖合并连续输入，
 * 避免 50 步栈被逐字符吞掉）；undo/redo 时反向同步快照回 reactive，
 * 保持 listConfig 引用不变以兼容现有 UI 双向绑定。</p>
 */
const history = useUndoRedo<ListConfig>(JSON.parse(JSON.stringify(listConfig)))
const { present: historyPresent, canUndo, canRedo } = history

/** 抑制标志：undo/redo 同步快照回 listConfig 时关闭 watch 推历史，避免循环 */
let suppressHistory = false
/** 防抖计时器：连续输入合并为一次历史入栈 */
let historyDebounce: ReturnType<typeof setTimeout> | null = null
const HISTORY_DEBOUNCE_MS = 400

/** 立即提交待入栈的变更（undo/redo 前调用，避免丢失未入栈编辑） */
function commitPendingHistory() {
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
    history.set(JSON.parse(JSON.stringify(listConfig)))
  }
}

// 深度监听 listConfig，自动推历史（flush: sync 便于精确抑制）
watch(
  listConfig,
  () => {
    if (suppressHistory) return
    if (historyDebounce) clearTimeout(historyDebounce)
    historyDebounce = setTimeout(() => {
      historyDebounce = null
      history.set(JSON.parse(JSON.stringify(listConfig)))
    }, HISTORY_DEBOUNCE_MS)
  },
  { deep: true, flush: 'sync' }
)

/** 将历史当前快照同步回 reactive listConfig（保持引用不变，UI 自动更新） */
function applyHistoryToListConfig() {
  const snap = historyPresent.value
  suppressHistory = true
  try {
    const target = listConfig as unknown as Record<string, unknown>
    const src = snap as unknown as Record<string, unknown>
    // 删除快照中没有的 key
    for (const key of Object.keys(target)) {
      if (!(key in src)) delete target[key]
    }
    // 写入快照中的所有 key（深拷贝避免共享引用）
    for (const key of Object.keys(src)) {
      target[key] = JSON.parse(JSON.stringify(src[key]))
    }
    // 重算各类型 ID 计数器，避免 undo/redo 后新增项 ID 冲突
    colSeq = 0
    filterSeq = 0
    opSeq = 0
    const bump = (prefix: string, getSeq: () => number, setSeq: (n: number) => void) => (item: { id: string }) => {
      const m = new RegExp(`${prefix}_(\\d+)`).exec(item.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > getSeq()) setSeq(n)
      }
    }
    listConfig.columns.forEach(bump('col', () => colSeq, (n) => (colSeq = n)))
    ;(listConfig.filters || []).forEach(bump('filter', () => filterSeq, (n) => (filterSeq = n)))
    ;(listConfig.operations || []).forEach(bump('op', () => opSeq, (n) => (opSeq = n)))
    ;(listConfig.toolbar || []).forEach(bump('op', () => opSeq, (n) => (opSeq = n)))
  } finally {
    nextTick(() => {
      suppressHistory = false
    })
  }
}

/** 撤销 */
function undo() {
  commitPendingHistory()
  if (!canUndo.value) return
  history.undo()
  applyHistoryToListConfig()
}

/** 重做 */
function redo() {
  commitPendingHistory()
  if (!canRedo.value) return
  history.redo()
  applyHistoryToListConfig()
}

/** 键盘快捷键：Ctrl/Cmd+Z 撤销，Ctrl+Y 或 Ctrl/Cmd+Shift+Z 重做 */
function onUndoRedoKeydown(event: KeyboardEvent) {
  const isMac =
    typeof navigator !== 'undefined' &&
    navigator.platform.toUpperCase().indexOf('MAC') >= 0
  const ctrlOrCmd = isMac ? event.metaKey : event.ctrlKey
  if (!ctrlOrCmd) return
  const key = event.key.toLowerCase()
  if (key === 'z' && !event.shiftKey) {
    event.preventDefault()
    undo()
  } else if ((key === 'z' && event.shiftKey) || key === 'y') {
    event.preventDefault()
    redo()
  }
}

// ===================== 加载已有列表（编辑模式） =====================

async function loadList(id: number) {
  loading.value = true
  try {
    const data = await getList(id)
    Object.assign(metaForm, data)
    parseListConfigFromStr()
    // 编辑模式加载完成后重置历史栈，使加载的配置成为初始状态（不可 undo 回空状态）
    if (historyDebounce) {
      clearTimeout(historyDebounce)
      historyDebounce = null
    }
    history.reset(JSON.parse(JSON.stringify(listConfig)))
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

// ===================== 保存 / 发布 / 归档 =====================

async function handleSave() {
  if (!metaFormRef.value) return
  await metaFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (listConfig.columns.length === 0) {
      ElMessage.warning('请至少添加一列')
      return
    }
    syncListConfigToStr()
    loading.value = true
    try {
      if (metaForm.id) {
        await updateList(metaForm.id, metaForm)
        ElMessage.success('保存成功')
      } else {
        const created = await createList(metaForm)
        metaForm.id = created.id
        metaForm.status = created.status
        ElMessage.success('创建成功')
      }
    } catch {
      /* handled by interceptor */
    } finally {
      loading.value = false
    }
  })
}

async function handlePublish() {
  if (!metaForm.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  syncListConfigToStr()
  loading.value = true
  try {
    await updateList(metaForm.id, metaForm)
    await publishList(metaForm.id)
    metaForm.status = 'PUBLISHED'
    ElMessage.success('发布成功')
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

async function handleArchive() {
  if (!metaForm.id) return
  try {
    await ElMessageBox.confirm('确认归档此列表？归档后不可再使用', '确认', { type: 'warning' })
    await archiveList(metaForm.id)
    metaForm.status = 'ARCHIVED'
    ElMessage.success('归档成功')
  } catch {
    /* cancelled or error */
  }
}

// ===================== 导入 / 导出 =====================

async function handleExport() {
  if (!metaForm.code) {
    ElMessage.warning('请先填写列表编码')
    return
  }
  syncListConfigToStr()
  if (metaForm.id) {
    try {
      await exportList(metaForm.code)
      ElMessage.success('导出成功')
    } catch {
      /* handled by interceptor */
    }
  } else {
    // 本地导出
    const blob = new Blob([JSON.stringify(metaForm, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `list-${metaForm.code}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    setTimeout(() => URL.revokeObjectURL(url), 0)
    ElMessage.success('本地导出成功')
  }
}

async function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,application/json'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    const text = await file.text()
    try {
      const imported = await importList(text)
      ElMessage.success(`导入成功，编码：${imported.code}`)
      Object.assign(metaForm, imported)
      parseListConfigFromStr()
    } catch {
      // 后端导入失败时本地解析
      try {
        const parsed = JSON.parse(text) as Partial<LowCodeListConfig> & { columns?: unknown }
        if (parsed.listConfig && typeof parsed.listConfig === 'string') {
          Object.assign(metaForm, parsed)
          parseListConfigFromStr()
          ElMessage.success('已加载到画布（本地解析，未提交后端）')
        } else if (Array.isArray(parsed.columns)) {
          metaForm.listConfig = text
          parseListConfigFromStr()
          ElMessage.success('已加载到画布')
        } else {
          ElMessage.error('无法识别的 JSON 结构')
        }
      } catch (e) {
        ElMessage.error('JSON 解析失败：' + (e as Error).message)
      }
    }
  }
  input.click()
}

// ===================== 预览 / 重置 =====================

function handlePreview() {
  syncListConfigToStr()
  previewVisible.value = true
}

function handleReset() {
  ElMessageBox.confirm('确认清空画布所有配置？此操作不可恢复', '确认', { type: 'warning' })
    .then(() => {
      listConfig.columns = []
      listConfig.filters = []
      listConfig.operations = []
      listConfig.toolbar = []
      selectedId.value = ''
      colSeq = 0
      filterSeq = 0
      opSeq = 0
      ElMessage.success('已重置画布')
    })
    .catch(() => {})
}

function goToList() {
  router.push('/lowcode/list-list')
}

// ===================== 当前 Tab 的字段列表 getter =====================

const currentItems = computed<Array<{ id: string; type?: string; label: string; prop?: string }>>(() => {
  switch (activeTab.value) {
    case 'column':
      return listConfig.columns.map((c) => ({ id: c.id, type: c.type, label: c.label, prop: c.prop }))
    case 'filter':
      return (listConfig.filters || []).map((f) => ({ id: f.id, type: f.type, label: f.label, prop: f.prop }))
    case 'operation':
      return (listConfig.operations || []).map((o) => ({ id: o.id, type: o.action, label: o.label }))
    case 'toolbar':
      return (listConfig.toolbar || []).map((o) => ({ id: o.id, type: o.action, label: o.label }))
    default:
      return []
  }
})

// ===================== 初始化 =====================

const editId = route.query.id ? Number(route.query.id) : 0
if (editId > 0) {
  loadList(editId)
} else {
  listConfig.title = '未命名列表'
  // 重置历史栈，使初始标题作为干净的起点（不可 undo 回空标题）
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
  }
  history.reset(JSON.parse(JSON.stringify(listConfig)))
}

// 加载注册中心业务组件，供"自定义列"绑定 componentName
onMounted(async () => {
  window.addEventListener('keydown', onUndoRedoKeydown)
  try {
    await initBuiltinComponents()
    registryComponents.value = LowCodeComponentRegistry.list()
  } catch {
    // 加载失败仅静默降级
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onUndoRedoKeydown)
  if (historyDebounce) clearTimeout(historyDebounce)
})
</script>

<template>
  <div class="list-designer">
    <!-- ============ 顶部操作栏 ============ -->
    <el-card shadow="never" class="toolbar-card" :body-style="{ padding: '12px 16px' }">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" :icon="'Document'" :loading="loading" @click="handleSave">保存草稿</el-button>
          <el-button type="success" :icon="'Promotion'" @click="handlePublish">发布</el-button>
          <el-button :icon="'Download'" @click="handleExport">导出</el-button>
          <el-button :icon="'Upload'" @click="handleImport">导入</el-button>
          <el-button :icon="'View'" @click="handlePreview">预览</el-button>
          <el-button :icon="'RefreshLeft'" :disabled="!canUndo" @click="undo">撤销</el-button>
          <el-button :icon="'RefreshRight'" :disabled="!canRedo" @click="redo">重做</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button v-if="metaForm.status === 'PUBLISHED'" :icon="'FolderOpened'" @click="handleArchive">归档</el-button>
        </div>
        <div class="toolbar-right">
          <el-tag :type="metaForm.status === 'PUBLISHED' ? 'success' : metaForm.status === 'ARCHIVED' ? 'info' : 'warning'">
            {{ metaForm.status || 'DRAFT' }}
          </el-tag>
          <el-button link type="primary" @click="goToList">返回列表</el-button>
        </div>
      </div>
    </el-card>

    <!-- ============ 元信息编辑区 ============ -->
    <el-card shadow="never" class="meta-card" :body-style="{ padding: '12px 16px' }">
      <el-form ref="metaFormRef" :model="metaForm" :rules="metaRules" inline label-width="90px">
        <el-form-item label="列表编码" prop="code">
          <el-input v-model="metaForm.code" placeholder="如：tpl_project_list" :disabled="!!metaForm.id" style="width: 220px" />
        </el-form-item>
        <el-form-item label="列表名称" prop="name">
          <el-input v-model="metaForm.name" placeholder="请输入列表名称" style="width: 220px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="metaForm.bizType" placeholder="如：PROJECT" style="width: 160px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="metaForm.description" placeholder="列表描述" style="width: 320px" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ============ 主体三栏 ============ -->
    <div class="designer-body">
      <!-- 左侧：组件库 -->
      <el-card shadow="never" class="panel panel-left" :body-style="{ padding: '8px' }">
        <template #header>
          <span class="panel-title">组件库</span>
        </template>
        <div v-for="group in componentGroups" :key="group.title" class="comp-group">
          <div class="comp-group-title">{{ group.title }}</div>
          <div class="comp-items">
            <div
              v-for="comp in group.items"
              :key="comp.type + comp.category"
              class="comp-item"
              draggable="true"
              @dragstart="onCompDragStart($event, comp)"
              @click="addComponent(comp)"
            >
              <el-icon><component :is="comp.icon" /></el-icon>
              <span>{{ comp.label }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 中间：画布 -->
      <el-card shadow="never" class="panel panel-center" :body-style="{ padding: '12px' }">
        <template #header>
          <div class="canvas-header">
            <el-tabs v-model="activeTab" type="border-card" class="canvas-tabs">
              <el-tab-pane label="列配置" name="column" />
              <el-tab-pane label="筛选配置" name="filter" />
              <el-tab-pane label="操作配置" name="operation" />
              <el-tab-pane label="工具栏配置" name="toolbar" />
            </el-tabs>
            <el-form inline size="small" class="canvas-config">
              <el-form-item label="查询接口">
                <el-input v-model="listConfig.searchApi" placeholder="/api/project" style="width: 200px" />
              </el-form-item>
              <el-form-item label="方法">
                <el-select v-model="listConfig.method" style="width: 90px">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                </el-select>
              </el-form-item>
              <el-form-item label="每页条数">
                <el-input-number v-model="listConfig.pageSize" :min="1" :max="500" style="width: 100px" />
              </el-form-item>
            </el-form>
            <div class="canvas-switches">
              <el-checkbox v-model="listConfig.showSelection">多选</el-checkbox>
              <el-checkbox v-model="listConfig.showIndex">序号</el-checkbox>
              <el-checkbox v-model="listConfig.showPagination">分页</el-checkbox>
              <el-checkbox v-model="listConfig.stripe">斑马纹</el-checkbox>
              <el-checkbox v-model="listConfig.border">边框</el-checkbox>
            </div>
          </div>
        </template>

        <!-- 拖拽放置区 -->
        <div
          class="canvas-dropzone"
          :class="{ empty: currentItems.length === 0 }"
          @dragover="onCanvasDragOver"
          @drop="onCanvasDrop"
        >
          <div v-if="currentItems.length === 0" class="empty-tip">
            <el-icon :size="40"><Plus /></el-icon>
            <p>从左侧拖拽{{ activeTab === 'column' ? '列' : activeTab === 'filter' ? '筛选' : '操作' }}组件到此处</p>
          </div>

          <div v-else class="item-list">
            <div
              v-for="(item, idx) in currentItems"
              :key="item.id"
              class="item-card"
              :class="{ active: selectedId === item.id }"
              draggable="true"
              @dragstart="onItemDragStart($event, item.id)"
              @dragover="onCanvasDragOver"
              @drop="onItemDrop($event, item.id)"
              @click="selectItem(item.id)"
            >
              <div class="item-card-header">
                <el-tag size="small" type="info">{{ item.type }}</el-tag>
                <span class="item-label">{{ item.label }}</span>
                <span v-if="item.prop" class="item-prop">{{ item.prop }}</span>
                <div class="item-actions">
                  <el-button-group size="small">
                    <el-button :icon="'Top'" :disabled="idx === 0" @click.stop="moveItem(item.id, -1)" />
                    <el-button :icon="'Bottom'" :disabled="idx === currentItems.length - 1" @click.stop="moveItem(item.id, 1)" />
                    <el-button :icon="'CopyDocument'" @click.stop="duplicateItem(item.id)" />
                    <el-button :icon="'Delete'" type="danger" @click.stop="removeItem(item.id)" />
                  </el-button-group>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 右侧：属性面板 -->
      <el-card shadow="never" class="panel panel-right" :body-style="{ padding: '12px' }">
        <template #header>
          <span class="panel-title">属性面板</span>
        </template>

        <div v-if="!selectedItem" class="empty-prop">
          <el-empty description="请选择一个配置项" :image-size="80" />
        </div>

        <!-- 列属性 -->
        <el-form v-else-if="activeTab === 'column' && selectedColumn" :model="selectedColumn" label-width="90px" size="small">
          <el-divider content-position="left">基础属性</el-divider>
          <el-form-item label="列类型">
            <el-tag v-if="isSelectedRegistryColumn" type="warning" size="small">
              业务组件：{{ selectedColumnComponentMeta?.displayName || selectedColumn.componentName }}
            </el-tag>
            <el-select v-else v-model="selectedColumn.type" style="width: 100%">
              <el-option-group label="列组件">
                <el-option v-for="c in componentGroups[0].items" :key="c.type" :label="c.label" :value="c.type" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <!-- 业务组件：选择列类型为"自定义"后，绑定注册中心组件名 -->
          <el-form-item v-if="selectedColumn.type === ColumnType.CUSTOM" label="业务组件">
            <el-select
              v-model="selectedColumn.componentName"
              placeholder="选择业务组件"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="meta in registryComponents"
                :key="meta.name"
                :label="`${meta.displayName} (${meta.name})`"
                :value="meta.name"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="列标题">
            <el-input v-model="selectedColumn.label" />
          </el-form-item>
          <el-form-item label="字段名">
            <el-input v-model="selectedColumn.prop" />
          </el-form-item>
          <el-form-item label="列宽(px)">
            <el-input-number v-model="selectedColumn.width" :min="50" :max="500" style="width: 100%" />
          </el-form-item>
          <el-form-item label="最小列宽">
            <el-input-number v-model="selectedColumn.minWidth" :min="50" :max="500" style="width: 100%" />
          </el-form-item>
          <el-form-item label="对齐">
            <el-select v-model="selectedColumn.align" style="width: 100%">
              <el-option label="左" value="left" />
              <el-option label="中" value="center" />
              <el-option label="右" value="right" />
            </el-select>
          </el-form-item>
          <el-form-item label="固定列">
            <el-select v-model="selectedColumn.fixed" style="width: 100%">
              <el-option label="不固定" :value="false" />
              <el-option label="左" value="left" />
              <el-option label="右" value="right" />
            </el-select>
          </el-form-item>
          <el-form-item label="可排序">
            <el-switch v-model="selectedColumn.sortable" />
          </el-form-item>
          <el-form-item label="隐藏列">
            <el-switch v-model="selectedColumn.hidden" />
          </el-form-item>

          <!-- 类型特定属性 -->
          <el-divider content-position="left">类型属性</el-divider>
          <template v-if="selectedColumn.type === ColumnType.DICT">
            <el-form-item label="字典编码">
              <el-input v-model="selectedColumn.dictCode" placeholder="如：project_status" />
            </el-form-item>
          </template>
          <template v-if="selectedColumn.type === ColumnType.IMAGE">
            <el-form-item label="图片宽">
              <el-input-number v-model="selectedColumn.imageWidth" :min="20" :max="200" style="width: 100%" />
            </el-form-item>
            <el-form-item label="图片高">
              <el-input-number v-model="selectedColumn.imageHeight" :min="20" :max="200" style="width: 100%" />
            </el-form-item>
          </template>
          <template v-if="selectedColumn.type === ColumnType.LINK">
            <el-form-item label="跳转地址">
              <el-input v-model="selectedColumn.linkUrl" placeholder="/project/detail/{id}" />
            </el-form-item>
          </template>
          <template v-if="selectedColumn.type === ColumnType.TAG">
            <el-form-item label="标签类型">
              <el-select v-model="selectedColumn.tagType" style="width: 100%">
                <el-option label="主要" value="primary" />
                <el-option label="成功" value="success" />
                <el-option label="警告" value="warning" />
                <el-option label="危险" value="danger" />
                <el-option label="信息" value="info" />
              </el-select>
            </el-form-item>
          </template>
          <el-form-item label="格式化器">
            <el-input
              v-model="selectedColumn.formatter"
              placeholder="dateFormat:YYYY-MM-DD 或 currency:¥"
            />
          </el-form-item>
        </el-form>

        <!-- 筛选属性 -->
        <el-form v-else-if="activeTab === 'filter' && selectedFilter" :model="selectedFilter" label-width="90px" size="small">
          <el-divider content-position="left">基础属性</el-divider>
          <el-form-item label="筛选类型">
            <el-select v-model="selectedFilter.type" style="width: 100%">
              <el-option-group label="筛选组件">
                <el-option v-for="c in componentGroups[1].items" :key="c.type" :label="c.label" :value="c.type" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="selectedFilter.label" />
          </el-form-item>
          <el-form-item label="字段名">
            <el-input v-model="selectedFilter.prop" />
          </el-form-item>
          <el-form-item label="占位符">
            <el-input v-model="selectedFilter.placeholder" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="selectedFilter.defaultValue" placeholder="留空表示无默认值" />
          </el-form-item>
          <el-form-item label="响应式栅格">
            <el-switch v-model="isFilterResponsive" />
            <span class="form-tip">开启后按 xs/sm/md/lg/xl 五档断点配置</span>
          </el-form-item>
          <el-form-item v-if="!isFilterResponsive" label="栅格宽度">
            <el-slider v-model="filterSpan" :min="1" :max="24" show-input style="width: 100%" />
          </el-form-item>
          <el-collapse v-else v-model="filterResponsiveCollapse" class="resp-collapse">
            <el-collapse-item title="响应式断点（1-24，留空继承更小断点）" name="resp">
              <el-form-item v-for="bp in breakpointOrder" :key="bp" :label="bp">
                <div class="bp-row">
                  <el-input-number
                    :model-value="getFilterBreakpoint(bp)"
                    :min="1"
                    :max="24"
                    controls-position="right"
                    placeholder="留空"
                    style="flex: 1"
                    @update:model-value="(v: number | undefined) => setFilterBreakpoint(bp, v ?? undefined)"
                  />
                  <el-button
                    v-if="getFilterBreakpoint(bp) !== undefined"
                    link
                    type="primary"
                    :icon="'Close'"
                    title="清除（留空，继承更小断点）"
                    @click="setFilterBreakpoint(bp, undefined)"
                  />
                </div>
              </el-form-item>
            </el-collapse-item>
          </el-collapse>
          <el-form-item label="可清空">
            <el-switch v-model="selectedFilter.clearable" />
          </el-form-item>

          <!-- select 选项 -->
          <template v-if="selectedFilter.type === FilterType.SELECT">
            <el-divider content-position="left">选项配置</el-divider>
            <el-form-item label="字典编码">
              <el-input v-model="selectedFilter.dictCode" placeholder="留空时使用下方选项" />
            </el-form-item>
            <el-form-item label="多选">
              <el-switch v-model="selectedFilter.multiple" />
            </el-form-item>
            <div
              v-for="(opt, idx) in selectedFilter.options"
              :key="idx"
              class="option-row"
            >
              <el-input v-model="opt.label" placeholder="标签" size="small" style="width: 40%" />
              <el-input v-model="opt.value" placeholder="值" size="small" style="width: 40%; margin-left: 4px" />
              <el-button :icon="'Delete'" type="danger" size="small" style="margin-left: 4px" @click="removeFilterOption(selectedFilter, idx)" />
            </div>
            <el-button :icon="'Plus'" size="small" @click="addFilterOption(selectedFilter)">添加选项</el-button>
          </template>
        </el-form>

        <!-- 操作属性（行操作 / 工具栏） -->
        <el-form
          v-else-if="(activeTab === 'operation' && selectedOperation) || (activeTab === 'toolbar' && selectedToolbar)"
          :model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!"
          label-width="90px"
          size="small"
        >
          <el-divider content-position="left">基础属性</el-divider>
          <el-form-item label="按钮文本">
            <el-input v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.label" />
          </el-form-item>
          <el-form-item label="按钮类型">
            <el-select v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.type" style="width: 100%">
              <el-option label="主要" value="primary" />
              <el-option label="成功" value="success" />
              <el-option label="警告" value="warning" />
              <el-option label="危险" value="danger" />
              <el-option label="信息" value="info" />
              <el-option label="文本" value="text" />
            </el-select>
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.icon" placeholder="Element Plus 图标名" />
          </el-form-item>
          <el-form-item label="动作类型">
            <el-select v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.action" style="width: 100%">
              <el-option label="新建" value="create" />
              <el-option label="编辑" value="edit" />
              <el-option label="查看" value="view" />
              <el-option label="删除" value="delete" />
              <el-option label="自定义" value="custom" />
            </el-select>
          </el-form-item>
          <el-form-item label="跳转地址">
            <el-input v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.url" placeholder="/project/edit/{id}" />
          </el-form-item>
          <el-form-item label="调用接口">
            <el-input v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.api" placeholder="/api/project/{id}" />
          </el-form-item>
          <el-form-item label="接口方法">
            <el-select v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.method" style="width: 100%">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
          <el-form-item label="确认提示">
            <el-input v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.confirm" placeholder="如：确认删除？" />
          </el-form-item>
          <el-form-item label="权限标识">
            <el-input v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.permission" placeholder="如：project:update" />
          </el-form-item>
          <el-form-item label="显示条件" v-if="activeTab === 'operation'">
            <el-input
              v-model="(activeTab === 'operation' ? selectedOperation : selectedToolbar)!.visible"
              placeholder="如：row.status === 'DRAFT'"
            />
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- ============ 预览弹窗 ============ -->
    <el-dialog
      v-model="previewVisible"
      title="列表预览"
      width="90%"
      top="3vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <LowCodeListRenderer
        :config="listConfig"
        :auto-fetch="false"
        :data="[
          { id: 1, projectCode: 'P2025001', projectName: '示例项目', status: 'IN_PROGRESS', amount: 100000, progress: 0.65, createTime: '2025-01-01 10:00:00' }
        ]"
        :dict-map="{}"
      />
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.list-designer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 110px);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.designer-body {
  display: grid;
  grid-template-columns: 220px 1fr 320px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}

.panel-title {
  font-weight: 600;
  font-size: 14px;
}

/* 左侧组件库 */
.comp-group {
  margin-bottom: 8px;
}

.comp-group-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 6px 4px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 6px;
}

.comp-items {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.comp-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: grab;
  font-size: 12px;
  transition: all 0.2s;
  background: var(--el-bg-color);
}

.comp-item:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.comp-item:active {
  cursor: grabbing;
}

/* 中间画布 */
.canvas-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.canvas-tabs {
  width: 100%;
}

.canvas-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.canvas-config {
  margin: 0;
}

.canvas-config :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 8px;
}

.canvas-switches {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.canvas-dropzone {
  min-height: 400px;
  border: 2px dashed var(--el-border-color);
  border-radius: 6px;
  padding: 8px;
  background: var(--el-fill-color-blank);
}

.canvas-dropzone.empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
}

.empty-tip p {
  margin: 8px 0 0;
  font-size: 13px;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item-card {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: all 0.2s;
}

.item-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.item-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.item-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.item-label {
  font-weight: 600;
}

.item-prop {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-family: monospace;
}

.item-actions {
  margin-left: auto;
}

/* 右侧属性面板 */
.empty-prop {
  padding: 24px 0;
}

.option-row {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.resp-collapse {
  margin: 4px 0 12px;
}

.resp-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}

/* 断点编辑行：input + 清除按钮横排 */
.bp-row {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

/* 响应式：小屏堆叠 */
@media (max-width: 1200px) {
  .designer-body {
    grid-template-columns: 180px 1fr 280px;
  }
}

@media (max-width: 992px) {
  .designer-body {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
  }
}
</style>
