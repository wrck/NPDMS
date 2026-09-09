<template>
  <div class="panel-heading">
    <div
      ><h2>满意度判定结果</h2><p>结果版本不可修改；支持历史文件下载、正式失效和异步导出。</p></div
    >
    <div class="toolbar"
      ><el-input-number
        v-if="!context.scoped"
        v-model="projectId"
        :min="1"
        controls-position="right"
        placeholder="项目ID"
      /><el-tag v-else>项目 {{ props.projectId }}</el-tag
      ><el-button :loading="loading" @click="load">查询</el-button
      ><el-button
        type="primary"
        :disabled="!context.valid || !context.projectId"
        @click="openExport"
        >导出</el-button
      ></div
    >
  </div>
  <el-alert
    v-if="!context.valid"
    title="项目上下文无效，未查询其他项目。"
    type="warning"
    :closable="false"
  />
  <el-alert v-else-if="errorText" :title="errorText" type="error" :closable="false" />
  <el-skeleton v-else-if="loading" :rows="4" animated />
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
          v-if="!props.readonly && scope.row.resultStatus === 'EFFECTIVE' && scope.row.passed"
          link
          type="danger"
          @click="invalidate(scope.row)"
          >失效</el-button
        >
      </template>
    </el-table-column>
  </el-table>

  <el-dialog v-model="downloadVisible" title="下载结果文件" width="min(460px, 94vw)">
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
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as Api from '@/api/pms/project/satisfaction'
import * as FileApi from '@/api/pms/platform/file'
import type { ExportTask, ResultView } from '@/api/pms/project/satisfaction'
import { satisfactionProjectContext, type SatisfactionViewProps } from './projectContext'

const props = defineProps<SatisfactionViewProps>()
const emit = defineEmits<{ 'dirty-change': [value: boolean]; changed: [] }>()

const message = useMessage()
const loading = ref(false)
const projectId = ref<number>()
const context = computed(() => satisfactionProjectContext(props.projectId, projectId.value))
const errorText = ref('')
let loadSequence = 0
let contextVersion = 0
const inContext = (row: ResultView) =>
  context.value.valid && (!context.value.scoped || row.projectId === props.projectId)
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
  const sequence = ++loadSequence
  results.value = []
  errorText.value = ''
  if (!context.value.valid) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await Api.listResults(context.value.projectId)
    if (sequence === loadSequence) results.value = result
  } catch {
    if (sequence === loadSequence) errorText.value = '满意度结果加载失败，请重新查询。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
const openDownload = (row: ResultView) => {
  if (!inContext(row)) return
  selected.value = row
  downloadSequence.value = 1
  downloadVisible.value = true
}
const download = async () => {
  if (!selected.value || !inContext(selected.value)) return
  const row = selected.value
  const version = contextVersion
  const fact = await Api.getResultDownload(row.resultId, downloadSequence.value)
  if (version !== contextVersion) return
  const responseObject = fact.role === 'RESULT_DOCUMENT' ? row.resultId : row.responseId
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
  if (version !== contextVersion) return
  window.open(ticket.shortLivedUrl, '_blank', 'noopener,noreferrer')
  downloadVisible.value = false
}
const invalidate = async (row: ResultView) => {
  if (props.readonly || !inContext(row)) return
  const version = contextVersion
  await message.confirm('确认失效当前达标结果？历史与文件仍会保留。')
  if (props.readonly || !inContext(row) || version !== contextVersion) return
  await Api.invalidateResult(row, 'BUSINESS_REVOKED', '由满意度管理页发起失效')
  if (version !== contextVersion) return
  emit('changed')
  message.success('结果已失效')
  await load()
}
const openExport = () => {
  if (!context.value.valid || !context.value.projectId) return
  window.clearTimeout(pollTimer)
  exportTask.value = undefined
  exportVisible.value = true
}
const startExport = async () => {
  if (!context.value.valid || !context.value.projectId || !exportFields.value.length) return
  const version = contextVersion
  exporting.value = true
  try {
    const task = await Api.requestResultExport(
      context.value.projectId,
      exportFields.value,
      includeFiles.value
    )
    if (version !== contextVersion) return
    exportTask.value = task
    schedulePoll()
  } finally {
    if (version === contextVersion) exporting.value = false
  }
}
const schedulePoll = () => {
  window.clearTimeout(pollTimer)
  if (
    !exportTask.value ||
    ['SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED'].includes(exportTask.value.status)
  )
    return
  const version = contextVersion
  const taskId = exportTask.value.taskId
  pollTimer = window.setTimeout(async () => {
    try {
      const task = await Api.getExportTask(taskId)
      if (version !== contextVersion || exportTask.value?.taskId !== taskId) return
      exportTask.value = task
      schedulePoll()
    } catch {
      if (version === contextVersion) message.warning('导出状态读取失败，请重新查询。')
    }
  }, 1500)
}
const retryExport = async () => {
  if (exportTask.value) {
    const version = contextVersion
    const task = await Api.retryExportTask(exportTask.value)
    if (version !== contextVersion) return
    exportTask.value = task
    schedulePoll()
  }
}
const downloadExport = async () => {
  if (exportTask.value) {
    const version = contextVersion
    const ticket = await Api.getExportAccessTicket(exportTask.value.taskId)
    if (version !== contextVersion) return
    window.open(ticket.shortLivedUrl, '_blank', 'noopener,noreferrer')
  }
}
const resetDialogs = () => {
  window.clearTimeout(pollTimer)
  downloadVisible.value = exportVisible.value = exporting.value = false
  selected.value = undefined
  exportTask.value = undefined
}
watch(
  () => downloadVisible.value || exportVisible.value,
  (value) => emit('dirty-change', value),
  { immediate: true }
)
watch(
  () => props.projectId,
  () => {
    contextVersion++
    resetDialogs()
    void load()
  },
  { immediate: true, flush: 'sync' }
)
onBeforeUnmount(() => {
  loadSequence++
  contextVersion++
  window.clearTimeout(pollTimer)
})
defineExpose({
  isDirty: () => downloadVisible.value || exportVisible.value,
  discardChanges: () => {
    contextVersion++
    resetDialogs()
    return true
  }
})
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
