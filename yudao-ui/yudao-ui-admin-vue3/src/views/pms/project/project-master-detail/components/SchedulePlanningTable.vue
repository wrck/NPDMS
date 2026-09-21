<template>
  <div class="planning-table">
    <div class="planning-tools">
      <div class="view-switch" aria-label="计划视图">
        <el-radio-group v-model="view" size="small">
          <el-radio-button value="dates">日期安排</el-radio-button>
          <el-radio-button value="timeline">时间轴</el-radio-button>
        </el-radio-group>
        <span>{{ items.length }} 个阶段 / {{ tasks.length }} 项任务</span>
      </div>
      <el-input
        v-model="keyword"
        placeholder="查找阶段或任务"
        clearable
        aria-label="查找阶段或任务"
        class="plan-search"
      />
    </div>
    <el-table
      :data="rows"
      row-key="key"
      default-expand-all
      :tree-props="{ children: 'children' }"
      :row-class-name="rowClass"
      data-testid="schedule-stage-task-table"
      empty-text="没有符合条件的阶段或任务"
    >
      <el-table-column label="阶段 / 任务" min-width="200" fixed>
        <template #default="{ row }">
          <span class="node-kind" :class="row.kind">{{
            row.kind === 'stage' ? '阶段' : '任务'
          }}</span>
          <strong v-if="row.kind === 'stage'">{{ row.name }}</strong
          ><span v-else>{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="view === 'dates'" label="计划开始" width="152">
        <template #default="{ row }">
          <el-date-picker
            v-if="editable"
            v-model="row.value.planStart"
            type="date"
            value-format="YYYY-MM-DD"
            :aria-label="`${row.name}计划开始`"
            :clearable="true"
            class="plan-date"
          />
          <span v-else>{{ row.value.planStart || '未安排' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="view === 'dates'" label="计划结束" width="152">
        <template #default="{ row }">
          <el-date-picker
            v-if="editable"
            v-model="row.value.planEnd"
            type="date"
            value-format="YYYY-MM-DD"
            :aria-label="`${row.name}计划结束`"
            :clearable="true"
            class="plan-date"
          />
          <span v-else>{{ row.value.planEnd || '未安排' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="view === 'timeline'" min-width="360">
        <template #header
          ><div class="timeline-scale"
            ><span>{{ range.start || '开始' }}</span
            ><span>计划时间轴</span><span>{{ range.end || '结束' }}</span></div
          ></template
        >
        <template #default="{ row }">
          <div
            v-if="bar(row)"
            class="timeline-track"
            :title="`${row.name}：${row.value.planStart} 至 ${row.value.planEnd}`"
          >
            <div
              class="timeline-bar"
              :class="{ 'task-bar': row.kind === 'task', 'issue-bar': issue(row) }"
              :style="bar(row)!"
            >
              <span>{{ row.value.planStart?.slice(5) }} — {{ row.value.planEnd?.slice(5) }}</span>
            </div>
          </div>
          <span v-else class="secondary">未安排日期</span>
        </template>
      </el-table-column>
      <el-table-column label="天数" width="64" align="right"
        ><template #default="{ row }">{{ duration(row) ?? '—' }}</template></el-table-column
      >
      <el-table-column label="倒排建议 / 计划验收" width="156">
        <template #default="{ row }">
          <span>{{
            row.kind === 'stage'
              ? row.stage.suggestedEnd || '暂无建议'
              : row.task?.acceptanceTime?.slice(0, 10) || '随所属阶段安排'
          }}</span>
          <small v-if="row.kind === 'task' && row.task?.acceptanceTime">合同计划验收</small>
          <small v-if="row.kind === 'stage' && acceptanceByStage[row.stage.phaseCode]"
            >计划验收 {{ acceptanceByStage[row.stage.phaseCode]?.slice(0, 10) }}</small
          >
        </template>
      </el-table-column>
      <el-table-column label="安排检查" min-width="150">
        <template #default="{ row }"
          ><span :class="issue(row) ? 'plan-issue' : 'secondary'">{{
            issue(row) || '已安排'
          }}</span></template
        >
      </el-table-column>
      <el-table-column label="备注 / 操作" min-width="130">
        <template #default="{ row }">
          <el-input
            v-if="row.kind === 'stage' && editable"
            v-model="row.stage.remark"
            :aria-label="`${row.name}备注`"
            placeholder="填写备注"
          />
          <span v-else-if="row.kind === 'stage'">{{ row.stage.remark || '—' }}</span>
          <el-button
            v-else
            link
            type="primary"
            :disabled="navigationDisabled"
            @click="emit('open-task', row.task!)"
            >任务详情</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <p class="table-help"
      >展开阶段可安排任务及子任务。任务日期在所属阶段内安排，保存后随整份计划提交审核。</p
    >
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import type { StagePlanItemVO, StagePlanTaskVO } from '@/api/pms/engineering/stage-plan'
import {
  buildScheduleRows,
  scheduleRowIssue,
  scheduleRange,
  timelineBar,
  type ScheduleRow
} from './schedulePresentation'

const props = defineProps<{
  items: StagePlanItemVO[]
  tasks: StagePlanTaskVO[]
  editable: boolean
  navigationDisabled: boolean
  overdueByStage?: Record<number, number>
  acceptanceByStage: Record<string, string>
}>()
const emit = defineEmits<{ 'open-task': [task: StagePlanTaskVO] }>()
const view = ref('dates'),
  keyword = ref('')
const rows = computed(() => buildScheduleRows(props.items, props.tasks, keyword.value))
const range = computed(() => scheduleRange(props.items, props.tasks))
const issue = (row: ScheduleRow) =>
  (row.kind === 'stage' &&
  row.value.planEnd &&
  props.acceptanceByStage[row.stage.phaseCode!] &&
  row.value.planEnd > props.acceptanceByStage[row.stage.phaseCode!].slice(0, 10)
    ? '晚于计划验收日期'
    : '') ||
  scheduleRowIssue(row) ||
  (row.kind === 'stage' && props.overdueByStage?.[row.stage.phaseId!]
    ? `超期 ${props.overdueByStage[row.stage.phaseId!]} 天`
    : '')
const bar = (row: ScheduleRow) => timelineBar(row.value.planStart, row.value.planEnd, range.value)
const duration = (row: ScheduleRow) =>
  row.value.planStart && row.value.planEnd && row.value.planEnd >= row.value.planStart
    ? dayjs(row.value.planEnd).diff(dayjs(row.value.planStart), 'day') + 1
    : undefined
const rowClass = ({ row }: { row: ScheduleRow }) =>
  `${row.kind}-row ${issue(row) ? 'has-issue' : ''}`
</script>

<style scoped lang="scss">
.planning-tools,
.view-switch {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.planning-tools {
  justify-content: space-between;
  padding: 16px 0;
}

.view-switch > span,
.secondary,
.table-help,
small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.plan-search {
  width: 220px;
}

.plan-date {
  width: 128px !important;
}

.node-kind {
  display: inline-block;
  padding: 0 5px;
  margin-right: 8px;
  font-size: 11px;
  line-height: 19px;
  border-radius: 3px;
}

.node-kind.stage {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.node-kind.task {
  color: var(--el-text-color-secondary);
  border: 1px solid var(--el-border-color-lighter);
}

:deep(.stage-row) {
  --el-table-tr-bg-color: var(--el-fill-color-light);
}

:deep(.el-table .cell) {
  padding-top: 6px;
  padding-bottom: 6px;
}

.plan-issue {
  font-size: 12px;
  color: var(--el-color-danger);
}

small {
  display: block;
}

.timeline-scale {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  font-weight: normal;
}

.timeline-track {
  position: relative;
  height: 32px;
  background: repeating-linear-gradient(
    to right,
    transparent 0,
    transparent calc(25% - 1px),
    var(--el-border-color-lighter) calc(25% - 1px),
    var(--el-border-color-lighter) 25%
  );
}

.timeline-bar {
  position: absolute;
  top: 5px;
  height: 22px;
  min-width: 3px;
  color: var(--el-color-white);
  background: var(--el-color-primary-light-3);
  border-radius: 3px;
}

.timeline-bar span {
  padding: 0 6px;
  font-size: 11px;
  line-height: 22px;
  white-space: nowrap;
}

.task-bar {
  color: var(--el-text-color-primary);
  background: var(--el-color-primary-light-8);
}

.issue-bar {
  color: var(--el-color-danger-dark-2);
  background: var(--el-color-danger-light-7);
}

.table-help {
  margin: 12px 0 0;
  line-height: 1.6;
}

@media (width <= 767px) {
  .plan-search {
    width: 100%;
  }

  .planning-tools {
    gap: 12px;
  }
}
</style>
