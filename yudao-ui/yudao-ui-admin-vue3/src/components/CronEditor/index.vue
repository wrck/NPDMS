<script setup lang="ts">
/**
 * Cron 可视化编辑器（借鉴 Budibase Automation 的定时触发器配置）。
 *
 * <p>编辑 Quartz 6 字段 cron 表达式中的 5 个可配字段（分 / 时 / 日 / 月 / 周），
 * 秒字段固定为 0。每个字段支持 4 种模式：</p>
 * <ul>
 *   <li>EVERY 每（*）</li>
 *   <li>SPECIFIC 指定值（多选，逗号分隔）</li>
 *   <li>INTERVAL 间隔（起始值 + 间隔，如 0/15）</li>
 *   <li>RANGE 范围（起始值 + 结束值，如 9-17）</li>
 * </ul>
 *
 * <p>实时预览生成的 cron 表达式、人类可读描述，以及最近 5 次执行时间
 *（前端自行实现简单解析，不引入 cron-parser 等外部库）。
 * 通过 v-model 与 cron 字符串双向绑定。</p>
 */
import { computed, reactive, watch } from 'vue'

defineOptions({ name: 'CronEditor' })

const props = defineProps<{
  /** Quartz cron 表达式（6 字段：秒 分 时 日 月 周，如 "0 0/5 * * * ?"） */
  modelValue: string
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

// ===================== 字段元信息 =====================

interface FieldMeta {
  key: 'minute' | 'hour' | 'day' | 'month' | 'week'
  label: string
  min: number
  max: number
}

/** 5 个可配字段（秒字段固定 0，不在编辑范围） */
const FIELDS: FieldMeta[] = [
  { key: 'minute', label: '分', min: 0, max: 59 },
  { key: 'hour', label: '时', min: 0, max: 23 },
  { key: 'day', label: '日', min: 1, max: 31 },
  { key: 'month', label: '月', min: 1, max: 12 },
  { key: 'week', label: '周', min: 0, max: 6 }
]

type Mode = 'EVERY' | 'SPECIFIC' | 'INTERVAL' | 'RANGE'

interface FieldConfig {
  mode: Mode
  values: number[] // SPECIFIC 模式选中的值
  start: number // INTERVAL / RANGE 起始
  interval: number // INTERVAL 间隔
  end: number // RANGE 结束
}

/** 周几中文标签（0=周日 … 6=周六） */
const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/** 月份中文标签 */
const MONTH_LABELS = [
  '1 月', '2 月', '3 月', '4 月', '5 月', '6 月',
  '7 月', '8 月', '9 月', '10 月', '11 月', '12 月'
]

// ===================== 字段 <-> cron 片段 =====================

function createEvery(min: number, max: number): FieldConfig {
  return { mode: 'EVERY', values: [], start: min, interval: 1, end: max }
}

/** 解析单个数字，失败返回 null */
function parseNum(s: string, fallback?: number): number | null {
  const n = Number.parseInt(s, 10)
  if (Number.isNaN(n)) return fallback != null ? fallback : null
  return n
}

/** 将单个 cron 片段解析为 FieldConfig，无法识别时降级为 EVERY */
function parseField(token: string, meta: FieldMeta): FieldConfig {
  const t = (token || '').trim()
  if (t === '*' || t === '?') return createEvery(meta.min, meta.max)
  if (t.includes('/')) {
    const [s, i] = t.split('/')
    const start = parseNum(s, meta.min) ?? meta.min
    const interval = parseNum(i, 1) ?? 1
    return { mode: 'INTERVAL', values: [], start, interval: Math.max(1, interval), end: meta.max }
  }
  if (t.includes('-')) {
    const [s, e] = t.split('-')
    const start = parseNum(s, meta.min) ?? meta.min
    const end = parseNum(e, meta.max) ?? meta.max
    return { mode: 'RANGE', values: [], start, interval: 1, end }
  }
  if (t.includes(',')) {
    const values = t
      .split(',')
      .map((x) => parseNum(x, meta.min))
      .filter((x): x is number => x != null)
    return { mode: 'SPECIFIC', values, start: meta.min, interval: 1, end: meta.max }
  }
  const n = parseNum(t, meta.min)
  if (n != null) {
    return { mode: 'SPECIFIC', values: [n], start: meta.min, interval: 1, end: meta.max }
  }
  return createEvery(meta.min, meta.max)
}

/** FieldConfig -> cron 片段 */
function fieldToCron(fc: FieldConfig): string {
  switch (fc.mode) {
    case 'EVERY':
      return '*'
    case 'SPECIFIC':
      return fc.values.length ? [...fc.values].sort((a, b) => a - b).join(',') : '*'
    case 'INTERVAL':
      return `${fc.start}/${fc.interval}`
    case 'RANGE':
      return `${fc.start}-${fc.end}`
    default:
      return '*'
  }
}

/** 字段值是否匹配（用于下次执行时间计算） */
function matchField(fc: FieldConfig, value: number): boolean {
  switch (fc.mode) {
    case 'EVERY':
      return true
    case 'SPECIFIC':
      return fc.values.includes(value)
    case 'INTERVAL':
      return value >= fc.start && (value - fc.start) % fc.interval === 0
    case 'RANGE':
      return value >= fc.start && value <= fc.end
    default:
      return true
  }
}

// ===================== cron 拆分 / 组装（Quartz 6 字段） =====================

/** 拆分 cron，统一为 6 字段（5 字段 unix 自动补秒位 0） */
function splitCron(cron: string): string[] {
  const parts = cron.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 6) return parts
  if (parts.length === 5) return ['0', ...parts]
  throw new Error('cron 表达式必须为 5 或 6 字段')
}

/**
 * 规整 日 / 周 两个字段，使其符合 Quartz 规则（二者之一必须为 ?）。
 *
 * <p>? 表示「不指定」，对应编辑器中的 EVERY。规则：</p>
 * <ul>
 *   <li>都为每（*）→ 日=*，周=?</li>
 *   <li>日为每、周指定 → 日=?，周=指定</li>
 *   <li>日指定、周为每 → 日=指定，周=?</li>
 *   <li>都指定（Quartz 不允许）→ 保留日，周=?</li>
 * </ul>
 */
function normalizeDayWeek(dayStr: string, weekStr: string): { day: string; week: string } {
  const dayEvery = dayStr === '*'
  const weekEvery = weekStr === '*'
  if (dayEvery && weekEvery) return { day: '*', week: '?' }
  if (dayEvery && !weekEvery) return { day: '?', week: weekStr }
  if (!dayEvery && weekEvery) return { day: dayStr, week: '?' }
  return { day: dayStr, week: '?' }
}

/** 由 5 个 FieldConfig 组装完整 Quartz 6 字段 cron */
function compose(f: Record<FieldMeta['key'], FieldConfig>): string {
  const dayRaw = fieldToCron(f.day)
  const weekRaw = fieldToCron(f.week)
  const { day, week } = normalizeDayWeek(dayRaw, weekRaw)
  return `0 ${fieldToCron(f.minute)} ${fieldToCron(f.hour)} ${day} ${fieldToCron(f.month)} ${week}`
}

// ===================== v-model 状态 =====================

function createDefaultFields(): Record<FieldMeta['key'], FieldConfig> {
  return {
    minute: createEvery(0, 59),
    hour: createEvery(0, 23),
    day: createEvery(1, 31),
    month: createEvery(1, 12),
    week: createEvery(0, 6)
  }
}

const fields = reactive<Record<FieldMeta['key'], FieldConfig>>(createDefaultFields())

/** 将 cron 字符串应用到字段状态 */
function applyCron(cronStr: string) {
  let parts: string[]
  try {
    parts = splitCron(cronStr)
  } catch {
    return
  }
  fields.minute = parseField(parts[1], FIELDS[0])
  fields.hour = parseField(parts[2], FIELDS[1])
  fields.day = parseField(parts[3], FIELDS[2])
  fields.month = parseField(parts[4], FIELDS[3])
  fields.week = parseField(parts[5], FIELDS[4])
}

// 初始化 + 外部变更同步
applyCron(props.modelValue)
watch(
  () => props.modelValue,
  (v) => {
    if (v !== cron.value) applyCron(v)
  }
)

const cron = computed(() => compose(fields))
watch(cron, (v) => emit('update:modelValue', v))

/** 模式切换时重置该字段的默认值，避免遗留非法值 */
function onModeChange(meta: FieldMeta, mode: Mode) {
  const fc = fields[meta.key]
  fc.mode = mode
  if (mode === 'SPECIFIC' && fc.values.length === 0) {
    fc.values = [meta.min]
  }
  if (mode === 'INTERVAL') {
    fc.start = meta.min
    fc.interval = 1
  }
  if (mode === 'RANGE') {
    fc.start = meta.min
    fc.end = meta.max
  }
}

/** 手动输入 cron 字符串 */
function onCronInput(val: string) {
  applyCron(val)
}

// ===================== 选项生成 =====================

function optionsFor(meta: FieldMeta): Array<{ label: string; value: number }> {
  const list: Array<{ label: string; value: number }> = []
  for (let v = meta.min; v <= meta.max; v++) {
    let label = String(v)
    if (meta.key === 'week') label = WEEK_LABELS[v] ?? String(v)
    else if (meta.key === 'month') label = MONTH_LABELS[v - 1] ?? String(v)
    list.push({ label, value: v })
  }
  return list
}

// ===================== 人类可读描述 =====================

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function monthDesc(fc: FieldConfig): string {
  switch (fc.mode) {
    case 'EVERY':
      return '每月'
    case 'SPECIFIC':
      return fc.values.map((v) => MONTH_LABELS[v - 1] ?? `${v} 月`).join('、')
    case 'RANGE':
      return `${fc.start}-${fc.end} 月`
    case 'INTERVAL':
      return `从 ${fc.start} 月起每 ${fc.interval} 月`
    default:
      return '每月'
  }
}

/** 生成人类可读描述（覆盖常见组合，非穷尽） */
function describe(cronStr: string): string {
  let parts: string[]
  try {
    parts = splitCron(cronStr)
  } catch {
    return '无法解析的 cron 表达式'
  }
  const m = parseField(parts[1], FIELDS[0])
  const h = parseField(parts[2], FIELDS[1])
  const d = parseField(parts[3], FIELDS[2])
  const mo = parseField(parts[4], FIELDS[3])
  const w = parseField(parts[5], FIELDS[4])

  // 时间描述
  let timeDesc = ''
  if (m.mode === 'EVERY' && h.mode === 'EVERY') {
    timeDesc = '每分钟'
  } else if (m.mode === 'INTERVAL') {
    timeDesc = `每 ${m.interval} 分钟`
  } else if (h.mode === 'INTERVAL') {
    timeDesc = `每 ${h.interval} 小时`
    if (m.mode === 'SPECIFIC') timeDesc += `的 ${m.values.map(pad).join('、')} 分`
  } else if (h.mode === 'SPECIFIC' && m.mode === 'SPECIFIC') {
    timeDesc = h.values.map((hv) => `${pad(hv)}:${pad(m.values[0] ?? 0)}`).join('、')
  } else if (h.mode === 'EVERY' && m.mode === 'SPECIFIC') {
    timeDesc = `每小时的 ${m.values.map(pad).join('、')} 分`
  } else if (h.mode === 'SPECIFIC' && m.mode === 'EVERY') {
    timeDesc = `${h.values.map(pad).join('、')} 点每分钟`
  } else {
    timeDesc = '按设定时间'
  }

  // 日期描述
  let dateDesc = ''
  const dayEvery = d.mode === 'EVERY'
  const weekEvery = w.mode === 'EVERY'
  const monthSuffix = mo.mode === 'EVERY' ? '' : `（${monthDesc(mo)}）`
  if (dayEvery && weekEvery) {
    dateDesc = `每天${monthSuffix}`
  } else if (!dayEvery && weekEvery) {
    if (d.mode === 'SPECIFIC') dateDesc = `每月 ${d.values.join('、')} 日${monthSuffix}`
    else if (d.mode === 'RANGE') dateDesc = `每月 ${d.start}-${d.end} 日${monthSuffix}`
    else if (d.mode === 'INTERVAL') dateDesc = `每月从 ${d.start} 日起每 ${d.interval} 日${monthSuffix}`
    else dateDesc = `每月指定日${monthSuffix}`
  } else if (dayEvery && !weekEvery) {
    if (w.mode === 'SPECIFIC') dateDesc = `每周 ${w.values.map((v) => WEEK_LABELS[v] ?? v).join('、')}${monthSuffix}`
    else if (w.mode === 'RANGE') dateDesc = `每周 ${WEEK_LABELS[w.start] ?? w.start} 至 ${WEEK_LABELS[w.end] ?? w.end}${monthSuffix}`
    else if (w.mode === 'INTERVAL') dateDesc = `每周从 ${WEEK_LABELS[w.start] ?? w.start} 起每 ${w.interval} 天${monthSuffix}`
    else dateDesc = `每周指定日${monthSuffix}`
  } else {
    dateDesc = `按设定日期${monthSuffix}`
  }

  return `${dateDesc} ${timeDesc} 执行`.replace(/\s+/g, ' ').trim()
}

const description = computed(() => describe(cron.value))

// ===================== 下次执行时间（前端简单计算） =====================

function formatDateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 计算最近 count 次执行时间。
 *
 * <p>从「下一分钟」开始按分钟向前扫描，逐个时间点匹配 5 个字段。
 * 扫描上限为一年内的分钟数，足以覆盖月/周级别的低频任务。</p>
 */
function getNextExecutionTimes(cronStr: string, count = 5, from = new Date()): string[] {
  let parts: string[]
  try {
    parts = splitCron(cronStr)
  } catch {
    return []
  }
  const m = parseField(parts[1], FIELDS[0])
  const h = parseField(parts[2], FIELDS[1])
  const d = parseField(parts[3], FIELDS[2])
  const mo = parseField(parts[4], FIELDS[3])
  const w = parseField(parts[5], FIELDS[4])

  const result: string[] = []
  const start = new Date(from.getTime())
  start.setSeconds(0, 0)
  start.setMinutes(start.getMinutes() + 1)
  const maxIter = 366 * 24 * 60
  for (let i = 0; i < maxIter && result.length < count; i++) {
    const ts = new Date(start.getTime() + i * 60 * 1000)
    if (
      matchField(mo, ts.getMonth() + 1) &&
      matchField(d, ts.getDate()) &&
      matchField(w, ts.getDay()) &&
      matchField(h, ts.getHours()) &&
      matchField(m, ts.getMinutes())
    ) {
      result.push(formatDateTime(ts))
    }
  }
  return result
}

const nextTimes = computed(() => getNextExecutionTimes(cron.value, 5))
</script>

<template>
  <div class="cron-editor">
    <!-- 5 字段编辑区 -->
    <div class="ce-fields">
      <div v-for="meta in FIELDS" :key="meta.key" class="ce-field-row">
        <div class="ce-field-label">{{ meta.label }}</div>
        <el-radio-group
          :model-value="fields[meta.key].mode"
          size="small"
          @update:model-value="onModeChange(meta, $event as Mode)"
        >
          <el-radio-button value="EVERY">每</el-radio-button>
          <el-radio-button value="SPECIFIC">指定</el-radio-button>
          <el-radio-button value="INTERVAL">间隔</el-radio-button>
          <el-radio-button value="RANGE">范围</el-radio-button>
        </el-radio-group>

        <div class="ce-field-control">
          <!-- EVERY -->
          <span v-if="fields[meta.key].mode === 'EVERY'" class="ce-hint">每{{ meta.label }}</span>

          <!-- SPECIFIC -->
          <el-select
            v-else-if="fields[meta.key].mode === 'SPECIFIC'"
            v-model="fields[meta.key].values"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择值"
            style="min-width: 200px"
          >
            <el-option v-for="o in optionsFor(meta)" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>

          <!-- INTERVAL -->
          <template v-else-if="fields[meta.key].mode === 'INTERVAL'">
            <el-input-number v-model="fields[meta.key].start" :min="meta.min" :max="meta.max" size="small" />
            <span class="ce-inline-text">起，每</span>
            <el-input-number v-model="fields[meta.key].interval" :min="1" :max="meta.max" size="small" />
            <span class="ce-inline-text">{{ meta.label }}</span>
          </template>

          <!-- RANGE -->
          <template v-else-if="fields[meta.key].mode === 'RANGE'">
            <el-input-number v-model="fields[meta.key].start" :min="meta.min" :max="meta.max" size="small" />
            <span class="ce-inline-text">至</span>
            <el-input-number v-model="fields[meta.key].end" :min="meta.min" :max="meta.max" size="small" />
            <span class="ce-inline-text">{{ meta.label }}</span>
          </template>
        </div>
      </div>
    </div>

    <!-- 预览区 -->
    <div class="ce-preview">
      <el-form label-width="80px" label-position="left">
        <el-form-item label="表达式">
          <el-input :model-value="cron" style="font-family: monospace" @update:model-value="onCronInput">
            <template #append>
              <span style="color: var(--el-text-color-secondary)">秒固定 0</span>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="描述">
          <span class="ce-desc">{{ description }}</span>
        </el-form-item>
        <el-form-item label="下次执行">
          <div v-if="nextTimes.length" class="ce-next-list">
            <span v-for="(t, i) in nextTimes" :key="i" class="ce-next-item">{{ t }}</span>
          </div>
          <span v-else class="ce-empty">一年内无可执行时间，请检查表达式</span>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.cron-editor {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
}

.ce-fields {
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.ce-field-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  flex-wrap: wrap;
}

.ce-field-label {
  width: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  flex-shrink: 0;
}

.ce-field-control {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.ce-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.ce-inline-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.ce-preview {
  padding: 12px;
  background: var(--el-fill-color-light);
}

.ce-desc {
  font-size: 14px;
  color: var(--el-color-primary);
  font-weight: 500;
}

.ce-next-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ce-next-item {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 3px;
}

.ce-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
