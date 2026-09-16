<script setup lang="ts">
/**
 * 低代码表单设计器。
 *
 * <p>三栏布局：</p>
 * <ul>
 *   <li>左侧：组件库面板（按分类列出可拖拽组件）</li>
 *   <li>中间：画布（拖拽放置区 + 字段列表 + 实时预览）</li>
 *   <li>右侧：属性面板（选中字段的属性配置）</li>
 * </ul>
 *
 * <p>顶部操作栏：保存草稿 / 发布 / 导入 / 导出 / 预览 / 重置，以及表单元信息编辑。</p>
 *
 * <p>使用原生 HTML5 拖拽 API（draggable + dragstart/dragover/drop）实现，
 * 避免引入额外依赖；字段排序通过上移/下移按钮 + 拖拽两种方式。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules
} from 'element-plus'
import {
  archiveForm,
  createForm,
  deleteForm,
  exportForm,
  FieldType,
  getForm,
  importForm,
  LayoutType,
  publishForm,
  updateForm,
  type FormConfig,
  type FormFieldConfig,
  type LowCodeFormConfig,
  type LowCodeFormQuery,
  type ResponsiveSpan
} from '@/api/lowcode'
import LowCodeFormRenderer from '@/components/LowCodeFormRenderer/index.vue'
import LowCodePropertyPanel from '@/components/LowCodePropertyPanel/index.vue'
import LowCodeComponentRegistry, {
  initBuiltinComponents
} from '@/components/LowCodeComponentRegistry'
import type { ComponentMeta } from '@/components/LowCodeComponentRegistry/types'
import { useUndoRedo } from '@/composables/useUndoRedo'
import {
  BREAKPOINT_ORDER,
  BREAKPOINT_PREVIEW_WIDTH,
  BREAKPOINT_LABEL
} from '@/styles/breakpoints'
import { useCollaboration } from '@/composables/useCollaboration'
import OnlineUsersIndicator from '@/components/OnlineUsersIndicator/index.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ===================== 组件库定义 =====================

interface ComponentDef {
  type: string
  label: string
  icon: string
  defaultProps?: Record<string, unknown>
  /** registry 组件标记：拖入后 field.type 设为 custom，并写入 componentName */
  isRegistry?: boolean
  /** 对应 LowCodeComponentRegistry meta.name */
  componentName?: string
}

/** 基础组件库分组（17 种内置字段类型，保留不动） */
const baseComponentGroups: Array<{ title: string; items: ComponentDef[] }> = [
  {
    title: '基础组件',
    items: [
      { type: FieldType.INPUT, label: '单行文本', icon: 'EditPen', defaultProps: { maxlength: 100 } },
      { type: FieldType.TEXTAREA, label: '多行文本', icon: 'Document', defaultProps: { rows: 3, maxlength: 500 } },
      { type: FieldType.NUMBER, label: '数字', icon: 'Histogram', defaultProps: { min: 0, step: 1 } },
      { type: FieldType.PASSWORD, label: '密码', icon: 'Lock', defaultProps: { showPassword: true } }
    ]
  },
  {
    title: '选择组件',
    items: [
      { type: FieldType.SELECT, label: '下拉选择', icon: 'ArrowDown', defaultProps: { options: [], multiple: false } },
      { type: FieldType.RADIO, label: '单选', icon: 'CircleCheck', defaultProps: { options: [] } },
      { type: FieldType.CHECKBOX, label: '多选', icon: 'Select', defaultProps: { options: [] } },
      { type: FieldType.SWITCH, label: '开关', icon: 'Open', defaultProps: { activeText: '', inactiveText: '' } },
      { type: FieldType.SLIDER, label: '滑块', icon: 'Minus', defaultProps: { min: 0, max: 100 } },
      { type: FieldType.RATE, label: '评分', icon: 'Star', defaultProps: { max: 5, allowHalf: false } },
      { type: FieldType.CASCADER, label: '级联选择', icon: 'Share', defaultProps: { options: [] } }
    ]
  },
  {
    title: '日期组件',
    items: [
      { type: FieldType.DATE, label: '日期', icon: 'Calendar', defaultProps: { format: 'YYYY-MM-DD', valueFormat: 'YYYY-MM-DD' } },
      { type: FieldType.DATETIME, label: '日期时间', icon: 'Clock', defaultProps: { format: 'YYYY-MM-DD HH:mm:ss', valueFormat: 'YYYY-MM-DD HH:mm:ss' } },
      { type: FieldType.DATERANGE, label: '日期范围', icon: 'Calendar', defaultProps: { format: 'YYYY-MM-DD', valueFormat: 'YYYY-MM-DD' } }
    ]
  },
  {
    title: '上传组件',
    items: [
      { type: FieldType.UPLOAD, label: '文件上传', icon: 'Upload', defaultProps: { action: '/api/file/upload', limit: 5, accept: '', multiple: true } }
    ]
  },
  {
    title: '布局组件',
    items: [
      { type: FieldType.DIVIDER, label: '分隔线', icon: 'Minus', defaultProps: { contentPosition: 'center' } },
      { type: FieldType.TITLE, label: '标题', icon: 'Document', defaultProps: {} }
    ]
  }
]

/** 注册中心加载的业务组件 meta 列表（按 category 分组后合并到组件库） */
const registryComponents = ref<ComponentMeta[]>([])

/**
 * 组件库分组（computed）：基础组件 + 注册中心业务组件。
 *
 * <p>基础 5 组保留不动；registry 组件按 category 分组，在基础组之后追加。
 * registry 组件统一标记 isRegistry=true、type='custom'，拖入画布时写入 componentName。</p>
 */
const componentGroups = computed((): Array<{ title: string; items: ComponentDef[] }> => {
  const groups = baseComponentGroups.map((g) => ({ ...g, items: [...g.items] }))
  if (registryComponents.value.length === 0) return groups
  // 按 category 分组
  const byCategory = new Map<string, ComponentMeta[]>()
  for (const meta of registryComponents.value) {
    const cat = meta.category || '其他'
    if (!byCategory.has(cat)) byCategory.set(cat, [])
    byCategory.get(cat)!.push(meta)
  }
  for (const [cat, metas] of byCategory) {
    groups.push({
      title: `业务组件·${cat}`,
      items: metas.map((meta) => ({
        type: FieldType.CUSTOM,
        label: meta.displayName,
        icon: 'Box',
        isRegistry: true,
        componentName: meta.name
      }))
    })
  }
  return groups
})

// ===================== 表单元信息 + 配置状态 =====================

/** 表单元信息（对应 LowCodeFormConfig 的非 formConfig 字段） */
const metaForm = reactive<LowCodeFormConfig>({
  code: '',
  name: '',
  description: '',
  formConfig: '',
  status: 'DRAFT',
  bizType: '',
  version: 1
})

/** 设计器内部维护的 FormConfig 对象（解析后） */
const formConfig = reactive<FormConfig>({
  title: '',
  description: '',
  labelWidth: 100,
  labelPosition: 'right',
  size: 'default',
  fields: [],
  layout: { type: LayoutType.GRID, gutter: 16 }
})

/** 当前选中的字段 id */
const selectedFieldId = ref<string>('')
/** 字段计数器，用于生成 field_N */
let fieldSeq = 0

/** 元信息表单 ref */
const metaFormRef = ref<FormInstance>()
/** 元信息校验规则 */
const metaRules: FormRules = {
  code: [{ required: true, message: '请输入表单编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入表单名称', trigger: 'blur' }]
}

/** 加载状态 */
const loading = ref(false)
/** 预览模式 */
const previewMode = ref(false)
/** 预览表单数据 */
const previewData = reactive<Record<string, unknown>>({})

// ===================== 字段操作 =====================

/** 选中字段对象 */
const selectedField = computed(() =>
  formConfig.fields.find((f) => f.id === selectedFieldId.value) || null
)

/** 选中字段是否为 registry 业务组件（type=custom 且携带 componentName） */
const isSelectedRegistry = computed(
  () => !!selectedField.value && selectedField.value.type === FieldType.CUSTOM && !!selectedField.value.componentName
)

/** 选中 registry 组件的 meta（含 propsSchema，供 LowCodePropertyPanel 渲染） */
const selectedComponentMeta = computed<ComponentMeta | null>(() => {
  const field = selectedField.value
  if (!field || !field.componentName) return null
  return LowCodeComponentRegistry.get(field.componentName)?.meta || null
})

// ===================== 响应式栅格断点（xs/sm/md/lg/xl） =====================

/** 响应式断点折叠面板激活项（默认展开） */
const responsiveCollapse = ref<string[]>(['resp'])

type Breakpoint = 'xs' | 'sm' | 'md' | 'lg' | 'xl'

/** 断点枚举数组（自小到大，用于遍历与继承查找）— 引用统一常量 */
const breakpointOrder: Breakpoint[] = BREAKPOINT_ORDER

/** 各断点对应的最小屏幕宽度（px），用于画布响应式预览模拟 — 引用统一常量 */
const breakpointWidth: Record<Breakpoint, number> = BREAKPOINT_PREVIEW_WIDTH

/** 断点显示文案（含屏幕宽度范围）— 引用统一常量 */
const breakpointLabel: Record<Breakpoint, string> = BREAKPOINT_LABEL

/**
 * 当前选中字段是否启用响应式断点（span 为对象）。
 *
 * <p>开启时仅初始化 xs 与 md 两个断点为当前 span，sm/lg/xl 留空，
 * 由 el-col 按断点继承规则回退到更小断点，体现“留空即继承”。</p>
 */
const isResponsive = computed<boolean>({
  get: () => !!selectedField.value && typeof selectedField.value.span === 'object',
  set: (val: boolean) => {
    const field = selectedField.value
    if (!field) return
    if (val) {
      const cur = typeof field.span === 'number' ? field.span : 24
      // 仅显式设置 xs/md，sm/lg/xl 留空继承，演示断点继承机制
      field.span = { xs: cur, md: cur }
    } else {
      const obj = field.span
      // 关闭时取最接近默认的断点值回退为数字 span
      field.span = typeof obj === 'object' && obj ? (obj.md ?? obj.sm ?? obj.xs ?? 24) : 24
    }
  }
})

/** 非响应式模式下的栅格宽度（数字） */
const fieldSpan = computed<number>({
  get: () => (typeof selectedField.value?.span === 'number' ? selectedField.value.span : 24),
  set: (v: number) => {
    if (selectedField.value) selectedField.value.span = v
  }
})

/**
 * 读取指定断点值。
 *
 * <p>未配置时返回 undefined，表示该断点留空、由 el-col 继承更小断点
 * （对应属性面板 el-input-number 显示占位“留空”）。</p>
 */
function getBreakpoint(k: Breakpoint): number | undefined {
  const s = selectedField.value?.span
  return typeof s === 'object' && s ? s[k] : undefined
}

/**
 * 设置指定断点值。
 *
 * <p>传入 undefined/null/NaN 视为“留空”——从对象中删除该断点 key，
 * el-col 渲染时即不输出对应 prop，自动继承更小断点。</p>
 */
function setBreakpoint(k: Breakpoint, v: number | undefined | null): void {
  const field = selectedField.value
  if (!field) return
  const s = field.span
  const obj: ResponsiveSpan = typeof s === 'object' && s ? { ...s } : {}
  if (v === undefined || v === null || Number.isNaN(v)) {
    delete obj[k]
  } else {
    obj[k] = v
  }
  field.span = obj
}

/**
 * 计算字段在指定断点下的有效 span（模拟 el-col 断点继承）。
 *
 * <p>从当前断点向更小断点逐级查找，取第一个已配置的值；均未配置回退 24。
 * 用于画布响应式预览——因 el-col 的 :xs/:sm 等基于视口媒体查询，
 * 无法在受限容器内触发，故手动计算有效 span 以 :span= 形式渲染。</p>
 */
function effectiveSpan(field: FormFieldConfig, bp: Breakpoint): number {
  const s = field.span
  if (s === undefined || typeof s === 'number') return s ?? 24
  const idx = breakpointOrder.indexOf(bp)
  for (let i = idx; i >= 0; i--) {
    const v = s[breakpointOrder[i]]
    if (v !== undefined) return v
  }
  return 24
}

// ===================== 画布响应式预览（模拟不同屏幕宽度栅格布局） =====================

/** 预览宽度档位：auto=跟随画布宽度，其余模拟对应断点 */
const previewWidth = ref<Breakpoint | 'auto'>('auto')

/** 当前预览选中的断点（auto 时为 null） */
const activePreviewBp = computed<Breakpoint | null>(() =>
  previewWidth.value === 'auto' ? null : previewWidth.value
)

/** 预览模拟的屏幕宽度（px） */
const previewWidthPx = computed<number>(() =>
  activePreviewBp.value ? breakpointWidth[activePreviewBp.value] : 0
)

/** 预览断点显示文案 */
const previewLabel = computed<string>(() =>
  activePreviewBp.value ? breakpointLabel[activePreviewBp.value] : ''
)

/**
 * 响应式预览行：每个字段在当前模拟断点下的有效 span。
 *
 * <p>实时反映 formConfig.fields 与各字段 span 配置，切换断点即时重算。</p>
 */
const previewRows = computed(() => {
  const bp = activePreviewBp.value
  if (!bp) return [] as Array<{ field: FormFieldConfig; span: number }>
  return formConfig.fields.map((f) => ({ field: f, span: effectiveSpan(f, bp) }))
})

/**
 * 创建一个新字段对象。
 */
function createField(type: string, label: string, extraProps: Record<string, unknown> = {}): FormFieldConfig {
  fieldSeq++
  const id = `field_${fieldSeq}`
  const prop = `field${fieldSeq}`
  return {
    id,
    type,
    label,
    prop,
    placeholder: `请输入${label}`,
    required: false,
    disabled: false,
    readonly: false,
    hidden: false,
    clearable: true,
    span: 24,
    props: { ...extraProps },
    events: {}
  }
}

/** 添加字段到画布（点击组件库或拖拽放置） */
function addField(comp: ComponentDef) {
  const field = createField(comp.type, comp.label, comp.defaultProps || {})
  // registry 组件：写入 componentName，属性面板据此渲染 schema 驱动表单
  if (comp.isRegistry && comp.componentName) {
    field.componentName = comp.componentName
  }
  formConfig.fields.push(field)
  selectedFieldId.value = field.id
}

/** 删除字段 */
function removeField(id: string) {
  const idx = formConfig.fields.findIndex((f) => f.id === id)
  if (idx >= 0) {
    formConfig.fields.splice(idx, 1)
    if (selectedFieldId.value === id) {
      selectedFieldId.value = ''
    }
  }
}

/** 复制字段 */
function duplicateField(id: string) {
  const src = formConfig.fields.find((f) => f.id === id)
  if (!src) return
  fieldSeq++
  const copy: FormFieldConfig = JSON.parse(JSON.stringify(src))
  copy.id = `field_${fieldSeq}`
  copy.prop = `${src.prop}_copy`
  copy.label = `${src.label}_副本`
  const idx = formConfig.fields.findIndex((f) => f.id === id)
  formConfig.fields.splice(idx + 1, 0, copy)
  selectedFieldId.value = copy.id
}

/** 上移字段 */
function moveUp(id: string) {
  const idx = formConfig.fields.findIndex((f) => f.id === id)
  if (idx > 0) {
    const tmp = formConfig.fields[idx]
    formConfig.fields[idx] = formConfig.fields[idx - 1]
    formConfig.fields[idx - 1] = tmp
  }
}

/** 下移字段 */
function moveDown(id: string) {
  const idx = formConfig.fields.findIndex((f) => f.id === id)
  if (idx >= 0 && idx < formConfig.fields.length - 1) {
    const tmp = formConfig.fields[idx]
    formConfig.fields[idx] = formConfig.fields[idx + 1]
    formConfig.fields[idx + 1] = tmp
  }
}

/** 选中字段 */
function selectField(id: string) {
  selectedFieldId.value = id
}

// ===================== 拖拽（HTML5 native） =====================

/** 当前拖拽的组件类型（来自组件库） */
let dragType = ''
/** 当前拖拽的组件定义（registry 组件 type 同为 'custom'，需用完整对象区分） */
let dragComp: ComponentDef | null = null

function onDragStart(event: DragEvent, comp: ComponentDef) {
  dragType = comp.type
  dragComp = comp
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'copy'
    // registry 组件用 componentName 区分；基础组件用 type
    event.dataTransfer.setData('text/plain', comp.isRegistry && comp.componentName ? `custom::${comp.componentName}` : comp.type)
  }
}

function onCanvasDragOver(event: DragEvent) {
  // 允许放置
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
  event.preventDefault()
}

function onCanvasDrop(event: DragEvent) {
  event.preventDefault()
  const raw = event.dataTransfer?.getData('text/plain') || dragType
  if (!raw) return
  // 优先用 dragComp（拖拽起始即记录的完整定义，最可靠）
  if (dragComp) {
    addField(dragComp)
    dragComp = null
    dragType = ''
    return
  }
  // 回退：按 type 或 custom::componentName 查找
  if (raw.startsWith('custom::')) {
    const cn = raw.slice(8)
    for (const group of componentGroups.value) {
      const comp = group.items.find((c) => c.isRegistry && c.componentName === cn)
      if (comp) {
        addField(comp)
        return
      }
    }
    return
  }
  for (const group of componentGroups.value) {
    const comp = group.items.find((c) => c.type === raw && !c.isRegistry)
    if (comp) {
      addField(comp)
      return
    }
  }
}

// ===================== 类型切换 =====================

/** 类型选项 */
const typeOptions = computed(() => {
  const list: Array<{ value: string; label: string; group: string }> = []
  for (const g of componentGroups.value) {
    for (const c of g.items) {
      list.push({ value: c.type, label: c.label, group: g.title })
    }
  }
  return list
})

/** 切换字段类型：保留公共属性，重置类型特定属性 */
function changeFieldType(field: FormFieldConfig, newType: string) {
  const comp = typeOptions.value.find((t) => t.value === newType)
  if (!comp) return
  // 找到对应的组件默认 props
  let defaultProps: Record<string, unknown> = {}
  for (const g of componentGroups.value) {
    const c = g.items.find((c) => c.type === newType)
    if (c) {
      defaultProps = c.defaultProps || {}
      break
    }
  }
  field.type = newType
  field.props = { ...defaultProps }
}

// ===================== 选项编辑（select/radio/checkbox） =====================

/** 添加选项 */
function addOption(field: FormFieldConfig) {
  if (!field.props) field.props = {}
  if (!Array.isArray(field.props.options)) field.props.options = []
  ;(field.props.options as Array<{ label: string; value: string }>).push({
    label: '新选项',
    value: `option_${Date.now()}`
  })
}

/** 删除选项 */
function removeOption(field: FormFieldConfig, idx: number) {
  if (field.props?.options && Array.isArray(field.props.options)) {
    ;(field.props.options as unknown[]).splice(idx, 1)
  }
}

// ===================== 元信息 & 配置序列化 =====================

/** 将 formConfig 对象序列化为 JSON 字符串（赋值到 metaForm.formConfig） */
function syncFormConfigToStr() {
  metaForm.formConfig = JSON.stringify(formConfig, null, 2)
}

/** 从 JSON 字符串解析到 formConfig 对象 */
function parseFormConfigFromStr() {
  try {
    if (!metaForm.formConfig) {
      formConfig.fields = []
      formConfig.layout = { type: LayoutType.GRID, gutter: 16 }
      return
    }
    const parsed = JSON.parse(metaForm.formConfig) as FormConfig
    formConfig.title = parsed.title ?? ''
    formConfig.description = parsed.description ?? ''
    const parsedLabelWidth =
      typeof parsed.labelWidth === 'string' ? Number.parseInt(parsed.labelWidth, 10) : parsed.labelWidth
    formConfig.labelWidth =
      typeof parsedLabelWidth === 'number' && Number.isFinite(parsedLabelWidth) ? parsedLabelWidth : 100
    formConfig.labelPosition = parsed.labelPosition ?? 'right'
    formConfig.size = parsed.size ?? 'default'
    formConfig.fields = (parsed.fields || []).map((field) => ({
      ...field,
      props: field.props || {},
      events: field.events || {}
    }))
    formConfig.layout = parsed.layout || { type: LayoutType.GRID, gutter: 16 }
    // 重置字段计数器
    fieldSeq = 0
    for (const f of formConfig.fields) {
      const m = /field_(\d+)/.exec(f.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > fieldSeq) fieldSeq = n
      }
    }
  } catch (e) {
    ElMessage.error('表单配置 JSON 解析失败：' + (e as Error).message)
  }
}

// ===================== 撤销/重做 =====================

/**
 * 撤销/重做历史栈：对整个 formConfig 做 JSON 快照。
 *
 * <p>采用 watch 深度监听 formConfig 自动推历史（400ms 防抖合并连续输入，
 * 避免 50 步栈被逐字符吞掉）；undo/redo 时反向同步快照回 reactive，
 * 保持 formConfig 引用不变以兼容现有 UI 双向绑定。</p>
 */
const history = useUndoRedo<FormConfig>(JSON.parse(JSON.stringify(formConfig)))
const { present: historyPresent, canUndo, canRedo } = history

/** 抑制标志：undo/redo 同步快照回 formConfig 时关闭 watch 推历史，避免循环 */
let suppressHistory = false
/** 防抖计时器：连续输入合并为一次历史入栈 */
let historyDebounce: ReturnType<typeof setTimeout> | null = null
const HISTORY_DEBOUNCE_MS = 400

/** 立即提交待入栈的变更（undo/redo 前调用，避免丢失未入栈编辑） */
function commitPendingHistory() {
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
    history.set(JSON.parse(JSON.stringify(formConfig)))
  }
}

// 深度监听 formConfig，自动推历史（flush: sync 便于精确抑制）
watch(
  formConfig,
  () => {
    if (suppressHistory) return
    if (historyDebounce) clearTimeout(historyDebounce)
    historyDebounce = setTimeout(() => {
      historyDebounce = null
      history.set(JSON.parse(JSON.stringify(formConfig)))
    }, HISTORY_DEBOUNCE_MS)
  },
  { deep: true, flush: 'sync' }
)

/** 将历史当前快照同步回 reactive formConfig（保持引用不变，UI 自动更新） */
function applyHistoryToFormConfig() {
  const snap = historyPresent.value
  suppressHistory = true
  try {
    const target = formConfig as unknown as Record<string, unknown>
    const src = snap as unknown as Record<string, unknown>
    // 删除快照中没有的 key
    for (const key of Object.keys(target)) {
      if (!(key in src)) delete target[key]
    }
    // 写入快照中的所有 key（深拷贝避免共享引用）
    for (const key of Object.keys(src)) {
      target[key] = JSON.parse(JSON.stringify(src[key]))
    }
    // 重算 fieldSeq，避免 undo/redo 后新增字段 ID 冲突
    fieldSeq = 0
    for (const f of formConfig.fields) {
      const m = /field_(\d+)/.exec(f.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > fieldSeq) fieldSeq = n
      }
    }
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
  applyHistoryToFormConfig()
}

/** 重做 */
function redo() {
  commitPendingHistory()
  if (!canRedo.value) return
  history.redo()
  applyHistoryToFormConfig()
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

// ===================== 加载已有表单（编辑模式） =====================

async function loadForm(id: number) {
  loading.value = true
  try {
    const data = await getForm(id)
    Object.assign(metaForm, data)
    parseFormConfigFromStr()
    // 编辑模式加载完成后重置历史栈，使加载的配置成为初始状态（不可 undo 回空状态）
    if (historyDebounce) {
      clearTimeout(historyDebounce)
      historyDebounce = null
    }
    history.reset(JSON.parse(JSON.stringify(formConfig)))
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

// ===================== 保存 / 发布 / 归档 =====================

/** 保存草稿（创建或更新） */
async function handleSave() {
  if (!metaFormRef.value) return
  await metaFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (formConfig.fields.length === 0) {
      ElMessage.warning('请至少添加一个字段')
      return
    }
    syncFormConfigToStr()
    loading.value = true
    try {
      if (metaForm.id) {
        await updateForm(metaForm.id, metaForm)
        ElMessage.success('保存成功')
      } else {
        const created = await createForm(metaForm)
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

/** 发布 */
async function handlePublish() {
  if (!metaForm.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  syncFormConfigToStr()
  loading.value = true
  try {
    // 先保存最新配置
    await updateForm(metaForm.id, metaForm)
    await publishForm(metaForm.id)
    metaForm.status = 'PUBLISHED'
    ElMessage.success('发布成功')
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

/** 归档 */
async function handleArchive() {
  if (!metaForm.id) return
  try {
    await ElMessageBox.confirm('确认归档此表单？归档后不可再使用', '确认', { type: 'warning' })
    await archiveForm(metaForm.id)
    metaForm.status = 'ARCHIVED'
    ElMessage.success('归档成功')
  } catch {
    /* cancelled or error */
  }
}

// ===================== 导入 / 导出 =====================

/** 导出当前表单配置为 JSON 文件 */
async function handleExport() {
  if (!metaForm.code) {
    ElMessage.warning('请先填写表单编码')
    return
  }
  syncFormConfigToStr()
  // 若已存在记录，调用后端导出；否则本地导出
  if (metaForm.id) {
    try {
      await exportForm(metaForm.code)
      ElMessage.success('导出成功')
    } catch {
      /* handled by interceptor */
    }
  } else {
    // 本地导出
    const blob = new Blob([JSON.stringify(metaForm, null, 2)], {
      type: 'application/json'
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `form-${metaForm.code}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    setTimeout(() => URL.revokeObjectURL(url), 0)
    ElMessage.success('本地导出成功')
  }
}

/** 导入 JSON 文件 */
async function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,application/json'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    const text = await file.text()
    try {
      // 尝试调用后端导入接口
      const imported = await importForm(text)
      ElMessage.success(`导入成功，编码：${imported.code}`)
      // 加载导入后的表单到设计器
      Object.assign(metaForm, imported)
      parseFormConfigFromStr()
    } catch {
      // 后端导入失败时，尝试本地解析加载到画布
      try {
        const parsed = JSON.parse(text) as Partial<LowCodeFormConfig> & { fields?: unknown }
        if (parsed.formConfig && typeof parsed.formConfig === 'string') {
          Object.assign(metaForm, parsed)
          parseFormConfigFromStr()
          ElMessage.success('已加载到画布（本地解析，未提交后端）')
        } else if (Array.isArray(parsed.fields)) {
          // 直接是 FormConfig 结构（顶层含 fields 数组）
          metaForm.formConfig = text
          parseFormConfigFromStr()
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

/** 进入预览模式 */
function handlePreview() {
  syncFormConfigToStr()
  // 清空预览数据
  for (const k of Object.keys(previewData)) delete previewData[k]
  // 写入默认值
  for (const f of formConfig.fields) {
    previewData[f.prop] = f.defaultValue ?? ''
  }
  previewMode.value = true
}

/** 退出预览 */
function exitPreview() {
  previewMode.value = false
}

/** 预览提交 */
function handlePreviewSubmit(val: Record<string, unknown>) {
  ElMessageBox.alert(
    `<pre style="max-height:400px;overflow:auto;">${JSON.stringify(val, null, 2)}</pre>`,
    '提交数据预览',
    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }
  )
}

/** 重置画布 */
function handleReset() {
  ElMessageBox.confirm('确认清空画布所有字段？此操作不可恢复', '确认', { type: 'warning' })
    .then(() => {
      formConfig.fields = []
      selectedFieldId.value = ''
      fieldSeq = 0
      ElMessage.success('已重置画布')
    })
    .catch(() => {})
}

// ===================== 列表页（form-list）跳转 =====================

function goToList() {
  router.push('/lowcode/form-list')
}

// ===================== 初始化 =====================

const editId = route.query.id ? Number(route.query.id) : 0
if (editId > 0) {
  loadForm(editId)
} else {
  // 新建模式：初始化一个空标题
  formConfig.title = '未命名表单'
  // 重置历史栈，使初始标题作为干净的起点（不可 undo 回空标题）
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
  }
  history.reset(JSON.parse(JSON.stringify(formConfig)))
}

// ===================== 协同编辑（批次5-T6，借鉴 Mendix 协同编辑）=====================
// 注：当前为 HTTP 轮询简化方案，预留 WebSocket（y-websocket）升级点。
// 仅在编辑既有表单（editId > 0）时启用协同；新建模式不启用。
const collaboration = useCollaboration({
  configType: 'FORM',
  configId: editId,
  userId: userStore.userInfo?.id || 0,
  userName: userStore.userInfo?.nickname || userStore.userInfo?.username || ''
})

// 加载注册中心业务组件（15 个预置 Widget），合并到组件库"业务组件"分组
onMounted(async () => {
  window.addEventListener('keydown', onUndoRedoKeydown)
  try {
    await initBuiltinComponents()
    registryComponents.value = LowCodeComponentRegistry.list()
  } catch (e) {
    console.error('[FormDesigner] 组件加载失败，业务组件面板可能不可用:', e)
  }
  // 进入页面时加入协同会话（仅编辑模式）
  if (editId > 0) {
    collaboration.join()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onUndoRedoKeydown)
  if (historyDebounce) clearTimeout(historyDebounce)
})
</script>

<template>
  <div class="form-designer">
    <!-- ============ 顶部操作栏 ============ -->
    <el-card shadow="never" class="toolbar-card" :body-style="{ padding: '12px 16px' }">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" :icon="'Document'" :loading="loading" @click="handleSave">
            保存草稿
          </el-button>
          <el-button type="success" :icon="'Promotion'" @click="handlePublish">发布</el-button>
          <el-button :icon="'Download'" @click="handleExport">导出</el-button>
          <el-button :icon="'Upload'" @click="handleImport">导入</el-button>
          <el-button :icon="'View'" @click="handlePreview">预览</el-button>
          <el-button :icon="'RefreshLeft'" :disabled="!canUndo" @click="undo">撤销</el-button>
          <el-button :icon="'RefreshRight'" :disabled="!canRedo" @click="redo">重做</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button v-if="metaForm.status === 'PUBLISHED'" :icon="'FolderOpened'" @click="handleArchive">
            归档
          </el-button>
        </div>
        <div class="toolbar-right">
          <OnlineUsersIndicator
            v-if="editId > 0"
            :users="collaboration.onlineUsers.value"
            :current-user-id="userStore.userInfo?.id || 0"
          />
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
        <el-form-item label="表单编码" prop="code">
          <el-input v-model="metaForm.code" placeholder="如：tpl_project_create" :disabled="!!metaForm.id" style="width: 220px" />
        </el-form-item>
        <el-form-item label="表单名称" prop="name">
          <el-input v-model="metaForm.name" placeholder="请输入表单名称" style="width: 220px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="metaForm.bizType" placeholder="如：PROJECT" style="width: 160px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="metaForm.description" placeholder="表单描述" style="width: 320px" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ============ 主体三栏 ============ -->
    <div v-if="!previewMode" class="designer-body">
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
              :key="comp.type"
              class="comp-item"
              draggable="true"
              @dragstart="onDragStart($event, comp)"
              @click="addField(comp)"
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
            <span class="panel-title">画布（{{ formConfig.fields.length }} 个字段）</span>
            <el-form inline size="small" class="canvas-config">
              <el-form-item label="标签宽度">
                <el-input-number v-model="formConfig.labelWidth" :min="60" :max="200" :step="10" controls-position="right" style="width: 110px" />
              </el-form-item>
              <el-form-item label="标签位置">
                <el-select v-model="formConfig.labelPosition" style="width: 100px">
                  <el-option label="左" value="left" />
                  <el-option label="右" value="right" />
                  <el-option label="上" value="top" />
                </el-select>
              </el-form-item>
              <el-form-item label="尺寸">
                <el-select v-model="formConfig.size" style="width: 100px">
                  <el-option label="大" value="large" />
                  <el-option label="默认" value="default" />
                  <el-option label="小" value="small" />
                </el-select>
              </el-form-item>
              <el-form-item label="布局">
                <el-select v-model="formConfig.layout!.type" style="width: 110px">
                  <el-option label="栅格" value="grid" />
                  <el-option label="标签页" value="tabs" />
                  <el-option label="折叠面板" value="collapse" />
                </el-select>
              </el-form-item>
              <el-form-item label="间距" v-if="formConfig.layout!.type === 'grid'">
                <el-input-number v-model="formConfig.layout!.gutter" :min="0" :max="40" style="width: 100px" />
              </el-form-item>
            </el-form>
          </div>
        </template>

        <!-- 响应式预览栏：模拟不同屏幕宽度下的栅格布局 -->
        <div class="resp-preview-bar">
          <span class="resp-preview-bar__title">响应式预览</span>
          <el-radio-group v-model="previewWidth" size="small">
            <el-radio-button value="auto">自适应</el-radio-button>
            <el-radio-button v-for="bp in breakpointOrder" :key="bp" :value="bp">{{ bp }}</el-radio-button>
          </el-radio-group>
          <span v-if="activePreviewBp" class="resp-preview-bar__tip">
            模拟 {{ previewLabel }}（{{ previewWidthPx }}px）— 展示该断点下各字段栅格占比
          </span>
        </div>

        <!-- 响应式预览栅格（仅特定断点且有字段时显示） -->
        <div
          v-if="activePreviewBp && formConfig.fields.length > 0"
          class="resp-preview-grid"
          :style="{ maxWidth: previewWidthPx + 'px' }"
        >
          <el-row :gutter="formConfig.layout?.gutter ?? 16">
            <el-col
              v-for="row in previewRows"
              :key="row.field.id"
              :span="row.span"
            >
              <div
                class="resp-preview-cell"
                :class="{ active: selectedFieldId === row.field.id }"
                @click="selectField(row.field.id)"
              >
                <span class="resp-preview-cell__label">{{ row.field.label }}</span>
                <span class="resp-preview-cell__span">{{ row.span }}/24</span>
              </div>
            </el-col>
          </el-row>
        </div>

        <!-- 拖拽放置区 -->
        <div
          class="canvas-dropzone"
          :class="{ empty: formConfig.fields.length === 0 }"
          @dragover="onCanvasDragOver"
          @drop="onCanvasDrop"
        >
          <div v-if="formConfig.fields.length === 0" class="empty-tip">
            <el-icon :size="40"><Plus /></el-icon>
            <p>从左侧拖拽组件到此处，或点击组件添加</p>
          </div>

          <!-- 字段列表 -->
          <div v-else class="field-list">
            <div
              v-for="(field, idx) in formConfig.fields"
              :key="field.id"
              class="field-card"
              :class="{ active: selectedFieldId === field.id }"
              @click="selectField(field.id)"
            >
              <div class="field-card-header">
                <el-tag size="small" :type="field.type === FieldType.CUSTOM && field.componentName ? 'warning' : 'info'">
                  {{ field.type === FieldType.CUSTOM && field.componentName ? field.componentName : field.type }}
                </el-tag>
                <span class="field-label">{{ field.label }}</span>
                <span class="field-prop">{{ field.prop }}</span>
                <span v-if="field.required" class="field-required">*</span>
                <div class="field-actions">
                  <el-button-group size="small">
                    <el-button :icon="'Top'" :disabled="idx === 0" @click.stop="moveUp(field.id)" />
                    <el-button :icon="'Bottom'" :disabled="idx === formConfig.fields.length - 1" @click.stop="moveDown(field.id)" />
                    <el-button :icon="'CopyDocument'" @click.stop="duplicateField(field.id)" />
                    <el-button :icon="'Delete'" type="danger" @click.stop="removeField(field.id)" />
                  </el-button-group>
                </div>
              </div>
              <div class="field-preview">
                <el-input
                  v-if="field.type === FieldType.INPUT"
                  :placeholder="field.placeholder"
                  disabled
                  size="small"
                />
                <el-input
                  v-else-if="field.type === FieldType.TEXTAREA"
                  type="textarea"
                  :rows="2"
                  :placeholder="field.placeholder"
                  disabled
                  size="small"
                />
                <el-input-number
                  v-else-if="field.type === FieldType.NUMBER"
                  :placeholder="field.placeholder"
                  disabled
                  size="small"
                  style="width: 100%"
                />
                <el-select
                  v-else-if="field.type === FieldType.SELECT"
                  :placeholder="field.placeholder"
                  disabled
                  size="small"
                  style="width: 100%"
                />
                <el-date-picker
                  v-else-if="field.type === FieldType.DATE || field.type === FieldType.DATETIME || field.type === FieldType.DATERANGE"
                  :placeholder="field.placeholder"
                  disabled
                  size="small"
                  style="width: 100%"
                />
                <el-switch v-else-if="field.type === FieldType.SWITCH" disabled size="small" />
                <el-rate v-else-if="field.type === FieldType.RATE" disabled size="small" />
                <el-slider v-else-if="field.type === FieldType.SLIDER" disabled size="small" />
                <el-divider v-else-if="field.type === FieldType.DIVIDER">{{ field.label }}</el-divider>
                <h4 v-else-if="field.type === FieldType.TITLE">{{ field.label }}</h4>
                <span v-else>{{ field.label }}（{{ field.type }}）</span>
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

        <div v-if="!selectedField" class="empty-prop">
          <el-empty description="请选择一个字段" :image-size="80" />
        </div>

        <el-form v-else :model="selectedField" label-width="90px" size="small">
          <!-- 基础属性 -->
          <el-divider content-position="left">基础属性</el-divider>
          <el-form-item label="字段类型">
            <!-- registry 业务组件：只读展示，不可切换（切换会丢失 componentName 与 schema 绑定） -->
            <el-tag v-if="isSelectedRegistry" type="warning" size="small">
              业务组件：{{ selectedField.componentName }}
            </el-tag>
            <el-select
              v-else
              :model-value="selectedField.type"
              @change="(v: string) => changeFieldType(selectedField!, v)"
              style="width: 100%"
            >
              <el-option-group v-for="g in componentGroups" :key="g.title" :label="g.title">
                <el-option v-for="c in g.items" :key="c.type" :label="c.label" :value="c.type" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="selectedField.label" />
          </el-form-item>
          <el-form-item label="字段名">
            <el-input v-model="selectedField.prop" />
          </el-form-item>
          <el-form-item label="占位符">
            <el-input v-model="selectedField.placeholder" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="selectedField.defaultValue" placeholder="留空表示无默认值" />
          </el-form-item>

          <!-- registry 业务组件：schema 驱动属性面板（propsSchema 渲染） -->
          <template v-if="isSelectedRegistry && selectedComponentMeta">
            <el-divider content-position="left">组件属性</el-divider>
            <LowCodePropertyPanel
              :meta="selectedComponentMeta"
              :model-value="(selectedField.props as Record<string, any>) || {}"
              @update:model-value="(v: Record<string, any>) => { selectedField!.props = v }"
            />
          </template>

          <!-- 选项编辑（select/radio/checkbox/cascader） -->
          <template v-if="!isSelectedRegistry && selectedField.props && ('options' in selectedField.props)">
            <el-divider content-position="left">选项配置</el-divider>
            <div
              v-for="(opt, idx) in (selectedField.props.options as Array<{ label: string; value: string }>)"
              :key="idx"
              class="option-row"
            >
              <el-input v-model="opt.label" placeholder="标签" size="small" style="width: 40%" />
              <el-input v-model="opt.value" placeholder="值" size="small" style="width: 40%; margin-left: 4px" />
              <el-button :icon="'Delete'" type="danger" size="small" @click="removeOption(selectedField!, idx)" style="margin-left: 4px" />
            </div>
            <el-button :icon="'Plus'" size="small" @click="addOption(selectedField!)">添加选项</el-button>
          </template>

          <!-- 校验属性 -->
          <el-divider content-position="left">校验属性</el-divider>
          <el-form-item label="必填">
            <el-switch v-model="selectedField.required" />
          </el-form-item>
          <el-form-item label="最大长度" v-if="selectedField.type === FieldType.INPUT || selectedField.type === FieldType.TEXTAREA">
            <el-input-number
              :model-value="selectedField.props?.maxlength as number"
              @change="(v: number) => { selectedField!.props = selectedField!.props || {}; selectedField!.props.maxlength = v }"
              :min="1"
              :max="9999"
              style="width: 100%"
            />
          </el-form-item>

          <!-- 样式属性 -->
          <el-divider content-position="left">样式属性</el-divider>
          <el-form-item label="响应式栅格">
            <el-switch v-model="isResponsive" />
            <span class="form-tip">开启后按 xs/sm/md/lg/xl 五档断点配置</span>
          </el-form-item>
          <el-form-item v-if="!isResponsive" label="栅格宽度">
            <el-slider v-model="fieldSpan" :min="1" :max="24" show-input style="width: 100%" />
          </el-form-item>
          <el-collapse v-else v-model="responsiveCollapse" class="resp-collapse">
            <el-collapse-item name="resp">
              <template #title>
                <span>响应式断点（1-24，留空继承更小断点）</span>
              </template>
              <el-form-item v-for="bp in breakpointOrder" :key="bp" :label="bp">
                <div class="bp-row">
                  <el-input-number
                    :model-value="getBreakpoint(bp)"
                    :min="1"
                    :max="24"
                    controls-position="right"
                    placeholder="留空"
                    style="flex: 1"
                    @update:model-value="(v: number | undefined) => setBreakpoint(bp, v ?? undefined)"
                  />
                  <el-button
                    v-if="getBreakpoint(bp) !== undefined"
                    link
                    type="primary"
                    :icon="'Close'"
                    title="清除（留空，继承更小断点）"
                    @click="setBreakpoint(bp, undefined)"
                  />
                </div>
              </el-form-item>
            </el-collapse-item>
          </el-collapse>
          <el-form-item label="可清空">
            <el-switch v-model="selectedField.clearable" />
          </el-form-item>

          <!-- 状态属性 -->
          <el-divider content-position="left">状态属性</el-divider>
          <el-form-item label="禁用">
            <el-switch v-model="selectedField.disabled" />
          </el-form-item>
          <el-form-item label="只读">
            <el-switch v-model="selectedField.readonly" />
          </el-form-item>
          <el-form-item label="隐藏">
            <el-switch v-model="selectedField.hidden" />
          </el-form-item>

          <!-- 高级属性 -->
          <el-divider content-position="left">高级属性</el-divider>
          <el-form-item label="change回调">
            <el-input v-model="selectedField.events!.change" placeholder="如：onFieldChange" />
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- ============ 预览模式 ============ -->
    <el-card v-else shadow="never" class="preview-card">
      <template #header>
        <div class="preview-header">
          <span class="panel-title">表单预览：{{ formConfig.title || metaForm.name }}</span>
          <el-button :icon="'Back'" @click="exitPreview">退出预览</el-button>
        </div>
      </template>
      <LowCodeFormRenderer
        :config="formConfig"
        v-model="previewData"
        @submit="handlePreviewSubmit"
      />
    </el-card>
  </div>
</template>

<style scoped>
.form-designer {
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
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.canvas-config {
  margin: 0;
}

.canvas-config :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 8px;
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

.field-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-card {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: all 0.2s;
}

.field-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.field-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.field-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}

.field-label {
  font-weight: 600;
}

.field-prop {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-family: monospace;
}

.field-required {
  color: var(--el-color-danger);
  font-weight: bold;
}

.field-actions {
  margin-left: auto;
}

.field-preview {
  padding: 4px 0;
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

/* 响应式断点行：输入框 + 清除按钮 */
.bp-row {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

/* 响应式预览栏 */
.resp-preview-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 0 4px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.resp-preview-bar__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.resp-preview-bar__tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 响应式预览栅格（模拟不同屏幕宽度） */
.resp-preview-grid {
  margin: 0 auto 12px;
  padding: 8px;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 4px;
  background: var(--el-color-primary-light-9);
  transition: max-width 0.2s;
}

.resp-preview-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  padding: 4px;
  border: 1px solid var(--el-border-color);
  border-radius: 3px;
  background: var(--el-bg-color);
  cursor: pointer;
  font-size: 12px;
  text-align: center;
  word-break: break-all;
  transition: all 0.2s;
}

.resp-preview-cell:hover {
  border-color: var(--el-color-primary-light-5);
}

.resp-preview-cell.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.resp-preview-cell__label {
  font-weight: 600;
}

.resp-preview-cell__span {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-family: monospace;
}

.preview-card {
  flex: 1;
  overflow: auto;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
