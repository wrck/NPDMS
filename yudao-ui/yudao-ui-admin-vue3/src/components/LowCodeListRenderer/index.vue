<script setup lang="ts">
/**
 * 低代码列表渲染引擎。
 *
 * <p>根据传入的 {@link ListConfig} 动态渲染 Element Plus 表格，支持：</p>
 * <ul>
 *   <li>10 种列类型（text/image/tag/date/datetime/currency/percent/link/dict/custom）</li>
 *   <li>5 种筛选类型（input/select/date/daterange/cascader）</li>
 *   <li>行操作按钮（edit/view/delete/custom）+ 显示条件 + 权限指令</li>
 *   <li>工具栏按钮（create/custom）+ 权限指令</li>
 *   <li>分页（el-pagination，可关闭）+ 多选 + 序号 + 排序</li>
 *   <li>字典翻译（dictMap 注入 / 内部按 dictCode 异步加载缓存）</li>
 *   <li>导出（调用 config.export.api）</li>
 *   <li>自动请求（无 data 时按 searchApi 拉取数据）</li>
 * </ul>
 *
 * <p>对外暴露 selection-change / operation-click / page-change / filter-change
 * 事件，方便业务层介入。同时通过 defineExpose 暴露 refresh / getSelection /
 * getFilters / exportData 方法供父组件通过 ref 调用。</p>
 */
import { computed, reactive, ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import {
  ActionType,
  ColumnType,
  FilterType,
  type ListColumnConfig,
  type ListConfig,
  type ListFilterConfig,
  type ListOperationConfig,
  type ResponsiveSpan
} from '@/api/lowcode'
import { getDictPage, getDictItems, type SysDictItem } from '@/api/system'
import { TOKEN_KEY } from '@/utils/request'
import { triggerBlobDownload } from '@/api/excel'
import type { EpTagType } from '@/types'

/** Element Plus el-button type 联合（用于消除低代码配置中 op.type: string 的 as any） */
type EpButtonType = '' | 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'info'

/** Props 定义 */
const props = withDefaults(
  defineProps<{
    /** 列表配置（解析后的 ListConfig 对象） */
    config: ListConfig
    /** 表格数据（不传则由组件根据 searchApi 自动请求） */
    data?: Array<Record<string, unknown>>
    /** 加载状态（仅在传入 data 时由父组件控制） */
    loading?: boolean
    /** 默认每页条数（覆盖 config.pageSize） */
    pageSize?: number
    /** 字典预加载映射：key 为 dictCode，value 为字典项数组 */
    dictMap?: Record<string, Array<{ label: string; value: string | number }>>
    /** 自定义组件注册表：key 为列 prop，value 为渲染函数（type=custom 时使用插槽 fallback） */
    autoFetch?: boolean
  }>(),
  {
    loading: false,
    pageSize: 20,
    dictMap: () => ({}),
    autoFetch: true
  }
)

/** Emits 定义 */
const emit = defineEmits<{
  (e: 'selection-change', selection: Array<Record<string, unknown>>): void
  (e: 'operation-click', operation: ListOperationConfig, row: Record<string, unknown>): void
  (e: 'toolbar-click', operation: ListOperationConfig): void
  (e: 'page-change', page: number, size: number): void
  (e: 'filter-change', filters: Record<string, unknown>): void
  (e: 'data-loaded', records: Array<Record<string, unknown>>, total: number): void
}>()

const router = useRouter()

// ===================== 内部数据状态 =====================

/** 内部维护的表格数据（autoFetch=true 时由 searchApi 拉取） */
const innerData = ref<Array<Record<string, unknown>>>([])
/** 内部加载状态 */
const innerLoading = ref(false)
/** 内部分页总条数 */
const innerTotal = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页条数 */
const pageSizeRef = ref(props.pageSize || props.config.pageSize || 20)
/** 当前选中行 */
const selection = ref<Array<Record<string, unknown>>>([])

/**
 * 实际渲染数据：优先使用父组件传入的 data，否则使用内部 innerData。
 */
const tableData = computed(() => props.data ?? innerData.value)

/** 实际加载状态 */
const loading = computed(() => props.loading || innerLoading.value)

/** 实际分页总条数：父组件 data 模式下用 data.length，自动模式下用 innerTotal */
const total = computed(() => (props.data ? props.data.length : innerTotal.value))

/** 实际使用的 pageSizes */
const pageSizes = computed(() => props.config.pageSizes ?? [10, 20, 50, 100])

/**
 * Runtime list configs generated from an entity historically only stored
 * entityCode. Derive the standard dynamic-data endpoint so those configs are
 * immediately runnable without duplicating a URL in every saved page.
 */
const effectiveSearchApi = computed(() => {
  if (props.config.searchApi) return props.config.searchApi
  if (props.config.entityCode) {
    return `/api/lowcode/data/${encodeURIComponent(props.config.entityCode)}`
  }
  return ''
})

/** Convention-based form target for entity-generated list pages. */
const effectiveFormCode = computed(() => {
  if (props.config.formCode) return props.config.formCode
  if (props.config.entityCode) return `form_${props.config.entityCode}`
  return ''
})

// ===================== 筛选表单 =====================

/** 筛选表单数据（按 filter.prop 为 key） */
const filterForm = reactive<Record<string, unknown>>({})

/**
 * 将筛选项的 span 解析为单个宽度数字（1-24），用于内联百分比宽度。
 *
 * <p>向后兼容：span 为数字时直接使用；span 为响应式断点对象时取 md 优先，
 * 依次回退 sm/lg/xs/xl，全缺省时返回 undefined（不设宽度）。</p>
 */
function resolveFilterSpan(span: number | ResponsiveSpan | undefined): number | undefined {
  if (span === undefined) return undefined
  if (typeof span === 'number') return span
  return span.md ?? span.sm ?? span.lg ?? span.xs ?? span.xl
}

/** 计算筛选项的内联样式（按 span 设置百分比宽度） */
function filterStyle(f: ListFilterConfig): Record<string, string> | undefined {
  const spanNum = resolveFilterSpan(f.span)
  return spanNum ? { width: `${(spanNum / 24) * 100}%`, flex: '0 0 auto' } : undefined
}

/** 初始化筛选项默认值 */
function initFilterDefaults() {
  for (const f of props.config.filters ?? []) {
    if (f.defaultValue !== undefined) {
      filterForm[f.prop] = f.defaultValue
    } else if (f.type === FilterType.DATERANGE) {
      filterForm[f.prop] = []
    } else if (f.type === FilterType.SELECT && f.multiple) {
      filterForm[f.prop] = []
    } else {
      filterForm[f.prop] = ''
    }
  }
}

// 监听 config.filters 变化，重新初始化默认值
watch(
  () => props.config.filters,
  () => initFilterDefaults(),
  { immediate: true, deep: true }
)

/** 查询 */
function handleSearch() {
  currentPage.value = 1
  emit('filter-change', { ...filterForm })
  if (props.autoFetch && effectiveSearchApi.value) {
    fetchData()
  }
}

/** 重置筛选 */
function handleReset() {
  for (const f of props.config.filters ?? []) {
    if (f.defaultValue !== undefined) {
      filterForm[f.prop] = f.defaultValue
    } else if (f.type === FilterType.DATERANGE) {
      filterForm[f.prop] = []
    } else if (f.type === FilterType.SELECT && f.multiple) {
      filterForm[f.prop] = []
    } else {
      filterForm[f.prop] = ''
    }
  }
  handleSearch()
}

/** 筛选组件解析 */
function resolveFilterComponent(f: ListFilterConfig) {
  switch (f.type) {
    case FilterType.SELECT:
      return 'el-select'
    case FilterType.DATE:
    case FilterType.DATERANGE:
      return 'el-date-picker'
    case FilterType.CASCADER:
      return 'el-cascader'
    default:
      return 'el-input'
  }
}

/** 筛选 select 选项（优先 options，否则从 dictMap 取） */
function getFilterOptions(f: ListFilterConfig): Array<{ label: string; value: string | number }> {
  if (f.options && f.options.length > 0) return f.options
  if (f.dictCode && props.dictMap[f.dictCode]) return props.dictMap[f.dictCode]
  return []
}

// ===================== 字典翻译 =====================

/** 内部字典缓存（按 dictCode 索引） */
const dictCache = reactive<Record<string, Array<{ label: string; value: string | number }>>>({
  ...props.dictMap
})

/** 标记正在加载的 dictCode，避免重复请求 */
const loadingDictCodes = reactive<Record<string, boolean>>({})

/** 需要字典翻译的列 */
const dictColumns = computed(() =>
  (props.config.columns || []).filter(
    (c) => c.type === ColumnType.DICT && c.dictCode && !c.hidden
  )
)

/** 需要字典翻译的筛选 */
const dictFilters = computed(() =>
  (props.config.filters || []).filter(
    (f) => f.type === FilterType.SELECT && f.dictCode && (!f.options || f.options.length === 0)
  )
)

/** 加载指定 dictCode 的字典项（异步，带缓存） */
async function loadDict(dictCode: string): Promise<void> {
  if (!dictCode || dictCache[dictCode] || loadingDictCodes[dictCode]) return
  loadingDictCodes[dictCode] = true
  try {
    // 通过 dictName 查询字典，取第一项匹配 dictType 的字典
    const dictPage = await getDictPage({ dictName: dictCode, page: 1, size: 50 })
    const dict = (dictPage.records || []).find((d) => d.dictType === dictCode)
    if (!dict || !dict.id) {
      // 未找到字典，置为空数组避免重复加载
      dictCache[dictCode] = []
      return
    }
    const items: SysDictItem[] = await getDictItems(dictCode)
    dictCache[dictCode] = items.map((it) => ({ label: it.itemText, value: it.itemValue }))
  } catch {
    // 加载失败也置空，避免无限重试
    dictCache[dictCode] = []
  } finally {
    loadingDictCodes[dictCode] = false
  }
}

/** 字典翻译：根据 dictCode + value 返回 label */
function translateDict(dictCode: string, value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  const items = dictCache[dictCode]
  if (!items) return String(value)
  const item = items.find((it) => String(it.value) === String(value))
  return item ? item.label : String(value)
}

/** 初始化所有字典（dictMap 已传入的不再加载） */
function initDicts() {
  for (const col of dictColumns.value) {
    if (col.dictCode) loadDict(col.dictCode)
  }
  for (const f of dictFilters.value) {
    if (f.dictCode) loadDict(f.dictCode)
  }
}

watch(
  () => [props.config.columns, props.config.filters],
  () => initDicts(),
  { immediate: true, deep: true }
)

// ===================== 列渲染辅助 =====================

/** 可见列（过滤 hidden） */
const visibleColumns = computed(() =>
  (props.config.columns || []).filter((c) => !c.hidden)
)

/** 解析 fixed 属性：字符串 "false" 或布尔 false 均视为不固定 */
function resolveFixed(fixed: string | boolean | undefined): boolean | string {
  if (fixed === undefined || fixed === false || fixed === 'false') return false
  return fixed as string | true
}

/** 单元格值 */
function getCellValue(row: Record<string, unknown>, col: ListColumnConfig): unknown {
  return row[col.prop]
}

/** 格式化日期 */
function formatDate(val: unknown, fmt: string): string {
  if (val === null || val === undefined || val === '') return ''
  const s = String(val)
  // 兼容 ISO 字符串、时间戳、YYYY-MM-DD 等
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  const map: Record<string, string> = {
    YYYY: String(d.getFullYear()),
    MM: pad(d.getMonth() + 1),
    DD: pad(d.getDate()),
    HH: pad(d.getHours()),
    mm: pad(d.getMinutes()),
    ss: pad(d.getSeconds())
  }
  return fmt.replace(/YYYY|MM|DD|HH|mm|ss/g, (m) => map[m])
}

/** 格式化货币：千分位 + 货币符号 */
function formatCurrency(val: unknown, symbol = '¥'): string {
  if (val === null || val === undefined || val === '') return ''
  const n = Number(val)
  if (isNaN(n)) return String(val)
  return symbol + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 格式化百分比：值 × 100 + % */
function formatPercent(val: unknown, decimals = 2): string {
  if (val === null || val === undefined || val === '') return ''
  const n = Number(val)
  if (isNaN(n)) return String(val)
  return (n * 100).toFixed(decimals) + '%'
}

/** 解析 formatter 字符串：返回 { kind, args } */
function parseFormatter(formatter?: string): { kind: string; args: string } | null {
  if (!formatter) return null
  const idx = formatter.indexOf(':')
  if (idx < 0) return { kind: formatter, args: '' }
  return { kind: formatter.slice(0, idx), args: formatter.slice(idx + 1) }
}

/** 综合渲染单元格内容（用于非 custom 列） */
function renderCell(row: Record<string, unknown>, col: ListColumnConfig): string {
  const val = getCellValue(row, col)
  const type = col.type || ColumnType.TEXT
  switch (type) {
    case ColumnType.DATE:
      return formatDate(val, 'YYYY-MM-DD')
    case ColumnType.DATETIME:
      return formatDate(val, 'YYYY-MM-DD HH:mm:ss')
    case ColumnType.CURRENCY:
      return formatCurrency(val)
    case ColumnType.PERCENT:
      return formatPercent(val)
    case ColumnType.DICT:
      return col.dictCode ? translateDict(col.dictCode, val) : String(val ?? '')
    default:
      // TEXT / TAG / IMAGE / LINK 由模板特殊渲染，这里返回原始字符串作为 fallback
      return val === null || val === undefined ? '' : String(val)
  }
}

/** 处理 formatter 覆盖（如 dateFormat:YYYY-MM/DD） */
function renderWithFormatter(row: Record<string, unknown>, col: ListColumnConfig): string {
  const fmt = parseFormatter(col.formatter)
  if (!fmt) return renderCell(row, col)
  const val = getCellValue(row, col)
  switch (fmt.kind) {
    case 'dateFormat':
      return formatDate(val, fmt.args || 'YYYY-MM-DD')
    case 'datetimeFormat':
      return formatDate(val, fmt.args || 'YYYY-MM-DD HH:mm:ss')
    case 'currency':
      return formatCurrency(val, fmt.args || '¥')
    case 'percent':
      return formatPercent(val, fmt.args ? Number(fmt.args) : 2)
    default:
      return renderCell(row, col)
  }
}

/** 链接跳转：替换 {prop} 占位符 */
function resolveLinkUrl(url: string, row: Record<string, unknown>): string {
  return url
    .replace(/\{(\w+)\}/g, (_m, key: string) => String(row[key] ?? ''))
    .replace(/&amp;/g, '&')
}

/** 处理链接点击 */
function handleLinkClick(col: ListColumnConfig, row: Record<string, unknown>) {
  if (!col.linkUrl) return
  const url = resolveLinkUrl(col.linkUrl, row)
  // 简单路由跳转（外部链接用 location.href）
  if (url.startsWith('http')) {
    window.location.href = url
  } else {
    router.push(url)
  }
}

// ===================== 行操作 / 工具栏 =====================

/** 操作列宽度估算：每按钮约 60px + padding */
const operationsWidth = computed(() => {
  const ops = visibleRowOps.value
  if (!ops.length) return 0
  return Math.max(ops.length * 70 + 20, 100)
})

/** 可见行操作（不考虑 row 维度 visible） */
const visibleRowOps = computed(() => props.config.operations || [])

/** 计算行维度可见操作（visible 表达式求值） */
function getVisibleRowOps(row: Record<string, unknown>): ListOperationConfig[] {
  return visibleRowOps.value.filter((op) => {
    if (!op.visible) return true
    try {
      // 简单表达式求值：以 row 为上下文
      // eslint-disable-next-line no-new-func
      const fn = new Function('row', `return (${op.visible})`)
      return !!fn(row)
    } catch {
      return true
    }
  })
}

/** 行操作点击处理 */
async function handleRowClick(
  op: ListOperationConfig,
  row: Record<string, unknown>,
  index: number,
  event: MouseEvent
) {
  let actualRow = row
  if (!row || Object.keys(row).length === 0) {
    const tr = (event.target as HTMLElement).closest('tr')
    if (tr) {
      const allRows = Array.from(document.querySelectorAll('.el-table__body-wrapper tbody tr'))
      const realIndex = allRows.indexOf(tr)
      const sourceData = props.data ?? innerData.value
      if (realIndex >= 0 && sourceData?.[realIndex]) actualRow = sourceData[realIndex]
    }
    if ((!actualRow || Object.keys(actualRow).length === 0) && index >= 0) {
      const sourceData = props.data ?? innerData.value
      if (sourceData?.[index]) actualRow = sourceData[index]
    }
  }
  // 二次确认
  if (op.confirm) {
    try {
      await ElMessageBox.confirm(op.confirm, '确认', { type: 'warning' })
    } catch {
      return // 用户取消
    }
  }
  switch (op.action) {
    case ActionType.EDIT:
    case ActionType.VIEW:
      if (op.url) {
        router.push(resolveLinkUrl(op.url, actualRow))
      } else if (effectiveFormCode.value && actualRow.id != null) {
        router.push({
          path: `/lowcode/form/${effectiveFormCode.value}`,
          query: { mode: op.action === ActionType.EDIT ? 'edit' : 'view', id: String(actualRow.id) }
        })
      } else {
        emit('operation-click', op, actualRow)
      }
      break
    case ActionType.DELETE:
      if (op.api) {
        try {
          const url = resolveLinkUrl(op.api, actualRow)
          const method = (op.method || 'DELETE').toUpperCase()
          const token = localStorage.getItem(TOKEN_KEY) || ''
          await axios.request({ url, method, headers: { Authorization: `Bearer ${token}` } })
          ElMessage.success('删除成功')
          fetchData()
        } catch {
          /* handled by interceptor */
        }
      }
      break
    case ActionType.CUSTOM:
    default:
      emit('operation-click', op, actualRow)
  }
}

/** 工具栏点击处理 */
function handleToolbarClick(op: ListOperationConfig) {
  if (op.confirm) {
    ElMessageBox.confirm(op.confirm, '确认', { type: 'warning' })
      .then(() => execToolbar(op))
      .catch(() => {})
    return
  }
  execToolbar(op)
}

function execToolbar(op: ListOperationConfig) {
  switch (op.action) {
    case ActionType.CREATE:
    case ActionType.EDIT:
    case ActionType.VIEW:
      if (op.url) router.push(op.url)
      else if (op.action === ActionType.CREATE && effectiveFormCode.value) {
        router.push({ path: `/lowcode/form/${effectiveFormCode.value}`, query: { mode: 'create' } })
      }
      else emit('toolbar-click', op)
      break
    case ActionType.CUSTOM:
    default:
      emit('toolbar-click', op)
  }
}

/** 行操作按钮类型映射（默认 text 模式更紧凑） */
function rowButtonType(op: ListOperationConfig): '' | string {
  return op.type || ''
}

// ===================== 分页 / 多选 =====================

/** 多选变化 */
function handleSelectionChange(sel: Array<Record<string, unknown>>) {
  selection.value = sel
  emit('selection-change', sel)
}

/** 页码变化 */
function handleCurrentChange(page: number) {
  currentPage.value = page
  emit('page-change', page, pageSizeRef.value)
  if (props.autoFetch && effectiveSearchApi.value) fetchData()
}

/** 每页条数变化 */
function handleSizeChange(size: number) {
  pageSizeRef.value = size
  currentPage.value = 1
  emit('page-change', 1, size)
  if (props.autoFetch && effectiveSearchApi.value) fetchData()
}

/** 排序变化（透传到查询参数） */
function handleSortChange(_payload: { column: unknown; prop: string; order: string | null }) {
  // 排序交给后端：触发一次查询即可
  if (props.autoFetch && effectiveSearchApi.value) fetchData()
}

// ===================== 数据请求 =====================

/** 构造查询参数（含分页 + 筛选） */
function buildQuery(): Record<string, unknown> {
  const q: Record<string, unknown> = {
    current: currentPage.value,
    size: pageSizeRef.value
  }
  if (!props.config.searchApi && props.config.entityCode) {
    q.page = currentPage.value
  }
  for (const f of props.config.filters ?? []) {
    const v = filterForm[f.prop]
    if (v !== '' && v !== null && v !== undefined && !(Array.isArray(v) && v.length === 0)) {
      q[f.prop] = v
    }
  }
  return q
}

/** 按 searchApi 拉取数据 */
async function fetchData(): Promise<void> {
  const api = effectiveSearchApi.value
  if (!api) return
  innerLoading.value = true
  try {
    const method = (props.config.method || 'GET').toUpperCase()
    const query = buildQuery()
    const token = localStorage.getItem(TOKEN_KEY) || ''
    const headers: Record<string, string> = { Authorization: `Bearer ${token}` }
    let response
    if (method === 'POST') {
      response = await axios.post(api, query, { headers })
    } else {
      response = await axios.get(api, { params: query, headers })
    }
    // 兼容后端统一 envelope { code, data, ... } 或裸 IPage
    const payload = response.data
    const page = payload?.data ?? payload
    innerData.value = page?.records ?? page?.list ?? (Array.isArray(page) ? page : [])
    innerTotal.value = page?.total ?? innerData.value.length
    emit('data-loaded', innerData.value, innerTotal.value)
  } catch (e) {
    innerData.value = []
    innerTotal.value = 0
    // 不弹错（拦截器已处理），仅在控制台留痕
    console.warn('[LowCodeListRenderer] 加载数据失败', e)
  } finally {
    innerLoading.value = false
  }
}

// ===================== 导出 =====================

/** 触发导出（调用 config.export.api） */
async function exportData(): Promise<void> {
  const exp = props.config.export
  if (!exp?.enabled) {
    ElMessage.warning('未启用导出')
    return
  }
  if (!exp.api) {
    ElMessage.warning('未配置导出接口')
    return
  }
  try {
    const token = localStorage.getItem(TOKEN_KEY) || ''
    const query = exp.withFilter === false ? {} : buildQuery()
    const response = await axios.get(exp.api, {
      params: query,
      responseType: 'blob',
      headers: { Authorization: `Bearer ${token}` }
    })
    const fileName = exp.fileName ? `${exp.fileName}-${Date.now()}.xlsx` : `export-${Date.now()}.xlsx`
    triggerBlobDownload(response.data, fileName)
    ElMessage.success('导出成功')
  } catch (e) {
    console.warn('[LowCodeListRenderer] 导出失败', e)
  }
}

// ===================== 暴露方法 =====================

defineExpose({
  /** 重新加载数据 */
  refresh: fetchData,
  /** 获取当前选中行 */
  getSelection: () => selection.value,
  /** 获取当前筛选条件 */
  getFilters: () => ({ ...filterForm }),
  /** 触发导出 */
  exportData,
  /** 重置筛选并加载 */
  resetAndFetch: handleReset
})

// ===================== 初始化 =====================

onMounted(() => {
  if (props.autoFetch && effectiveSearchApi.value) {
    fetchData()
  }
})

// 当显式查询接口或实体绑定变化时重新加载
watch(
  effectiveSearchApi,
  (api) => {
    if (props.autoFetch && api) {
      currentPage.value = 1
      fetchData()
    }
  }
)
</script>

<template>
  <div class="low-code-list-renderer">
    <!-- ============ 筛选区 ============ -->
    <el-form
      v-if="config.filters && config.filters.length"
      inline
      :model="filterForm"
      class="list-filter-form"
    >
      <el-form-item
        v-for="f in config.filters"
        :key="f.id"
        :label="f.label"
        :style="filterStyle(f)"
      >
        <!-- 输入框 -->
        <el-input
          v-if="f.type === FilterType.INPUT"
          v-model="filterForm[f.prop]"
          :placeholder="f.placeholder || `请输入${f.label}`"
          :clearable="f.clearable !== false"
          @keyup.enter="handleSearch"
        />
        <!-- 下拉选择 -->
        <el-select
          v-else-if="f.type === FilterType.SELECT"
          v-model="filterForm[f.prop]"
          :placeholder="f.placeholder || `请选择${f.label}`"
          :clearable="f.clearable !== false"
          :multiple="f.multiple === true"
          style="min-width: 160px"
        >
          <el-option
            v-for="opt in getFilterOptions(f)"
            :key="String(opt.value)"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <!-- 日期 -->
        <el-date-picker
          v-else-if="f.type === FilterType.DATE"
          v-model="filterForm[f.prop]"
          type="date"
          :placeholder="f.placeholder || `请选择${f.label}`"
          :clearable="f.clearable !== false"
          value-format="YYYY-MM-DD"
        />
        <!-- 日期范围 -->
        <el-date-picker
          v-else-if="f.type === FilterType.DATERANGE"
          v-model="filterForm[f.prop]"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="f.clearable !== false"
          value-format="YYYY-MM-DD"
        />
        <!-- 级联选择 -->
        <el-cascader
          v-else-if="f.type === FilterType.CASCADER"
          v-model="filterForm[f.prop]"
          :options="(f.options as unknown as Array<{ label: string; value: string | number; children?: unknown[] }>)"
          :placeholder="f.placeholder || `请选择${f.label}`"
          :clearable="f.clearable !== false"
        />
        <!-- 默认：输入框 -->
        <el-input
          v-else
          v-model="filterForm[f.prop]"
          :placeholder="f.placeholder || `请输入${f.label}`"
          :clearable="f.clearable !== false"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="'Search'" @click="handleSearch">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- ============ 工具栏 ============ -->
    <div
      v-if="config.toolbar && config.toolbar.length"
      class="list-toolbar"
    >
      <el-button
        v-for="op in config.toolbar"
        :key="op.id"
        :type="(op.type as EpButtonType) || 'default'"
        :icon="op.icon"
        v-permission="op.permission"
        @click="handleToolbarClick(op)"
      >
        {{ op.label }}
      </el-button>
      <!-- 导出按钮（如配置启用） -->
      <el-button
        v-if="config.export && config.export.enabled"
        :icon="'Download'"
        @click="exportData"
      >
        导出
      </el-button>
    </div>

    <!-- ============ 表格 ============ -->
    <el-table
      :data="tableData"
      v-loading="loading"
      :stripe="config.stripe !== false"
      :border="config.border !== false"
      row-key="id"
      :style="{ width: '100%' }"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <!-- 多选列 -->
      <el-table-column
        v-if="config.showSelection"
        type="selection"
        width="50"
        fixed="left"
      />
      <!-- 序号列 -->
      <el-table-column
        v-if="config.showIndex"
        type="index"
        width="50"
        label="序号"
        fixed="left"
        :index="(i: number) => (currentPage - 1) * pageSizeRef + i + 1"
      />
      <!-- 数据列 -->
      <el-table-column
        v-for="col in visibleColumns"
        :key="col.id"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :fixed="resolveFixed(col.fixed)"
        :sortable="col.sortable ? 'custom' : false"
        :align="(col.align as 'left' | 'center' | 'right') || 'left'"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <!-- 图片 -->
          <el-image
            v-if="col.type === ColumnType.IMAGE"
            :src="String(row[col.prop] || '')"
            :style="{ width: (col.imageWidth || 60) + 'px', height: (col.imageHeight || 60) + 'px' }"
            fit="cover"
            :preview-src-list="row[col.prop] ? [String(row[col.prop])] : []"
            preview-teleported
          />
          <!-- 标签 -->
          <el-tag
            v-else-if="col.type === ColumnType.TAG"
            :type="(col.tagType as EpTagType) || 'primary'"
          >
            {{ renderWithFormatter(row, col) }}
          </el-tag>
          <!-- 链接 -->
          <el-link
            v-else-if="col.type === ColumnType.LINK"
            type="primary"
            :underline="false"
            @click="handleLinkClick(col, row)"
          >
            {{ renderWithFormatter(row, col) }}
          </el-link>
          <!-- 自定义（具名插槽 fallback） -->
          <slot
            v-else-if="col.type === ColumnType.CUSTOM"
            :name="col.prop"
            :row="row"
            :column="col"
          >
            {{ renderWithFormatter(row, col) }}
          </slot>
          <!-- 默认文本（含日期/货币/百分比/字典/普通文本） -->
          <span v-else>{{ renderWithFormatter(row, col) }}</span>
        </template>
      </el-table-column>

      <!-- 操作列 -->
      <el-table-column
        v-if="visibleRowOps.length"
        label="操作"
        :width="operationsWidth"
        fixed="right"
      >
        <template #default="scope">
          <el-button
            v-for="op in getVisibleRowOps(tableData[scope.$index] || scope.row)"
            :key="op.id"
            :type="(rowButtonType(op) as EpButtonType)"
            :icon="op.icon"
            size="small"
            link
            v-permission="op.permission"
            @click="handleRowClick(op, tableData[scope.$index] || scope.row, scope.$index, $event)"
          >
            {{ op.label }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- ============ 分页 ============ -->
    <el-pagination
      v-if="config.showPagination !== false"
      v-model:current-page="currentPage"
      v-model:page-size="pageSizeRef"
      :total="total"
      :page-sizes="pageSizes"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 12px; justify-content: flex-end"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.low-code-list-renderer {
  width: 100%;
}

.list-filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin-bottom: 12px;
}

.list-filter-form :deep(.el-form-item) {
  margin-bottom: 8px;
  margin-right: 12px;
}

.list-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
</style>
