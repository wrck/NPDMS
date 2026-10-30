<template>
  <section
    class="umc-report-panel"
    aria-labelledby="umc-report-heading"
    data-testid="inspection-umc-report"
  >
    <header class="inspection-card__header">
      <h2 id="umc-report-heading">巡检报告</h2>
      <el-tag :type="currentReport?.sourceCode === 'UMC' ? 'success' : 'info'">
        {{ currentReport?.sourceCode === 'UMC' ? '报告已确认' : '待确认报告' }}
      </el-tag>
    </header>
    <p class="help-text">确认本次全部设备报告后，继续确认检出问题。</p>
    <el-descriptions :column="3" border>
      <el-descriptions-item label="本次设备">{{ task.devices.length }} 台</el-descriptions-item>
      <el-descriptions-item label="正式判定">{{ selectedResults.length }} 条</el-descriptions-item>
      <el-descriptions-item label="报告就绪"
        >{{ readyCount }} / {{ task.devices.length }}</el-descriptions-item
      >
    </el-descriptions>
    <el-alert
      v-if="blocker"
      :title="blocker"
      type="info"
      :closable="false"
      show-icon
      class="mt-16px"
    />
    <el-table
      :data="deviceReports"
      row-key="deviceId"
      class="mt-16px"
      empty-text="本任务尚未选择设备"
    >
      <el-table-column prop="serialNumber" label="设备序列号" min-width="210" />
      <el-table-column label="设备型号" min-width="160"
        ><template #default="{ row }">{{
          row.saved?.result?.devmodel || row.productModel || '—'
        }}</template></el-table-column
      >
      <el-table-column label="判定结果" min-width="210"
        ><template #default="{ row }">
          <span
            >{{ row.results.length }} 条：正常 {{ count(row.results, 'PASS') }}，异常
            {{ count(row.results, 'ABNORMAL') }}，不支持 {{ unsupportedCount(row.results) }}</span
          >
          <p
            v-if="
              row.results.some(
                (r) =>
                  r.externalRule?.support !== false && !['PASS', 'ABNORMAL'].includes(r.outcome)
              )
            "
            class="help-text"
            >含无法判定或执行错误，请查看执行结果。</p
          >
        </template></el-table-column
      >
      <el-table-column label="报告状态" min-width="150"
        ><template #default="{ row }">
          <el-tag :type="row.ready ? 'success' : 'warning'">{{
            row.ready ? 'DOC已保存' : row.saved?.failureMessage ? '未完成，需重试' : '等待巡检结果'
          }}</el-tag>
          <p v-if="row.saved?.failureMessage" class="help-text">{{ row.saved.failureMessage }}</p>
        </template></el-table-column
      >
      <el-table-column label="操作" min-width="170"
        ><template #default="{ row }">
          <el-button
            v-if="row.file && row.report"
            link
            type="primary"
            :disabled="!canDownload"
            @click="emit('download', { report: row.report, file: row.file })"
            >下载DOC</el-button
          >
          <el-button
            v-if="!row.ready && row.executionId && canRetry"
            link
            type="primary"
            :disabled="disabled"
            @click="emit('retryExecution', row.executionId)"
            >重试巡检</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <Teleport :to="actionsTarget || 'body'" :disabled="!actionsTarget">
      <div :class="actionsTarget ? 'inspection-actions-embedded' : 'inspection-footer'">
        <el-button @click="emit('showResults')">查看完整判定结果</el-button>
        <el-button
          v-if="!currentReport && task.statusCode === 'REPORT_PENDING'"
          type="primary"
          :disabled="!canConfirm"
          @click="emit('adoptExternal')"
          >确认报告并进入问题处理</el-button
        >
      </div>
    </Teleport>
    <el-collapse v-if="history.length" v-model="historyOpen" class="mt-16px">
      <el-collapse-item :title="`历史报告（${history.length}）`" name="history">
        <template v-if="historyOpen.includes('history')">
          <div v-for="report in history" :key="String(report.id)" class="history-report">
            <h3
              >{{ report.sourceCode === 'UMC' ? '历史UMC报告' : '原平台报告' }} ·
              {{ report.generatedAt ? formatDate(report.generatedAt) : '—' }}</h3
            >
            <el-button
              v-for="file in report.files || []"
              :key="String(file.id)"
              link
              type="primary"
              :disabled="!canDownload"
              @click="emit('download', { report, file })"
              >{{ file.fileName || `下载${file.formatCode}` }}</el-button
            >
          </div>
        </template>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  TaskDetail,
  ReportView,
  ReportFileView,
  ResultView,
  WireLong
} from '@/api/pms/inspection/tasks'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'

const props = defineProps<{ task: TaskDetail; disabled: boolean; actionsTarget?: HTMLElement }>()
const emit = defineEmits<{
  adoptExternal: []
  retryExecution: [executionId: WireLong]
  showResults: []
  download: [value: { report: ReportView; file: ReportFileView }]
}>()
const currentReport = computed(() =>
  props.task.reports.find((r) => String(r.id) === String(props.task.currentReportId))
)
const sourceIds = computed(
  () => new Set((currentReport.value?.componentReportIds || []).map(String))
)
const deviceReports = computed(() =>
  props.task.devices.map((device) => {
    const latest = props.task.executions
      .filter(
        (e) =>
          String(e.attemptId) === String(props.task.currentAttemptId) &&
          String(e.deviceId) === String(device.deviceId) &&
          e.statusCode !== 'CANCELLED' &&
          e.purposeCode !== 'PRECHECK'
      )
      .sort((a, b) => (BigInt(String(a.id)) > BigInt(String(b.id)) ? -1 : 1))[0]
    const candidates = (props.task.umcResults || []).filter(
      (r) =>
        String(r.deviceId) === String(device.deviceId) &&
        (currentReport.value
          ? sourceIds.value.has(String(r.reportId))
          : String(r.attemptId) === String(props.task.currentAttemptId) &&
            String(r.executionId) === String(latest?.id))
    )
    const saved = [...candidates].sort((a, b) =>
      BigInt(String(a.executionId)) > BigInt(String(b.executionId)) ? -1 : 1
    )[0]
    const report = props.task.reports.find((r) => String(r.id) === String(saved?.reportId))
    const file = report?.files?.find((f) => f.formatCode === 'DOC')
    const results = props.task.results.filter(
      (r) =>
        r.sourceCode === 'UMC' &&
        String(r.executionId) === String(saved?.executionId) &&
        (!currentReport.value ||
          (currentReport.value.resultIds || []).some((id) => String(id) === String(r.id)))
    )
    return {
      ...device,
      saved,
      report,
      file,
      results,
      executionId: saved?.executionId,
      ready:
        saved?.statusCode === 'AVAILABLE' &&
        !!file &&
        !!file.artifactId &&
        !!file.referenceKey &&
        !!file.fileVersionNo
    }
  })
)
const selectedResults = computed(() => deviceReports.value.flatMap((r) => r.results))
const readyCount = computed(() => deviceReports.value.filter((r) => r.ready).length)
const canDownload = computed(() => checkPermi(['pms:file:download']))
const canRetry = computed(
  () => props.task.statusCode === 'EXECUTING' && checkPermi(['pms:inspection-task:execute'])
)
const canConfirm = computed(
  () =>
    !props.disabled &&
    !currentReport.value &&
    props.task.statusCode === 'REPORT_PENDING' &&
    checkPermi(['pms:inspection-report:generate']) &&
    deviceReports.value.length > 0 &&
    deviceReports.value.every(
      (r) =>
        r.ready &&
        Array.isArray(r.saved?.result?.rulelist) &&
        r.results.length === r.saved!.result!.rulelist!.length
    )
)
const blocker = computed(() =>
  currentReport.value
    ? ''
    : props.task.statusCode === 'REPORT_PENDING'
      ? canConfirm.value
        ? '全部设备的正式判定和DOC已保存，请核对后确认报告。'
        : '报告或正式判定尚不完整，暂不能确认；请回到巡检执行核对缺失设备。'
      : '请先完成全部设备的巡检执行，再确认本次报告。'
)
const historyOpen = ref<string[]>([])
const history = computed(() => {
  const shown = new Set(deviceReports.value.map((r) => String(r.report?.id)))
  const adoptedSources = new Set(
    props.task.reports.flatMap((r) => r.componentReportIds || []).map(String)
  )
  return props.task.reports
    .filter(
      (r) =>
        String(r.id) !== String(props.task.currentReportId) &&
        !shown.has(String(r.id)) &&
        !adoptedSources.has(String(r.id))
    )
    .sort((a, b) => (b.revisionNo || 0) - (a.revisionNo || 0))
})
const count = (results: ResultView[], outcome: string) =>
  results.filter((r) => r.externalRule?.support !== false && r.outcome === outcome).length
const unsupportedCount = (results: ResultView[]) =>
  results.filter((r) => r.externalRule?.support === false).length
</script>

<style scoped>
.history-report {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.history-report h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
.inspection-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
