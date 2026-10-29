<template>
  <section class="umc-execution-results" aria-label="巡检设备与项目">
    <div class="section-heading">
      <h3 class="inspection-section-title">巡检设备列表</h3>
      <span class="help-text"
        >{{ task.devices.length }} 台设备 · {{ results.length }} 项规则结果</span
      >
    </div>
    <el-table
      :data="deviceRows"
      :row-key="(row: DeviceRow) => row.key"
      :current-row-key="selected?.key"
      highlight-current-row
      empty-text="尚未选择巡检设备"
      @row-click="selectDevice"
    >
      <el-table-column label="设备名称 / 序列号" min-width="220">
        <template #default="{ row }">
          <strong>{{ row.device.deviceName || row.observed?.devicelabel || '未提供名称' }}</strong>
          <div class="help-text">{{ row.device.serialNumber || '未提供序列号' }}</div>
          <el-tag v-if="row.execution && !isCurrent(row.execution)" type="info" size="small"
            >历史执行</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="设备IP" min-width="135">
        <template #default="{ row }">{{
          row.observed?.deviceip || row.execution?.host || '等待识别'
        }}</template>
      </el-table-column>
      <el-table-column label="设备类型 / 型号" min-width="170">
        <template #default="{ row }">
          <span>{{
            row.device.productTypeName || row.device.productTypeCode || '未提供类型'
          }}</span>
          <div class="help-text">{{
            row.observed?.devmodel || row.device.productModel || '等待识别型号'
          }}</div>
        </template>
      </el-table-column>
      <el-table-column label="软件版本" min-width="165">
        <template #default="{ row }">{{
          row.observed?.version || row.execution?.softwareVersion || '等待识别'
        }}</template>
      </el-table-column>
      <el-table-column label="巡检结果" min-width="180">
        <template #default="{ row }">
          <div v-if="row.results.length" class="result-counts">
            <span class="fatal">致命 {{ countSeverity(row, 3) }}</span>
            <span class="important">重要 {{ countSeverity(row, 2) }}</span>
            <span>一般 {{ countSeverity(row, 1) }}</span>
            <span v-if="unclassifiedCount(row)">未分级 {{ unclassifiedCount(row) }}</span>
            <span v-if="incompleteCount(row)">未完成 {{ incompleteCount(row) }}</span>
          </div>
          <span v-else class="help-text">尚无判定结果</span>
        </template>
      </el-table-column>
      <el-table-column label="执行状态" min-width="160">
        <template #default="{ row }">
          <el-tag :type="statusType(row)">{{ statusLabel(row) }}</el-tag>
          <div class="help-text">{{ attemptLabel(row.execution) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="230" :fixed="smallScreen ? false : 'right'">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :aria-pressed="selected?.key === row.key"
            @click.stop="selectDevice(row)"
          >
            查看巡检项目
          </el-button>
          <el-button
            v-if="canRefresh(row)"
            link
            type="primary"
            :disabled="disabled"
            @click.stop="emit('refresh', row.execution!.id)"
          >
            {{ row.execution?.statusCode === 'COMPLETE' ? '更新结果' : '获取 / 重试结果' }}
          </el-button>
          <el-button
            v-if="hasLog(row.execution)"
            link
            type="primary"
            @click.stop="emit('downloadLog', row.execution!)"
            >下载冻结日志</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <template v-if="selected">
      <div class="device-summary" data-testid="inspection-selected-device">
        <section class="summary-section">
          <h4>巡检设备</h4>
          <dl>
            <div
              ><dt>设备名称</dt
              ><dd>{{
                selected.device.deviceName || selected.observed?.devicelabel || '未提供'
              }}</dd></div
            >
            <div
              ><dt>设备序列号</dt
              ><dd>{{ selected.observed?.sn || selected.device.serialNumber || '未识别' }}</dd></div
            >
            <div
              ><dt>设备IP</dt
              ><dd>{{
                selected.observed?.deviceip || selected.execution?.host || '等待识别'
              }}</dd></div
            >
            <div
              ><dt>设备型号</dt
              ><dd>{{
                selected.observed?.devmodel || selected.device.productModel || '未提供'
              }}</dd></div
            >
            <div
              ><dt>软件版本</dt
              ><dd>{{
                selected.observed?.version || selected.execution?.softwareVersion || '未识别'
              }}</dd></div
            >
          </dl>
        </section>
        <section class="summary-section">
          <h4>巡检任务</h4>
          <dl>
            <div
              ><dt>执行尝试</dt><dd>{{ attemptLabel(selected.execution) }}</dd></div
            >
            <div
              ><dt>执行状态</dt><dd>{{ statusLabel(selected) }}</dd></div
            >
            <div
              ><dt>日志提交</dt
              ><dd>{{
                selected.execution?.submittedAt
                  ? formatDate(selected.execution.submittedAt)
                  : '尚未上传'
              }}</dd></div
            >
            <div
              ><dt>处理完成</dt
              ><dd>{{
                selected.execution?.completedAt
                  ? formatDate(selected.execution.completedAt)
                  : '尚未完成'
              }}</dd></div
            >
            <div v-if="selected.execution?.failureMessage || selected.saved?.failureMessage"
              ><dt>异常说明</dt
              ><dd>{{
                selected.execution?.failureMessage || selected.saved?.failureMessage
              }}</dd></div
            >
            <div v-if="selected.observed?.info"
              ><dt>巡检信息</dt><dd>{{ selected.observed.info }}</dd></div
            >
          </dl>
        </section>
        <section class="summary-section">
          <h4>巡检结果</h4>
          <dl>
            <div
              ><dt>致命问题</dt><dd class="fatal">{{ countSeverity(selected, 3) }}</dd></div
            >
            <div
              ><dt>重要问题</dt><dd class="important">{{ countSeverity(selected, 2) }}</dd></div
            >
            <div
              ><dt>一般问题</dt><dd>{{ countSeverity(selected, 1) }}</dd></div
            >
            <div v-if="unclassifiedCount(selected)"
              ><dt>未分级问题</dt><dd>{{ unclassifiedCount(selected) }}</dd></div
            >
            <div
              ><dt>未完成判定</dt><dd>{{ incompleteCount(selected) }}</dd></div
            >
            <div
              ><dt>已登记规则</dt
              ><dd>{{ formalCount(selected) }} / {{ selected.results.length }}</dd></div
            >
          </dl>
        </section>
      </div>

      <div class="section-heading items-heading">
        <div>
          <h3 class="inspection-section-title">巡检项目</h3>
          <span class="help-text"
            >{{ visibleResults.length }} / {{ selected.results.length }} 项规则 ·
            {{ targetCount }} 项指标</span
          >
        </div>
        <el-checkbox v-model="onlyAttention">仅看异常 / 未判定</el-checkbox>
      </div>
      <el-alert
        v-if="!selected.execution"
        title="本设备尚未上传日志，请先上传本设备日志。"
        type="info"
        :closable="false"
      />
      <el-alert
        v-else-if="!selected.results.length"
        title="本设备尚无巡检判定结果，不能视为通过；可获取或重试结果。"
        type="warning"
        :closable="false"
      />
      <el-empty
        v-else-if="!visibleResults.length"
        description="当前筛选下没有异常或未判定项目；取消筛选可查看全部规则。"
        :image-size="70"
      />
      <InspectionUmcProjectItems
        v-model="expandedItemIds"
        v-model:detail-id="detailResultId"
        :results="visibleResults"
        :detail-context="`${selected.device.serialNumber || '未提供序列号'} · ${attemptLabel(selected.execution)} · ${detailRow && isFormal(detailRow) ? '已登记正式判定' : '报告结果预览，尚未登记正式判定'}`"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { TagProps } from 'element-plus'
import type {
  TaskDetail,
  DeviceView,
  ExecutionView,
  ResultView,
  WireLong
} from '@/api/pms/inspection/tasks'
import type { UmcDevice } from '@/api/pms/inspection/reportResults'
import { formatDate } from '@/utils/formatTime'
import InspectionUmcProjectItems from './InspectionUmcProjectItems.vue'

const props = defineProps<{
  task: TaskDetail
  executions: ExecutionView[]
  results: ResultView[]
  disabled: boolean
}>()
const emit = defineEmits<{
  refresh: [executionId: WireLong]
  downloadLog: [execution: ExecutionView]
}>()
type SavedResult = NonNullable<TaskDetail['umcResults']>[number]
interface DeviceRow {
  key: string
  device: DeviceView
  execution?: ExecutionView
  saved?: SavedResult
  observed?: UmcDevice
  results: ResultView[]
}
const smallScreen = useMediaQuery('(max-width: 767px)')
const selectedKey = ref('')
const onlyAttention = ref(false)
const expandedIds = ref(new Set<string>())
const detailResultId = ref('')
const deviceRows = computed<DeviceRow[]>(() =>
  props.task.devices.flatMap<DeviceRow>((device) => {
    const executions = props.executions.filter(
      (row) => String(row.deviceId) === String(device.deviceId)
    )
    if (!executions.length) return [{ key: `device:${device.deviceId}`, device, results: [] }]
    return executions.map((execution) => {
      const saved = props.task.umcResults?.find(
        (row) => String(row.executionId) === String(execution.id)
      )
      return {
        key: `execution:${execution.id}`,
        device,
        execution,
        saved,
        observed: saved?.result,
        results: props.results.filter((row) => String(row.executionId) === String(execution.id))
      }
    })
  })
)
const selected = computed(
  () => deviceRows.value.find((row) => row.key === selectedKey.value) || deviceRows.value[0]
)
const selectDevice = (row: DeviceRow) => {
  selectedKey.value = row.key
  onlyAttention.value = false
}
watch(
  () => String(props.task.id),
  () => {
    selectedKey.value = ''
    onlyAttention.value = false
    expandedIds.value.clear()
    detailResultId.value = ''
  }
)
const visibleResults = computed(() =>
  (selected.value?.results || []).filter((row) => !onlyAttention.value || row.outcome !== 'PASS')
)
const expandedItemIds = computed({
  get: () =>
    visibleResults.value.map((row) => String(row.id)).filter((id) => expandedIds.value.has(id)),
  set: (ids: Array<string | number>) => {
    const expanded = new Set(ids.map(String))
    for (const row of visibleResults.value) {
      const id = String(row.id)
      if (expanded.has(id)) expandedIds.value.add(id)
      else expandedIds.value.delete(id)
    }
  }
})
const detailRow = computed(() =>
  visibleResults.value.find((row) => String(row.id) === detailResultId.value)
)
watch(
  () => selected.value?.key,
  () => {
    expandedIds.value.clear()
    detailResultId.value = ''
  }
)
watch(detailRow, (row) => {
  if (!row) detailResultId.value = ''
})
const targetCount = computed(() =>
  (selected.value?.results || []).reduce(
    (total, row) => total + (row.externalRule?.targetlist?.length || 0),
    0
  )
)
const isCurrent = (execution: ExecutionView) =>
  String(execution.attemptId) === String(props.task.currentAttemptId)
const registeredIds = computed(() => new Set(props.task.results.map((row) => String(row.id))))
const isFormal = (result: ResultView) => registeredIds.value.has(String(result.id))
const formalCount = (row: DeviceRow) => row.results.filter(isFormal).length
const countSeverity = (row: DeviceRow, level: number) =>
  row.results.filter(
    (result) => result.outcome === 'ABNORMAL' && result.externalRule?.level === level
  ).length
const unclassifiedCount = (row: DeviceRow) =>
  row.results.filter(
    (result) =>
      result.outcome === 'ABNORMAL' && ![1, 2, 3].includes(result.externalRule?.level ?? 0)
  ).length
const incompleteCount = (row: DeviceRow) =>
  row.results.filter((result) => !['PASS', 'ABNORMAL'].includes(result.outcome || '')).length
const hasLog = (row?: ExecutionView) =>
  row != null && row.artifactId != null && row.fileVersionNo != null && !!row.fileReferenceKey
const canRefresh = (row: DeviceRow) =>
  row.execution != null &&
  isCurrent(row.execution) &&
  (row.execution.purposeCode === 'OFFLINE' ||
    (row.execution.purposeCode === 'COLLECT' && hasLog(row.execution))) &&
  (row.saved != null || ['PENDING', 'RUNNING'].includes(row.execution.statusCode))
const attemptLabel = (execution?: ExecutionView) => {
  if (!execution) return '尚无执行记录'
  const attempt = props.task.attempts.find((row) => String(row.id) === String(execution.attemptId))
  return `第 ${attempt?.attemptNo ?? '—'} 次尝试${isCurrent(execution) ? '' : '（历史）'}`
}
const statuses: Record<string, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  COLLECTED: '已采集，待判定',
  PASSED: '预检通过',
  CANCEL_REQUESTED: '取消待确认',
  COMPLETE: '判定完成',
  PARTIAL: '部分失败',
  FAILED: '失败',
  CANCELLED: '已取消',
  SUCCEEDED: '成功',
  TIMED_OUT: '超时'
}
const statusLabel = (row: DeviceRow) => {
  if (!row.execution) return '等待上传日志'
  const attempt = props.task.attempts.find(
    (attempt) => String(attempt.id) === String(row.execution?.attemptId)
  )
  if (attempt?.providerCode !== 'UMC')
    return statuses[row.execution.statusCode] || row.execution.statusCode
  if (
    ['PARTIAL', 'FAILED', 'CANCELLED', 'PASSED', 'TIMED_OUT', 'CANCEL_REQUESTED'].includes(
      row.execution.statusCode
    )
  )
    return statuses[row.execution.statusCode]
  if (
    row.execution.statusCode === 'COMPLETE' &&
    row.results.length &&
    formalCount(row) === row.results.length
  )
    return '判定完成'
  if (row.saved?.failureMessage) return '未完成，需重试'
  return row.saved?.statusCode === 'AVAILABLE' ? '报告已保存，待登记判定' : '等待UMC结果'
}
const statusType = (row: DeviceRow): TagProps['type'] =>
  row.execution?.statusCode === 'COMPLETE' &&
  row.results.length > 0 &&
  formalCount(row) === row.results.length
    ? 'success'
    : ['FAILED', 'TIMED_OUT'].includes(row.execution?.statusCode || '')
      ? 'danger'
      : row.execution?.statusCode === 'PARTIAL'
        ? 'warning'
        : 'info'
</script>

<style scoped>
.umc-execution-results {
  min-width: 0;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.section-heading {
  margin: 20px 0 12px;
}

.inspection-section-title {
  display: inline-block;
  margin: 0 12px 0 0;
  font-size: 14px;
}

.help-text {
  margin: 6px 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.device-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.summary-section {
  min-width: 0;
  padding: 16px;
}

.summary-section + .summary-section {
  border-left: 1px solid var(--el-border-color-light);
}

.summary-section h4 {
  margin: 0 0 12px;
  font-size: 14px;
}

dl,
dd {
  margin: 0;
}

.summary-section dl > div {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 10px;
  margin-top: 8px;
  line-height: 1.6;
}

dt {
  color: var(--el-text-color-secondary);
}

dd {
  min-width: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.result-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  font-size: 12px;
}

.fatal,
.attention {
  color: var(--el-color-danger);
}

.important {
  color: var(--el-color-warning-dark-2);
}

@media (width <= 900px) {
  .device-summary {
    grid-template-columns: minmax(0, 1fr);
  }

  .summary-section + .summary-section {
    border-top: 1px solid var(--el-border-color-light);
    border-left: 0;
  }
}

@media (width <= 600px) {
  .summary-section {
    padding: 12px;
  }
}
</style>
