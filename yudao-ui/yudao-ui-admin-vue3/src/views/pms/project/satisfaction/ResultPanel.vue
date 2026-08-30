<template>
  <div class="panel-heading">
    <div
      ><h2>满意度判定结果</h2><p>结果版本不可修改；支持历史文件下载、正式失效和异步导出。</p></div
    >
    <div class="toolbar"
      ><el-input-number
        v-model="projectId"
        :min="1"
        controls-position="right"
        placeholder="项目ID"
      /><el-button :loading="loading" @click="load">查询</el-button
      ><el-button type="primary" :disabled="!projectId" @click="openExport">导出</el-button></div
    >
  </div>
  <el-skeleton v-if="loading" :rows="4" animated />
  <el-empty v-else-if="!results.length" description="当前范围内暂无满意度结果" />
  <el-table v-else :data="results" stripe>
    <el-table-column prop="resultId" label="结果ID" min-width="150" />
    <el-table-column prop="projectId" label="项目ID" min-width="150" />
    <el-table-column prop="taskRevisionNo" label="轮次" width="80" />
    <el-table-column label="得分 / 阈值" min-width="130"
      ><template #default="scope"
        >{{ scope.row.score }} / {{ scope.row.threshold }}</template
      ></el-table-column
    >
    <el-table-column label="判定" width="100"
      ><template #default="scope"
        ><el-tag :type="scope.row.passed ? 'success' : 'danger'">{{
          scope.row.passed ? '达标' : '未达标'
        }}</el-tag></template
      ></el-table-column
    >
    <el-table-column prop="resultStatus" label="状态" width="120" />
    <el-table-column prop="archiveStatus" label="归档" min-width="140" />
    <el-table-column label="操作" width="250" fixed="right">
      <template #default="scope">
        <el-button link type="primary" @click="openDownload(scope.row)">下载文件</el-button>
        <el-button
          v-if="scope.row.resultStatus === 'EFFECTIVE' && scope.row.passed"
          link
          type="danger"
          @click="invalidate(scope.row)"
          >失效</el-button
        >
      </template>
    </el-table-column>
  </el-table>

  <el-dialog v-model="downloadVisible" title="下载结果文件" width="460px">
    <el-form label-position="top"
      ><el-form-item label="来源附件序号"
        ><el-input-number v-model="downloadSequence" :min="1" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="downloadVisible = false">取消</el-button
      ><el-button type="primary" @click="download">创建下载票据</el-button></template
    >
  </el-dialog>

  <el-dialog v-model="exportVisible" title="异步导出满意度结果" width="min(560px, 94vw)">
    <el-checkbox-group v-model="exportFields" class="field-grid">
      <el-checkbox v-for="field in availableFields" :key="field.value" :value="field.value">{{
        field.label
      }}</el-checkbox>
    </el-checkbox-group>
    <el-checkbox v-model="includeFiles">包含已授权文件</el-checkbox>
    <el-alert
      v-if="exportTask"
      :title="`导出任务 ${exportTask.taskId}：${exportTask.status}`"
      :type="
        exportTask.status === 'SUCCEEDED'
          ? 'success'
          : exportTask.status === 'FAILED'
            ? 'error'
            : 'info'
      "
      :closable="false"
      show-icon
    />
    <template #footer>
      <el-button @click="exportVisible = false">关闭</el-button>
      <el-button
        v-if="exportTask?.status === 'FAILED' && exportTask.failureRetryable"
        @click="retryExport"
        >重试</el-button
      >
      <el-button v-if="exportTask?.status === 'SUCCEEDED'" type="success" @click="downloadExport"
        >下载</el-button
      >
      <el-button v-else type="primary" :loading="exporting" @click="startExport"
        >提交导出</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import * as Api from '@/api/pms/project/satisfaction'
import * as FileApi from '@/api/pms/platform/file'
import type { ExportTask, ResultView } from '@/api/pms/project/satisfaction'

const message = useMessage()
const loading = ref(false)
const projectId = ref<number>()
const results = ref<ResultView[]>([])
const selected = ref<ResultView>()
const downloadVisible = ref(false)
const downloadSequence = ref(1)
const exportVisible = ref(false)
const exporting = ref(false)
const exportTask = ref<ExportTask>()
const includeFiles = ref(false)
const exportFields = ref([
  'resultId',
  'projectId',
  'taskRevisionNo',
  'score',
  'threshold',
  'passed',
  'resultStatus'
])
const availableFields = [
  { value: 'resultId', label: '结果ID' },
  { value: 'projectId', label: '项目ID' },
  { value: 'taskRevisionNo', label: '轮次' },
  { value: 'ruleVersion', label: '规则版本' },
  { value: 'score', label: '得分' },
  { value: 'threshold', label: '阈值' },
  { value: 'passed', label: '达标判定' },
  { value: 'resultStatus', label: '状态' },
  { value: 'archiveStatus', label: '归档状态' },
  { value: 'effectiveFrom', label: '生效时间' }
]
let pollTimer: number | undefined

const load = async () => {
  loading.value = true
  try {
    results.value = await Api.listResults(projectId.value)
  } finally {
    loading.value = false
  }
}
const openDownload = (row: ResultView) => {
  selected.value = row
  downloadSequence.value = 1
  downloadVisible.value = true
}
const download = async () => {
  if (!selected.value) return
  const fact = await Api.getResultDownload(selected.value.resultId, downloadSequence.value)
  const responseObject =
    fact.role === 'RESULT_DOCUMENT' ? selected.value.resultId : selected.value.responseId
  const purposeCode =
    fact.role === 'RESULT_DOCUMENT' ? 'SATISFACTION_RESULT_DOCUMENT' : `SATISFACTION_${fact.role}`
  const ticket = await FileApi.createAccessTicket(
    fact.file.artifactId,
    fact.file.versionNo,
    'DOWNLOAD',
    {
      ownerContext: 'ACC',
      objectType: fact.role === 'RESULT_DOCUMENT' ? 'SATISFACTION_RESULT' : 'SATISFACTION_RESPONSE',
      objectId: String(responseObject),
      purposeCode,
      referenceKey: fact.file.referenceKey
    }
  )
  window.open(ticket.shortLivedUrl, '_blank', 'noopener,noreferrer')
  downloadVisible.value = false
}
const invalidate = async (row: ResultView) => {
  await message.confirm('确认失效当前达标结果？历史与文件仍会保留。')
  await Api.invalidateResult(row, 'BUSINESS_REVOKED', '由满意度管理页发起失效')
  message.success('结果已失效')
  await load()
}
const openExport = () => {
  exportTask.value = undefined
  exportVisible.value = true
}
const startExport = async () => {
  if (!projectId.value || !exportFields.value.length) return
  exporting.value = true
  try {
    exportTask.value = await Api.requestResultExport(
      projectId.value,
      exportFields.value,
      includeFiles.value
    )
    schedulePoll()
  } finally {
    exporting.value = false
  }
}
const schedulePoll = () => {
  window.clearTimeout(pollTimer)
  if (
    !exportTask.value ||
    ['SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED'].includes(exportTask.value.status)
  )
    return
  pollTimer = window.setTimeout(async () => {
    exportTask.value = await Api.getExportTask(exportTask.value!.taskId)
    schedulePoll()
  }, 1500)
}
const retryExport = async () => {
  if (exportTask.value) {
    exportTask.value = await Api.retryExportTask(exportTask.value)
    schedulePoll()
  }
}
const downloadExport = async () => {
  if (exportTask.value) {
    const ticket = await Api.getExportAccessTicket(exportTask.value.taskId)
    window.open(ticket.shortLivedUrl, '_blank', 'noopener,noreferrer')
  }
}
onMounted(load)
onBeforeUnmount(() => window.clearTimeout(pollTimer))
</script>

<style scoped lang="scss">
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.panel-heading h2 {
  margin: 0;
  font-size: 18px;
}
.panel-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}
.toolbar {
  display: flex;
  gap: 8px;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 16px;
}
@media (width <= 767px) {
  .panel-heading {
    flex-direction: column;
  }
  .toolbar {
    width: 100%;
    flex-wrap: wrap;
  }
  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
