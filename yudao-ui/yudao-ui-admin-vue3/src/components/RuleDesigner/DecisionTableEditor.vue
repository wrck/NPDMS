<script setup lang="ts">
/**
 * 决策表可视化编辑器（借鉴 Appian Decision Designer）。
 *
 * <p>以表格形式编辑决策表：左侧条件列（字段 + 操作符 + 值），右侧动作列（字段 + 值），
 * 中间分隔线。支持 Hit Policy 切换、增删条件/动作列、增删/上下移动规则行，
 * 单元格直接可编辑，Tab 键自然在单元格间切换。</p>
 *
 * <p>definition 采用新结构化格式：
 * <pre>
 * {hitPolicy, conditionColumns:[{field, operator}], actionColumns:[{field}],
 *  rows:[{conditions:[{value}], actions:[{value}]}]}
 * </pre>
 * 加载时若检测到旧格式（rows 内联 conditions/actions）会自动转换，保证向后兼容。</p>
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  type ActionColumn,
  type ConditionColumn,
  type DecisionOperator,
  type DecisionTable,
  type HitPolicy
} from '@/api/lowcode-rule'

defineOptions({ name: 'DecisionTableEditor' })

/** 有效的操作符集合，CSV 表头解析时校验 */
const VALID_OPERATORS = new Set<DecisionOperator>(['EQ', 'NE', 'GT', 'GE', 'LT', 'LE', 'IN'])

/** CSV 文件输入引用 */
const csvFileInput = ref<HTMLInputElement | null>(null)

const props = defineProps<{
  /** 决策表 definition JSON 字符串（v-model） */
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()

/** 操作符选项 */
const operatorOptions: { label: string; value: DecisionOperator }[] = [
  { label: 'EQ 等于', value: 'EQ' },
  { label: 'NE 不等于', value: 'NE' },
  { label: 'GT 大于', value: 'GT' },
  { label: 'GE 大于等于', value: 'GE' },
  { label: 'LT 小于', value: 'LT' },
  { label: 'LE 小于等于', value: 'LE' },
  { label: 'IN 包含于', value: 'IN' }
]

/** 命中策略选项 */
const hitPolicyOptions: { label: string; value: HitPolicy }[] = [
  { label: 'FIRST 匹配首行', value: 'FIRST' },
  { label: 'ALL 匹配全部', value: 'ALL' },
  { label: 'COLLECT 收集全部', value: 'COLLECT' }
]

/** 内部可编辑结构 */
const table = reactive<DecisionTable>({
  hitPolicy: 'FIRST',
  conditionColumns: [],
  actionColumns: [],
  rows: []
})

/** 标记是否正在从外部同步，避免回写触发循环 */
let syncing = false

/**
 * 将 definition 字符串解析为结构化对象。
 * 兼容新格式（含 conditionColumns/hitPolicy）与旧格式（rows 内联 conditions/actions）。
 */
function parseDefinition(raw: string): DecisionTable {
  const result: DecisionTable = {
    hitPolicy: 'FIRST',
    conditionColumns: [],
    actionColumns: [],
    rows: []
  }
  if (!raw || !raw.trim()) return result
  let obj: Record<string, unknown>
  try {
    obj = JSON.parse(raw)
  } catch {
    return result
  }
  // 新格式：含 conditionColumns 或 hitPolicy
  if (obj.hitPolicy || obj.conditionColumns) {
    result.hitPolicy = (obj.hitPolicy as HitPolicy) || 'FIRST'
    result.conditionColumns = ((obj.conditionColumns as ConditionColumn[]) || []).map((c) => ({
      field: c.field || '',
      operator: c.operator || 'EQ'
    }))
    result.actionColumns = ((obj.actionColumns as ActionColumn[]) || []).map((a) => ({
      field: a.field || ''
    }))
    result.rows = ((obj.rows as DecisionTable['rows']) || []).map((r) => ({
      conditions: normalizeCells(r.conditions, result.conditionColumns.length),
      actions: normalizeCells(r.actions, result.actionColumns.length)
    }))
    return result
  }
  // 旧格式：rows:[{conditions:[{field,operator,value}], actions:{field,value}}]
  const rows = (obj.rows as Record<string, unknown>[]) || []
  if (rows.length === 0) return result
  // 由首行推导列结构
  const firstConds = (rows[0].conditions as Array<Record<string, unknown>>) || []
  result.conditionColumns = firstConds.map((c) => ({
    field: (c.field as string) || '',
    operator: (c.operator as DecisionOperator) || 'EQ'
  }))
  const firstActions = (rows[0].actions as Record<string, unknown>) || {}
  result.actionColumns = Object.keys(firstActions).map((field) => ({ field }))
  result.rows = rows.map((r) => {
    const conds = (r.conditions as Array<Record<string, unknown>>) || []
    const acts = (r.actions as Record<string, unknown>) || {}
    return {
      conditions: result.conditionColumns.map((_, i) => ({ value: conds[i]?.value ?? '' })),
      actions: result.actionColumns.map((c) => ({ value: acts[c.field] ?? '' }))
    }
  })
  return result
}

/** 规范化单元格数组，保证长度与列数一致 */
function normalizeCells(
  cells: { value: unknown }[] | undefined,
  len: number
): { value: unknown }[] {
  const arr = (cells || []).slice(0, len)
  while (arr.length < len) arr.push({ value: '' })
  return arr
}

/** 同步外部字符串到内部结构 */
function syncFromModel(raw: string) {
  syncing = true
  const parsed = parseDefinition(raw)
  table.hitPolicy = parsed.hitPolicy
  table.conditionColumns.splice(0, table.conditionColumns.length, ...parsed.conditionColumns)
  table.actionColumns.splice(0, table.actionColumns.length, ...parsed.actionColumns)
  table.rows.splice(0, table.rows.length, ...parsed.rows)
  syncing = false
}

watch(
  () => props.modelValue,
  (v) => {
    if (syncing) return
    syncFromModel(v)
  },
  { immediate: true }
)

/** 单元格值 → 显示文本 */
function toText(v: unknown): string {
  if (v === null || v === undefined) return ''
  if (typeof v === 'string') return v
  return JSON.stringify(v)
}

/** 显示文本 → 存储值（尝试 JSON 解析以保留数字/布尔/数组类型） */
function fromText(text: string): unknown {
  const s = text.trim()
  if (s === '') return ''
  try {
    return JSON.parse(s)
  } catch {
    return text
  }
}

/** 序列化内部结构为 definition JSON 字符串并回写 */
function emitChange() {
  if (syncing) return
  const payload: DecisionTable = {
    hitPolicy: table.hitPolicy,
    conditionColumns: table.conditionColumns.map((c) => ({ field: c.field, operator: c.operator })),
    actionColumns: table.actionColumns.map((a) => ({ field: a.field })),
    rows: table.rows.map((r) => ({
      conditions: r.conditions.map((c) => ({ value: c.value })),
      actions: r.actions.map((a) => ({ value: a.value }))
    }))
  }
  emit('update:modelValue', JSON.stringify(payload, null, 2))
}

/** 深度监听内部结构变化，自动回写 */
watch(table, emitChange, { deep: true })

// ===================== 列操作 =====================

function addConditionColumn() {
  table.conditionColumns.push({ field: '', operator: 'EQ' })
  table.rows.forEach((r) => r.conditions.push({ value: '' }))
  emitChange()
}

function addActionColumn() {
  table.actionColumns.push({ field: '' })
  table.rows.forEach((r) => r.actions.push({ value: '' }))
  emitChange()
}

function removeConditionColumn(idx: number) {
  table.conditionColumns.splice(idx, 1)
  table.rows.forEach((r) => r.conditions.splice(idx, 1))
  emitChange()
}

function removeActionColumn(idx: number) {
  table.actionColumns.splice(idx, 1)
  table.rows.forEach((r) => r.actions.splice(idx, 1))
  emitChange()
}

// ===================== 行操作 =====================

function addRow() {
  table.rows.push({
    conditions: table.conditionColumns.map(() => ({ value: '' })),
    actions: table.actionColumns.map(() => ({ value: '' }))
  })
  emitChange()
}

function removeRow(idx: number) {
  table.rows.splice(idx, 1)
  emitChange()
}

function moveRow(idx: number, delta: number) {
  const target = idx + delta
  if (target < 0 || target >= table.rows.length) return
  const tmp = table.rows[idx]
  table.rows[idx] = table.rows[target]
  table.rows[target] = tmp
  emitChange()
}

// ===================== CSV 导入导出 =====================

/**
 * 单元格值序列化为 CSV 文本：
 * - 数字/布尔/字符串原样输出；
 * - 对象/数组用 JSON 字符串表示；
 * - 含逗号、双引号或换行的值用双引号包裹，内部双引号转义为两个双引号。
 */
function toCsvCell(v: unknown): string {
  let text: string
  if (v === null || v === undefined) {
    text = ''
  } else if (typeof v === 'string') {
    text = v
  } else {
    try {
      text = JSON.stringify(v)
    } catch {
      text = String(v)
    }
  }
  if (/[",\r\n]/.test(text)) {
    return '"' + text.replace(/"/g, '""') + '"'
  }
  return text
}

/** 将决策表结构序列化为 CSV 字符串（含表头行 + 数据行） */
function buildCsv(): string {
  const headerCells: string[] = []
  table.conditionColumns.forEach((c) => {
    // 条件列表头格式：条件:字段名(操作符)
    headerCells.push(toCsvCell(`条件:${c.field || ''}(${c.operator || 'EQ'})`))
  })
  table.actionColumns.forEach((a) => {
    // 动作列表头格式：动作:字段名
    headerCells.push(toCsvCell(`动作:${a.field || ''}`))
  })
  const lines: string[] = [headerCells.join(',')]
  table.rows.forEach((row) => {
    const cells: string[] = []
    row.conditions.forEach((c) => cells.push(toCsvCell(c.value)))
    row.actions.forEach((a) => cells.push(toCsvCell(a.value)))
    lines.push(cells.join(','))
  })
  // 末尾换行（兼容 Excel 等表格软件）
  return lines.join('\r\n') + '\r\n'
}

/** 解析一行 CSV 文本为单元格数组，支持双引号转义 */
function parseCsvLine(line: string): string[] {
  const cells: string[] = []
  let i = 0
  const len = line.length
  while (i <= len) {
    if (i === len) {
      // 行尾空单元格
      cells.push('')
      break
    }
    if (line[i] === '"') {
      // 引号包裹的字段
      let buf = ''
      i++ // 跳过起始引号
      while (i < len) {
        if (line[i] === '"') {
          if (i + 1 < len && line[i + 1] === '"') {
            // 转义的双引号
            buf += '"'
            i += 2
          } else {
            // 字段结束引号
            i++
            break
          }
        } else {
          buf += line[i]
          i++
        }
      }
      cells.push(buf)
      // 跳过到下一个逗号或行尾
      while (i < len && line[i] !== ',') i++
      if (i < len && line[i] === ',') i++
      else if (i >= len) break
    } else {
      // 普通字段（不含引号）
      let j = i
      while (j < len && line[j] !== ',') j++
      cells.push(line.slice(i, j))
      i = j
      if (i < len && line[i] === ',') i++
      else break
    }
  }
  return cells
}

/**
 * 将 CSV 文本拆分为行数组，处理引号内的换行（CRLF/LF）。
 * 仅在引号未闭合时跨行合并。
 */
function splitCsvRows(text: string): string[] {
  // 统一换行符为 \n
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const rows: string[] = []
  let buf = ''
  let inQuotes = false
  for (let i = 0; i < normalized.length; i++) {
    const ch = normalized[i]
    if (ch === '"') {
      // 判断是否为转义双引号
      if (inQuotes && normalized[i + 1] === '"') {
        buf += '""'
        i++
      } else {
        inQuotes = !inQuotes
        buf += ch
      }
    } else if (ch === '\n' && !inQuotes) {
      rows.push(buf)
      buf = ''
    } else {
      buf += ch
    }
  }
  if (buf.length > 0) rows.push(buf)
  // 过滤尾部空行
  while (rows.length > 0 && rows[rows.length - 1].trim() === '') rows.pop()
  return rows
}

/** 尝试把字符串解析为强类型值（数字/布尔/JSON），失败则返回原字符串 */
function parseCellText(text: string): unknown {
  const s = text.trim()
  if (s === '') return ''
  try {
    return JSON.parse(s)
  } catch {
    return text
  }
}

/**
 * 解析 CSV 文本为决策表结构。
 * 表头格式：`条件:字段名(操作符)` 或 `动作:字段名`，不区分的列视为动作列。
 * 解析失败抛出 Error，由调用方捕获并提示。
 */
function parseCsv(text: string): DecisionTable {
  const rows = splitCsvRows(text)
  if (rows.length === 0) {
    throw new Error('CSV 内容为空')
  }
  const headerCells = parseCsvLine(rows[0]).map((c) => c.trim())
  const conditionColumns: ConditionColumn[] = []
  const actionColumns: ActionColumn[] = []
  const colTypes: ('cond' | 'act')[] = []
  headerCells.forEach((h) => {
    // 条件列：条件:field(op)
    const condMatch = h.match(/^条件\s*:\s*(.*?)\s*\(\s*([A-Za-z]+)\s*\)\s*$/)
    if (condMatch) {
      const op = condMatch[2].toUpperCase() as DecisionOperator
      conditionColumns.push({
        field: condMatch[1] || '',
        operator: VALID_OPERATORS.has(op) ? op : 'EQ'
      })
      colTypes.push('cond')
      return
    }
    // 动作列：动作:field 或 纯字段名
    const actMatch = h.match(/^动作\s*:\s*(.*)$/)
    if (actMatch) {
      actionColumns.push({ field: actMatch[1] || '' })
    } else {
      // 未识别的列当作动作列（保留原始文本作为字段名）
      actionColumns.push({ field: h })
    }
    colTypes.push('act')
  })
  const dataRows: DecisionTable['rows'] = rows.slice(1).map((line) => {
    const cells = parseCsvLine(line)
    const conditions: { value: unknown }[] = []
    const actions: { value: unknown }[] = []
    colTypes.forEach((t, idx) => {
      const raw = cells[idx] ?? ''
      const value = parseCellText(raw)
      if (t === 'cond') conditions.push({ value })
      else actions.push({ value })
    })
    // 容错：列数与表头不一致时补齐
    while (conditions.length < conditionColumns.length) conditions.push({ value: '' })
    while (actions.length < actionColumns.length) actions.push({ value: '' })
    return { conditions, actions }
  })
  return { hitPolicy: table.hitPolicy, conditionColumns, actionColumns, rows: dataRows }
}

/** 触发 CSV 下载（保留当前 hitPolicy） */
function exportCsv() {
  if (table.conditionColumns.length === 0 && table.actionColumns.length === 0) {
    ElMessage.warning('当前决策表无列，无法导出')
    return
  }
  const csv = buildCsv()
  // 加 BOM 以便 Excel 正确识别 UTF-8
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `decision-table-${Date.now()}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 触发文件选择器 */
function onImportCsvClick() {
  csvFileInput.value?.click()
}

/** 读取 CSV 文件并解析回填到决策表 */
async function onImportCsvFile(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    const parsed = parseCsv(text)
    syncing = true
    table.conditionColumns.splice(0, table.conditionColumns.length, ...parsed.conditionColumns)
    table.actionColumns.splice(0, table.actionColumns.length, ...parsed.actionColumns)
    table.rows.splice(0, table.rows.length, ...parsed.rows)
    syncing = false
    emitChange()
    ElMessage.success(`已导入 ${parsed.rows.length} 行规则`)
  } catch (err) {
    ElMessage.error('CSV 解析失败：' + (err instanceof Error ? err.message : String(err)))
  } finally {
    // 重置 value 以便重复选择同一文件可再次触发 change
    target.value = ''
  }
}
</script>

<template>
  <div class="decision-table-editor">
    <!-- 顶部工具栏 -->
    <div class="dte-toolbar">
      <span class="dte-toolbar-label">命中策略</span>
      <el-select v-model="table.hitPolicy" size="small" style="width: 180px">
        <el-option
          v-for="o in hitPolicyOptions"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <el-button size="small" @click="addConditionColumn">+ 条件列</el-button>
      <el-button size="small" @click="addActionColumn">+ 动作列</el-button>
      <el-button size="small" type="primary" @click="addRow">+ 规则行</el-button>
      <el-button size="small" @click="onImportCsvClick">导入 CSV</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <input
        ref="csvFileInput"
        type="file"
        accept=".csv,text/csv"
        style="display: none"
        @change="onImportCsvFile"
      />
    </div>

    <!-- 决策表 -->
    <div class="dte-table-wrap">
      <table v-if="table.conditionColumns.length || table.actionColumns.length" class="dte-table">
        <thead>
          <!-- 分组表头：条件 / 动作 -->
          <tr>
            <th rowspan="2" class="dte-row-head">#</th>
            <th
              v-if="table.conditionColumns.length"
              :colspan="table.conditionColumns.length"
              class="dte-group dte-group-cond"
            >
              条件 (Conditions)
            </th>
            <th v-if="table.conditionColumns.length" class="dte-divider" rowspan="2"></th>
            <th
              v-if="table.actionColumns.length"
              :colspan="table.actionColumns.length"
              class="dte-group dte-group-act"
            >
              动作 (Actions)
            </th>
          </tr>
          <!-- 列表头：字段名 + 操作符 -->
          <tr>
            <th
              v-for="(c, ci) in table.conditionColumns"
              :key="'c' + ci"
              class="dte-col-head dte-col-cond"
            >
              <div class="dte-col-head-inner">
                <el-input v-model="c.field" size="small" placeholder="字段名" />
                <el-select v-model="c.operator" size="small" class="dte-op-select">
                  <el-option
                    v-for="o in operatorOptions"
                    :key="o.value"
                    :label="o.value"
                    :value="o.value"
                  />
                </el-select>
                <el-button
                  size="small"
                  link
                  class="dte-col-del"
                  title="删除列"
                  @click="removeConditionColumn(ci)"
                >✕</el-button>
              </div>
            </th>
            <th
              v-for="(a, ai) in table.actionColumns"
              :key="'a' + ai"
              class="dte-col-head dte-col-act"
            >
              <div class="dte-col-head-inner">
                <el-input v-model="a.field" size="small" placeholder="字段名" />
                <el-button
                  size="small"
                  link
                  class="dte-col-del"
                  title="删除列"
                  @click="removeActionColumn(ai)"
                >✕</el-button>
              </div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in table.rows" :key="'r' + ri">
            <td class="dte-row-head">
              <span class="dte-row-idx">{{ ri + 1 }}</span>
              <div class="dte-row-ops">
                <el-button
                  size="small"
                  link
                  :disabled="ri === 0"
                  title="上移"
                  @click="moveRow(ri, -1)"
                >▲</el-button>
                <el-button
                  size="small"
                  link
                  :disabled="ri === table.rows.length - 1"
                  title="下移"
                  @click="moveRow(ri, 1)"
                >▼</el-button>
                <el-button
                  size="small"
                  link
                  class="dte-row-del"
                  title="删除行"
                  @click="removeRow(ri)"
                >✕</el-button>
              </div>
            </td>
            <td v-for="(_, ci) in table.conditionColumns" :key="'rc' + ci" class="dte-cell">
              <el-input
                :model-value="toText(row.conditions[ci]?.value)"
                size="small"
                placeholder="值"
                @update:model-value="(v: string) => (row.conditions[ci].value = fromText(v))"
              />
            </td>
            <td class="dte-divider-cell"></td>
            <td v-for="(_, ai) in table.actionColumns" :key="'ra' + ai" class="dte-cell">
              <el-input
                :model-value="toText(row.actions[ai]?.value)"
                size="small"
                placeholder="值"
                @update:model-value="(v: string) => (row.actions[ai].value = fromText(v))"
              />
            </td>
          </tr>
          <tr v-if="table.rows.length === 0">
            <td :colspan="table.conditionColumns.length + table.actionColumns.length + 2" class="dte-empty">
              暂无规则行，点击「+ 规则行」添加
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="dte-placeholder">
        <p>暂无列，请先添加条件列或动作列</p>
        <div>
          <el-button size="small" @click="addConditionColumn">+ 条件列</el-button>
          <el-button size="small" @click="addActionColumn">+ 动作列</el-button>
        </div>
      </div>
    </div>

    <!-- 操作符提示 -->
    <div class="dte-tips">
      <span>提示：</span>
      <span>条件值支持数字(18)、字符串(CN)、布尔(true)、数组(IN 操作符用 [1,2,3])；</span>
      <span>Tab 键可在单元格间切换。</span>
    </div>
  </div>
</template>

<style scoped>
.decision-table-editor {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
}

.dte-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
  flex-wrap: wrap;
}

.dte-toolbar-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.dte-table-wrap {
  overflow: auto;
  max-height: 420px;
}

.dte-table {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}

.dte-table th,
.dte-table td {
  border: 1px solid var(--el-border-color-lighter);
  padding: 4px 6px;
  vertical-align: middle;
}

.dte-group {
  font-weight: 600;
  font-size: 12px;
  text-align: center;
  padding: 4px;
}

.dte-group-cond {
  background: #ecf5ff;
  color: var(--el-color-primary);
}

.dte-group-act {
  background: #f0f9eb;
  color: var(--el-color-success);
}

.dte-divider {
  width: 6px;
  padding: 0;
  background: var(--el-border-color);
  border-left: 2px solid var(--el-color-primary) !important;
  border-right: 2px solid var(--el-color-success) !important;
}

.dte-divider-cell {
  width: 6px;
  padding: 0;
  background: var(--el-border-color-light);
}

.dte-row-head {
  width: 56px;
  text-align: center;
  background: var(--el-fill-color-light);
  position: sticky;
  left: 0;
  z-index: 1;
}

.dte-row-idx {
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.dte-row-ops {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}

.dte-row-ops .el-button {
  height: 16px;
  padding: 0;
  margin: 0;
  font-size: 11px;
}

.dte-row-del {
  color: var(--el-color-danger);
}

.dte-col-head {
  background: var(--el-fill-color-blank);
  min-width: 150px;
}

.dte-col-head.dte-col-cond {
  background: #fafcff;
}

.dte-col-head.dte-col-act {
  background: #fafff6;
}

.dte-col-head-inner {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dte-op-select {
  width: 80px;
  flex-shrink: 0;
}

.dte-col-del {
  color: var(--el-color-danger);
  flex-shrink: 0;
}

.dte-cell {
  background: var(--el-bg-color);
}

.dte-empty {
  text-align: center;
  color: var(--el-text-color-placeholder);
  padding: 20px;
  font-size: 12px;
}

.dte-placeholder {
  padding: 40px 20px;
  text-align: center;
  color: var(--el-text-color-placeholder);
}

.dte-placeholder p {
  margin-bottom: 12px;
  font-size: 13px;
}

.dte-tips {
  padding: 6px 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  background: var(--el-fill-color-light);
}
</style>
