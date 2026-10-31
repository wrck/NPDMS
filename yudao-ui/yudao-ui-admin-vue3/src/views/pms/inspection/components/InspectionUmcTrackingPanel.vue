<template>
  <section aria-label="待办清单跟踪信息" class="tracking-panel">
    <el-empty v-if="!task.issues.length" description="当前没有跟踪事项" :image-size="64" />
    <details v-for="row in task.issues" :key="String(row.id)" class="tracking-item" open>
      <summary class="tracking-heading">
        <h3>{{
          trackedResult(row)
            ? trackedResult(row)?.ruleName || '未提供巡检项目名称'
            : '巡检项目来源缺失'
        }}</h3>
        <el-tag :type="row.statusCode === 'CLOSED' ? 'success' : 'warning'">{{
          statusLabel(row.statusCode)
        }}</el-tag>
      </summary>
      <div class="tracking-body">
        <div class="tracking-context">
          <template v-if="trackedResult(row)">
            <p class="tracking-description"
              ><strong>判定说明：</strong
              >{{ trackedResult(row)?.detailMessage || '未提供判定说明' }}</p
            >
          </template>
          <p v-else class="help-text"
            >未找到此待办关联的唯一巡检判定结果，无法显示项目和判定说明。</p
          >
          <p class="tracking-description"
            ><strong>确认跟踪原因：</strong>{{ row.description || '未提供' }}</p
          >
          <dl class="tracking-meta">
            <template v-if="trackedResult(row)">
              <dt>巡检设备</dt><dd>{{ trackedDevice(row) }}</dd>
            </template>
            <dt>责任人</dt><dd>{{ row.responsibleName || row.responsibleUserId || '未提供' }}</dd>
            <dt>计划完成</dt><dd>{{ row.dueAt ? formatDate(row.dueAt) : '未提供' }}</dd>
          </dl>
        </div>
        <div class="tracking-feedback">
          <el-form label-position="top" :disabled="disabled || !editable(row)">
            <el-form-item label="是否闭环">
              <el-radio-group v-model="draftFor(row).closed" :aria-label="`问题${row.id}是否闭环`">
                <el-radio :value="true">已闭环</el-radio>
                <el-radio :value="false">未闭环</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="情况备注">
              <el-input
                v-model="draftFor(row).remark"
                type="textarea"
                :rows="3"
                maxlength="8000"
                show-word-limit
                :aria-label="`问题${row.id}情况备注`"
              />
            </el-form-item>
          </el-form>
          <p v-if="editable(row)" class="help-text">
            {{
              row.statusCode === 'CLOSURE_PENDING'
                ? '保存已闭环将确认闭环，保存未闭环将退回申请。'
                : '未闭环时可选填情况备注；已闭环提交闭环申请，须有有效证据和闭环说明，并由有权限的人员确认。'
            }}
          </p>
          <p v-else-if="row.statusCode === 'CLOSURE_PENDING'" class="help-text"
            >闭环申请待确认，当前实际状态仍为未闭环。</p
          >
          <p v-if="draftFor(row).stale" class="help-text"
            >本项已被更新，原输入已保留。请核对处理历史后放弃本项修改并读取最新信息。</p
          >
          <div class="tracking-actions">
            <el-button v-if="draftFor(row).stale" :disabled="disabled" @click="reset(row)"
              >放弃本项修改并读取最新</el-button
            >
            <el-button
              v-if="editable(row)"
              type="primary"
              :disabled="disabled || !!draftFor(row).stale || !draftFor(row).remark.trim()"
              @click="save(row)"
              >{{
                row.statusCode === 'CLOSURE_PENDING'
                  ? '保存确认结果'
                  : draftFor(row).closed
                    ? '提交闭环申请'
                    : '保存跟踪信息'
              }}</el-button
            >
            <el-button v-if="canQuery" :disabled="disabled" @click="emit('open', row.id)"
              >处理证据与历史</el-button
            >
          </div>
        </div>
      </div>
    </details>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, watch } from 'vue'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import type { IssueView, TaskDetail, WireLong } from '@/api/pms/inspection/tasks'

const props = defineProps<{ task: TaskDetail; actorId: WireLong; disabled: boolean }>()
type Action = 'record-process' | 'request-closure' | 'confirm-closure' | 'return-closure'
const emit = defineEmits<{
  save: [
    input: {
      issueId: WireLong
      version: WireLong
      action: Action
      data: { content?: string; description?: string }
    }
  ]
  open: [id: WireLong]
  draftChanged: [dirty: boolean]
}>()
type Draft = {
  version: string
  closed: boolean
  remark: string
  baseline: string
  submitted?: string
  stale?: boolean
}
const drafts = reactive<Record<string, Draft>>({})
const canQuery = computed(() => checkPermi(['pms:service-issue:query']))
const sameId = (a: unknown, b: unknown) => a != null && b != null && String(a) === String(b)
function trackedResult(row: IssueView) {
  const matches = (props.task.results || []).filter((result) => sameId(result.id, row.resultId))
  return matches.length === 1 ? matches[0] : undefined
}
function trackedDevice(row: IssueView) {
  const result = trackedResult(row)
  const device = (props.task.devices || []).find((item) => sameId(item.deviceId, result?.deviceId))
  return device?.serialNumber || device?.deviceName || '设备来源未提供'
}
const statusLabel = (code: string) =>
  ({ PENDING: '待处理', PROCESSING: '处理中', CLOSURE_PENDING: '待确认闭环', CLOSED: '已闭环' })[
    code
  ] || code
function latestRemark(row: IssueView) {
  return (
    props.task.remediations
      .filter((note) => sameId(note.issueId, row.id))
      .slice()
      .sort((a, b) => {
        const time = Number(b.actionAt || 0) - Number(a.actionAt || 0)
        return time || (BigInt(String(b.id)) > BigInt(String(a.id)) ? 1 : -1)
      })[0]?.content ||
    row.closureDescription ||
    ''
  )
}
function editable(row: IssueView) {
  if (props.task.statusCode === 'ARCHIVED' || !canQuery.value) return false
  if (row.statusCode === 'CLOSURE_PENDING')
    return (
      (props.task.manager || sameId(props.task.ownerUserId, props.actorId)) &&
      checkPermi(['pms:service-issue:confirm'])
    )
  return (
    ['PENDING', 'PROCESSING'].includes(row.statusCode) &&
    sameId(row.responsibleUserId, props.actorId) &&
    checkPermi(['pms:service-issue:remediate'])
  )
}
function signature(draft: Draft) {
  return JSON.stringify([draft.closed, draft.remark])
}
function reset(row: IssueView) {
  const closed = row.statusCode === 'CLOSED',
    remark = latestRemark(row)
  drafts[String(row.id)] = {
    version: String(row.version),
    closed,
    remark,
    baseline: JSON.stringify([closed, remark])
  }
}
function discardDrafts() {
  props.task.issues.forEach(reset)
}
defineExpose({ discardDrafts })
function draftFor(row: IssueView) {
  return drafts[String(row.id)]
}
watch(
  () => [props.task.id, props.task.issues, props.task.remediations] as const,
  (_value, old) => {
    const task = props.task
    if (!sameId(task.id, old?.[0])) Object.keys(drafts).forEach((key) => delete drafts[key])
    const keys = new Set(task.issues.map((row) => String(row.id)))
    Object.keys(drafts)
      .filter((key) => !keys.has(key))
      .forEach((key) => delete drafts[key])
    task.issues.forEach((row) => {
      const key = String(row.id)
      if (drafts[key]?.version === String(row.version)) return
      const draft = drafts[key]
      if (draft && signature(draft) !== draft.baseline && draft.submitted !== signature(draft)) {
        draft.stale = true
        return
      }
      reset(row)
    })
  },
  { immediate: true, deep: true }
)
const dirty = computed(() =>
  Object.values(drafts).some(
    (draft) => JSON.stringify([draft.closed, draft.remark]) !== draft.baseline
  )
)
watch(dirty, (value) => emit('draftChanged', value), { immediate: true, flush: 'sync' })
function save(row: IssueView) {
  const draft = draftFor(row)
  if (props.disabled || !editable(row) || draft.stale || !draft.remark.trim()) return
  const action: Action =
    row.statusCode === 'CLOSURE_PENDING'
      ? draft.closed
        ? 'confirm-closure'
        : 'return-closure'
      : draft.closed
        ? 'request-closure'
        : 'record-process'
  draft.submitted = signature(draft)
  emit('save', {
    issueId: row.id,
    version: draft.version,
    action,
    data:
      action === 'record-process'
        ? { content: draft.remark.trim() }
        : { description: draft.remark.trim() }
  })
}
onBeforeUnmount(() => emit('draftChanged', false))
</script>

<style scoped>
.tracking-panel {
  --tracking-border: #f0f2f5;
  --tracking-muted: #606266;
  display: grid;
  gap: 16px;
  color: #333;
  font-size: 13px;
}

.tracking-item {
  min-width: 0;
  border: 1px solid var(--tracking-border);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.tracking-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  padding: 12px 20px;
  cursor: pointer;
  list-style: none;
}

.tracking-item[open] > .tracking-heading {
  border-bottom: 1px solid var(--tracking-border);
}

.tracking-heading::-webkit-details-marker {
  display: none;
}

.tracking-heading::after {
  content: '';
  flex: 0 0 7px;
  width: 7px;
  height: 7px;
  border-right: 2px solid var(--el-text-color-secondary);
  border-bottom: 2px solid var(--el-text-color-secondary);
  transform: rotate(-45deg);
}

.tracking-item[open] > .tracking-heading::after {
  transform: rotate(45deg);
}

.tracking-heading:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
  border-radius: 8px;
}

.tracking-heading .el-tag {
  flex-shrink: 0;
}

.tracking-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
}

.tracking-context,
.tracking-feedback {
  min-width: 0;
  padding: 16px 20px;
}

.tracking-context {
  background: #fafbfc;
  border-right: 1px solid var(--tracking-border);
}

.tracking-body .help-text {
  margin: 0 0 12px;
  color: var(--tracking-muted);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.tracking-context > :first-child {
  margin-top: 0;
}

.tracking-item h3 {
  margin: 0;
  margin-right: auto;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.tracking-description {
  margin: 0 0 16px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.tracking-description strong {
  display: block;
  margin-bottom: 4px;
  color: var(--tracking-muted);
  font-size: 12px;
  font-weight: 500;
}

.tracking-meta {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px 16px;
  margin: 0;
  padding-top: 12px;
  border-top: 1px solid var(--tracking-border);
  font-size: 12px;
  line-height: 1.6;
}

.tracking-meta dt {
  color: var(--tracking-muted);
}

.tracking-meta dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.tracking-feedback :deep(.el-form-item) {
  margin-bottom: 16px;
}

.tracking-feedback :deep(.el-form-item__label) {
  color: var(--tracking-muted);
}

.tracking-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tracking-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 767px) {
  .tracking-body {
    grid-template-columns: minmax(0, 1fr);
  }
  .tracking-context {
    border-right: 0;
    border-bottom: 1px solid var(--tracking-border);
  }
  .tracking-heading,
  .tracking-context,
  .tracking-feedback {
    padding: 12px;
  }
}
</style>
