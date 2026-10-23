<template>
  <el-dialog
    v-model="visible"
    title="阶段状态查看"
    width="min(1240px, 96vw)"
    class="execution-history-dialog"
    destroy-on-close
  >
    <div class="history-toolbar">
      <div>
        <p class="history-intro">按阶段查看任务进展与完成依据</p>
        <p class="history-caption">自动完成取对应业务时间，手工办理取实际提交时间；结束时间保留阶段或任务实际结束时间。</p>
      </div>
      <el-button :loading="loading" @click="load"><Icon icon="ep:refresh" />刷新页面</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading" class="history-content">
      <el-empty v-if="!loading && !error && !history?.rounds.length" description="暂无阶段执行记录" />
      <section v-for="plan in history?.plans ?? []" :key="plan.id" class="plan-history">
        <h4
          >项目计划 v{{ plan.revisionNo }} ·
          {{ plan.status === 'EFFECTIVE' ? '当前有效' : '历史版本' }}</h4
        >
        <p
          >生效：{{ formatDate(plan.effectiveAt)
          }}<span v-if="plan.closedAt"> · 项目收口：{{ formatDate(plan.closedAt) }}</span></p
        >
        <template v-if="plan.closure">
          <p
            >收口规则：{{ outcomeLabel(plan.closure.outcome) }} ·
            {{ plan.closure.reasonCode || '无异常' }}</p
          >
          <el-table :data="plan.closure.conditions" border>
            <el-table-column prop="path" label="条件位置" />
            <el-table-column prop="component" label="执行组件" />
            <el-table-column label="条件结果"
              ><template #default="{ row }">{{
                outcomeLabel(row.outcome)
              }}</template></el-table-column
            >
            <el-table-column prop="reasonCode" label="原因" />
          </el-table>
        </template>
      </section>
      <section v-for="group in groups" :key="group.key" class="stage-history" :aria-label="group.name">
        <header class="stage-heading">
          <div><span class="stage-code">{{ group.code }}</span><h3>{{ group.name }}</h3></div>
          <span class="stage-count">{{ group.completed }}/{{ group.rounds.length }} 项已完成</span>
        </header>
      <el-table
        :data="group.rounds"
        row-key="id"
        empty-text="暂无执行轮次"
        class="mt-16px"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="round-evidence">
              <p
                >计划版本：v{{ row.planRevisionNo ?? '未知' }} · 执行编号：{{ row.id }} ·
                {{ row.current ? '当前轮次' : '历史轮次' }}</p
              >
              <p
                >准入：{{ row.admittedAt ? formatDate(row.admittedAt) : '未准入' }} · 开始：{{
                  row.startedAt ? formatDate(row.startedAt) : '未开始'
                }}
                · 结束：{{ row.endedAt ? formatDate(row.endedAt) : '未结束' }}</p
              >
              <p
                >提交：{{ submissionLabel(row) }}<span v-if="row.submittedBy"> · 提交人编号：{{ row.submittedBy }}</span></p
              >
              <p v-if="row.canViewSubmissionNote" class="submission-note"
                >办理说明：{{ row.submissionNote || '无手工说明' }}</p
              >
              <p v-else>办理说明需要办理权限。</p>
              <section v-for="evaluation in row.evaluations" :key="evaluation.purpose">
                <h4
                  >{{ purposeLabel(evaluation.purpose) }} ·
                  {{ evaluation.name }} · {{ outcomeLabel(evaluation.result.outcome) }}</h4
                >
                <el-table :data="evaluation.result.conditions" border>
                  <el-table-column prop="path" label="条件位置" />
                  <el-table-column prop="component" label="执行组件" />
                  <el-table-column label="条件结果"
                    ><template #default="{ row: condition }">{{
                      outcomeLabel(condition.outcome)
                    }}</template></el-table-column
                  >
                  <el-table-column prop="reasonCode" label="原因" />
                </el-table>
                <p v-if="evaluation.result.steps.length"
                  >执行步骤：{{ evaluation.result.steps.join(' → ') }}</p
                >
              </section>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="阶段 / 任务" min-width="200">
          <template #default="{ row }">
            <div class="node-name"><span class="node-kind">{{ row.nodeKind === 'STAGE' ? '阶段' : '任务' }}</span><strong>{{ row.name }}</strong></div>
            <span class="node-meta">{{ row.nodeCode }} · 计划 v{{ row.planRevisionNo ?? '—' }}{{ row.current ? '' : ' · 历史轮次' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="roundNo" label="轮次" width="80" />
        <el-table-column label="状态" width="110"
          ><template #default="{ row }"><span class="status-label" :data-status="row.status">{{ statusLabel(row.status) }}</span></template></el-table-column
        >
        <el-table-column label="提交时间" min-width="205">
          <template #default="{ row }">
            <span class="time-value">{{ submissionLabel(row) }}</span>
            <span v-if="row.submittedAt != null || row.submissionTime" class="time-source">{{ row.submittedAt != null ? '手工提交' : row.submissionTime.label }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="175"
          ><template #default="{ row }">{{
            row.endedAt ? formatDate(row.endedAt) : '未结束'
          }}</template></el-table-column
        >
      </el-table>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatDate } from '@/utils/formatTime'
import {
  getExecutionHistory,
  type ExecutionHistory
} from '@/api/pms/project/projects/nodeExecutions'
const props = defineProps<{ projectId: number }>()
const visible = defineModel<boolean>({ default: false })
const history = ref<ExecutionHistory>()
type HistoryRound = ExecutionHistory['rounds'][number]
const submissionLabel = (row: HistoryRound) => {
  const time = row.submittedAt ?? row.submissionTime?.at
  if (time != null) return formatDate(time)
  return row.status === 'DONE' ? '业务时间未记录' : row.status === 'TERMINATED' ? '未提交' : '待提交'
}
const groups = computed(() => {
  const rounds = history.value?.rounds ?? []
  const stages = new Map(rounds.filter(row => row.nodeKind === 'STAGE').map(row => [`${row.planVersionId}:${row.nodeCode}`, row.name]))
  const result = new Map<string, { key: string; code: string; name: string; completed: number; rounds: HistoryRound[] }>()
  for (const row of rounds) {
    const code = row.stageCode || (row.nodeKind === 'STAGE' ? row.nodeCode : '')
    const key = `${row.planVersionId}:${code || 'unassigned'}`
    if (!result.has(key)) result.set(key, { key, code, name: stages.get(key) || (code ? `${code} 阶段` : '其他任务'), completed: 0, rounds: [] })
    const group = result.get(key)!
    group.rounds.push(row)
    if (row.status === 'DONE') group.completed++
  }
  return [...result.values()]
})
const error = ref('')
const loading = ref(false)
let generation = 0
const outcomeLabel = (value: string) =>
  ({ MATCHED: '满足', NOT_MATCHED: '不满足', UNKNOWN: '未知' })[value] ?? value
const purposeLabel = (value: string) =>
  ({ completion: '完成', exit: '退出', gate: '门禁' })[value] ?? value
const statusLabel = (value: string) =>
  ({ PENDING: '等待准入', ACTIVE: '进行中', DONE: '已完成', TERMINATED: '已终止' })[value] ?? value
const load = async () => {
  const request = ++generation
  error.value = ''
  loading.value = true
  try {
    const result = await getExecutionHistory(props.projectId)
    if (request === generation) history.value = result
  } catch {
    if (request === generation) error.value = '执行历史加载失败，请重试。'
  } finally {
    if (request === generation) loading.value = false
  }
}
watch(
  () => [visible.value, props.projectId],
  (next, previous) => {
    if (next[1] !== previous?.[1]) history.value = undefined
    if (visible.value) void load()
    else {
      generation++
      history.value = undefined
      loading.value = false
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.history-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding-bottom: 18px; border-bottom: 1px solid var(--el-border-color-lighter, #e3e8ef); }
.history-intro { margin: 0 0 6px; font-size: 16px; font-weight: 600; color: var(--el-text-color-primary, #263445); }
.history-caption, .node-meta, .time-source { color: var(--el-text-color-secondary, #64758b); font-size: 12px; }
.history-caption { margin: 0; line-height: 1.6; }
.history-content { min-height: 140px; }
.plan-history { margin: 16px 0; padding: 12px 16px; background: var(--el-fill-color-light, #f3f6fa); border-radius: 4px; }
.plan-history h4 { margin: 0 0 6px; font-size: 14px; }
.plan-history p { margin: 4px 0; font-size: 12px; color: var(--el-text-color-secondary); }
.stage-history { margin-top: 24px; }
.stage-heading, .stage-heading > div { display: flex; align-items: center; gap: 12px; }
.stage-heading { justify-content: space-between; padding: 10px 0; border-bottom: 2px solid var(--el-border-color-light, #e3e8ef); }
.stage-heading h3 { margin: 0; font-size: 16px; color: var(--el-text-color-primary, #263445); }
.stage-code { font-size: 14px; font-weight: 700; color: var(--el-color-primary, #2563a6); }
.stage-count { font-size: 12px; color: var(--el-text-color-secondary); }
.node-name { display: flex; align-items: baseline; gap: 8px; }
.node-name strong { font-size: 14px; font-weight: 500; }
.node-kind { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
.node-meta, .time-source { display: block; margin-top: 4px; }
.time-value { font-variant-numeric: tabular-nums; white-space: nowrap; }
.status-label { font-size: 13px; }
.status-label[data-status='DONE'] { color: var(--el-color-success, #247a52); }
.status-label[data-status='ACTIVE'] { color: var(--el-color-primary, #2563a6); }
.status-label[data-status='PENDING'], .status-label[data-status='TERMINATED'] { color: var(--el-text-color-secondary); }
.round-evidence { padding: 12px 24px; background: var(--el-fill-color-lighter); }
.round-evidence p { font-size: 12px; line-height: 1.7; }
.round-evidence h4 { font-size: 13px; }
.submission-note { white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 640px) { .history-toolbar { align-items: flex-start; } .history-intro { font-size: 14px; } .stage-heading { gap: 8px; } .round-evidence { padding: 12px; } }
</style>
