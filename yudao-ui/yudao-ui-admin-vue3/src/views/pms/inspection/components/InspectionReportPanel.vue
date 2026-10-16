<template>
  <section class="report-panel" aria-label="巡检报告">
    <header class="inspection-card__header">
      <h2>巡检报告基本信息</h2>
      <el-tag type="info">{{ task.reports.length }} 个版本</el-tag>
    </header>
    <el-alert
      v-if="!currentReport"
      title="尚无当前报告"
      type="info"
      :closable="false"
      class="mb-16px"
    />
    <el-empty v-if="!orderedReports.length" description="尚未生成或提交报告" :image-size="80" />
    <el-collapse v-if="orderedReports.length" v-model="reportSections" class="report-versions">
      <el-collapse-item
        v-for="report in orderedReports"
        :key="String(report.id)"
        :name="String(report.id)"
        class="report-version"
      >
        <template #title
          ><div class="section-heading report-version-heading">
            <h3>报告 V{{ report.revisionNo ?? '未提供' }}</h3>
            <div class="report-tags">
              <el-tag v-if="isCurrent(report)" type="success">当前报告</el-tag>
              <el-tag v-else type="info">历史版本</el-tag>
              <el-tag :type="report.statusCode === 'FAILED' ? 'danger' : 'info'">{{
                statusLabel(report.statusCode)
              }}</el-tag>
            </div>
          </div></template
        >
        <el-descriptions :column="smallScreen ? 1 : 2" class="mt-12px">
          <el-descriptions-item label="来源">{{
            sourceLabel(report.sourceCode)
          }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{
            formatDate(report.generatedAt) || '未提供'
          }}</el-descriptions-item>
          <el-descriptions-item v-if="report.templateVersion" label="模板版本">{{
            report.templateVersion
          }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{
            report.explanation || '未填写说明'
          }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="report.failureMessage"
          :title="report.failureMessage"
          type="error"
          :closable="false"
          class="mb-12px"
        />
        <el-table
          :data="report.files || []"
          :row-key="(file: ReportFileView) => String(file.id)"
          empty-text="此报告版本没有可用文件记录"
        >
          <el-table-column type="expand" width="42"
            ><template #default="{ row }">
              <el-descriptions :column="smallScreen ? 1 : 2" class="report-source">
                <el-descriptions-item label="报告记录">{{ report.id }}</el-descriptions-item>
                <el-descriptions-item label="执行尝试">{{
                  report.attemptId ?? '未提供'
                }}</el-descriptions-item>
                <el-descriptions-item label="文件记录">{{ row.id }}</el-descriptions-item>
                <el-descriptions-item label="平台工件">{{
                  row.artifactId ?? '未提供'
                }}</el-descriptions-item>
                <el-descriptions-item label="文件引用">{{
                  row.referenceKey || '未提供'
                }}</el-descriptions-item>
                <el-descriptions-item label="来源结果" :span="smallScreen ? 1 : 2">{{
                  report.resultIds?.length ? report.resultIds.map(String).join('、') : '未提供'
                }}</el-descriptions-item>
              </el-descriptions>
            </template></el-table-column
          >
          <el-table-column prop="formatCode" label="格式" width="90" />
          <el-table-column label="文件名" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.fileName || '未提供文件名' }}</template>
          </el-table-column>
          <el-table-column prop="fileVersionNo" label="文件版本" width="100" />
          <el-table-column label="操作" min-width="140">
            <template #default="{ row }">
              <el-button
                v-if="canDownload"
                link
                type="primary"
                :disabled="!hasReference(row)"
                @click="download(report, row)"
                >下载此版本文件</el-button
              >
              <span v-if="!hasReference(row)" class="help-text">文件引用不完整</span>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <el-collapse v-if="canGenerate" v-model="writeSections" class="report-writing">
      <el-collapse-item name="write" title="生成或补充巡检报告" :disabled="currentBusy">
        <div class="report-mode-titles" role="group" aria-label="报告提交方式">
          <h3 class="inspection-section-title" :class="{ 'is-active': reportMode === 'GENERATE' }">
            <button
              type="button"
              :aria-pressed="reportMode === 'GENERATE'"
              :disabled="disabled || currentBusy"
              @click="reportMode = 'GENERATE'"
              >生成基础报告</button
            >
          </h3>
          <h3 class="inspection-section-title" :class="{ 'is-active': reportMode === 'MANUAL' }">
            <button
              type="button"
              :aria-pressed="reportMode === 'MANUAL'"
              :disabled="disabled || currentBusy"
              @click="reportMode = 'MANUAL'"
              >提交人工报告</button
            >
          </h3>
        </div>
        <section v-show="reportMode === 'GENERATE'" class="write-section" aria-label="生成基础报告">
          <p class="help-text">按当前检测结果生成 PDF、DOCX 和 XML 文件，保存为新的报告版本。</p>
          <el-form label-position="top" :disabled="disabled || currentBusy">
            <el-form-item label="生成说明"
              ><el-input
                v-model="generationExplanation"
                type="textarea"
                :rows="2"
                maxlength="2000"
                show-word-limit
                placeholder="可填写本次生成的说明"
            /></el-form-item>
            <el-button type="primary" :disabled="disabled || currentBusy" @click="generate"
              >生成 PDF / DOCX / XML</el-button
            >
          </el-form>
        </section>

        <section v-show="reportMode === 'MANUAL'" class="write-section" aria-label="提交人工报告">
          <p class="help-text"
            >至少提交一种报告格式和人工报告说明，每种格式保留一个文件。支持
            PDF、DOCX、DOC、XML，实际文件格式由服务端校验。</p
          >
          <el-form label-position="top" :disabled="disabled || currentBusy">
            <el-form-item label="人工报告说明" required
              ><el-input
                v-model="manualExplanation"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
                placeholder="填写报告补充或替换说明"
            /></el-form-item>
          </el-form>
          <template v-for="slot in uploadSlots" :key="slot.referenceKey">
            <div v-show="slot.referenceKey === activeReferenceKey">
              <PmsFileUploader
                v-if="canUpload"
                :ref="(instance) => setUploader(slot, instance)"
                hide-submit
                accept=".pdf,.docx,.doc,.xml"
                owner-context="SRV"
                object-type="INSPECTION_TASK"
                :object-id="String(slot.taskId)"
                purpose-code="REPORT"
                :reference-key="slot.referenceKey"
                category-code="INSPECTION_REPORT"
                :disabled="disabled || slot.uploading || slot.resolving"
                @completed="(selection) => completeUpload({ slot, selection })"
              />
              <el-button
                v-if="canUpload"
                class="mt-12px"
                :loading="slot.uploading || slot.resolving"
                :disabled="disabled || !slot.uploader?.hasPendingFile()"
                @click="upload(slot)"
                >上传人工报告文件</el-button
              >
            </div>
          </template>
          <p v-if="!canUpload" class="help-text">没有平台文件上传权限。</p>
          <div class="section-heading draft-heading"
            ><h4>待提交清单</h4><el-tag type="info">{{ draftFiles.length }} 个文件</el-tag></div
          >
          <el-table :data="draftFiles" row-key="uid" empty-text="尚未上传人工报告文件">
            <el-table-column label="文件名" min-width="200"
              ><template #default="{ row }">{{
                row.fileName || '正在读取平台文件名称'
              }}</template></el-table-column
            >
            <el-table-column label="格式" width="90"
              ><template #default="{ row }">{{
                row.formatCode || '待核对'
              }}</template></el-table-column
            >
            <el-table-column label="状态" min-width="200"
              ><template #default="{ row }">{{
                row.state === 'READY'
                  ? '待提交报告'
                  : row.state === 'READING'
                    ? '正在核对文件'
                    : row.error
              }}</template></el-table-column
            >
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button
                  v-if="row.state === 'ERROR' || row.state === 'INVALID'"
                  link
                  type="primary"
                  :disabled="disabled || currentBusy"
                  @click="resolveFile(row)"
                  >重新核对</el-button
                >
                <el-button
                  link
                  type="danger"
                  :disabled="disabled || currentBusy"
                  @click="removeFile(row.uid)"
                  >移除选择</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <p class="help-text"
            >上传成功后仍需提交人工报告。移除选择仅清理本次待提交清单，平台文件保留。</p
          >
          <el-button
            type="primary"
            :disabled="disabled || currentBusy || !manualReady"
            @click="submitManual"
            >提交人工报告</el-button
          >
        </section>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { generateUUID } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { PmsFileUploader, type FileSelection } from '@/components/PmsFileArtifact'
import { getArtifact } from '@/api/pms/platform/file/version-reference'
import type { FileBusinessKey } from '@/api/pms/platform/file'
import type {
  FileReference,
  ReportView,
  ReportFileView,
  TaskDetail,
  WireLong
} from '@/api/pms/inspection/tasks'

const props = defineProps<{ task: TaskDetail; disabled: boolean }>()
const reportSections = ref<Array<string | number>>([])
const writeSections = ref<Array<string | number>>([])
const reportMode = ref<'GENERATE' | 'MANUAL'>('GENERATE')
type FormatCode = 'PDF' | 'DOCX' | 'DOC' | 'XML'
const emit = defineEmits<{
  generate: [value: { explanation: string }]
  submitManual: [
    value: { explanation: string; files: { formatCode: FormatCode; file: FileReference }[] }
  ]
  download: [value: { report: ReportView; file: ReportFileView }]
  draftChanged: [value: boolean]
}>()
const message = useMessage()
const smallScreen = useMediaQuery('(max-width: 768px)')
const canGenerate = computed(() => checkPermi(['pms:inspection-report:generate']))
const canUpload = computed(() => checkPermi(['pms:file:upload']))
const canDownload = computed(() => checkPermi(['pms:file:download']))
const generationExplanation = ref('')
const manualExplanation = ref('')
let epoch = 0
let mounted = true
interface UploadSlot {
  epoch: number
  taskId: WireLong
  taskCode: string
  referenceKey: string
  uploader?: { submit: () => Promise<void>; hasPendingFile: () => boolean }
  uploading: boolean
  resolving: boolean
}
interface DraftFile {
  uid: string
  slot: UploadSlot
  selection: FileSelection
  fileName: string
  formatCode?: FormatCode
  file: FileReference
  state: 'READING' | 'READY' | 'ERROR' | 'INVALID'
  error: string
}
const uploadSlots = ref<UploadSlot[]>([])
const activeReferenceKey = ref('')
const draftFiles = ref<DraftFile[]>([])
const currentReport = computed(() =>
  props.task.reports.find((report) => String(report.id) === String(props.task.currentReportId))
)
watch(
  () => `${props.task.id}:${currentReport.value?.id ?? ''}`,
  () => {
    reportSections.value = currentReport.value ? [String(currentReport.value.id)] : []
    writeSections.value = currentReport.value ? [] : ['write']
  },
  { immediate: true }
)
const isCurrent = (report: ReportView) => String(report.id) === String(props.task.currentReportId)
const orderedReports = computed(() =>
  [...props.task.reports].sort(
    (left, right) =>
      (isCurrent(right) ? 1 : 0) - (isCurrent(left) ? 1 : 0) ||
      (right.revisionNo ?? 0) - (left.revisionNo ?? 0)
  )
)
const statusLabels: Record<string, string> = {
  AVAILABLE: '文件已就绪',
  FAILED: '生成失败',
  GENERATING: '正在生成'
}
const statusLabel = (status: string) => statusLabels[status] || status
const sourceLabel = (source?: string) =>
  source === 'PLATFORM' ? '平台基础模板' : source === 'MANUAL' ? '人工报告' : source || '未提供'
const currentSlot = computed(() =>
  uploadSlots.value.find((slot) => slot.referenceKey === activeReferenceKey.value)
)
const currentBusy = computed(
  () =>
    !!currentSlot.value?.uploading ||
    !!currentSlot.value?.resolving ||
    draftFiles.value.some((file) => file.state === 'READING')
)
const manualReady = computed(
  () =>
    !!manualExplanation.value.trim() &&
    draftFiles.value.length > 0 &&
    draftFiles.value.every((file) => file.state === 'READY')
)
const dirty = computed(
  () =>
    !!generationExplanation.value ||
    !!manualExplanation.value ||
    !!draftFiles.value.length ||
    currentBusy.value ||
    !!currentSlot.value?.uploader?.hasPendingFile()
)
watch(dirty, (value) => emit('draftChanged', value), { immediate: true, flush: 'sync' })

function belongsToCurrent(slot: UploadSlot) {
  return mounted && slot.epoch === epoch && String(slot.taskId) === String(props.task.id)
}
function originalTaskNotice(slot: UploadSlot) {
  message.warning(`本次文件操作属于原巡检任务 ${slot.taskCode}，结果未加入当前任务的报告草稿。`)
}
function newSlot() {
  const slot: UploadSlot = {
    epoch,
    taskId: props.task.id,
    taskCode: props.task.taskCode,
    referenceKey: `manual-report:${generateUUID()}`,
    uploading: false,
    resolving: false
  }
  uploadSlots.value.push(slot)
  activeReferenceKey.value = slot.referenceKey
}
function cleanupSlots() {
  uploadSlots.value = uploadSlots.value.filter(
    (slot) => slot.referenceKey === activeReferenceKey.value || slot.uploading || slot.resolving
  )
}
watch(
  () => [String(props.task.id), String(props.task.version)],
  () => {
    ++epoch
    generationExplanation.value = ''
    manualExplanation.value = ''
    draftFiles.value = []
    newSlot()
    cleanupSlots()
  },
  { immediate: true }
)
function setUploader(slot: UploadSlot, instance: unknown) {
  slot.uploader = instance ? (instance as UploadSlot['uploader']) : undefined
}
function keyOf(slot: UploadSlot): FileBusinessKey {
  return {
    ownerContext: 'SRV',
    objectType: 'INSPECTION_TASK',
    objectId: String(slot.taskId),
    purposeCode: 'REPORT',
    referenceKey: slot.referenceKey
  }
}
async function upload(slot: UploadSlot) {
  if (
    props.disabled ||
    !canGenerate.value ||
    !canUpload.value ||
    !belongsToCurrent(slot) ||
    !slot.uploader?.hasPendingFile() ||
    slot.uploading ||
    slot.resolving
  )
    return
  slot.uploading = true
  try {
    await slot.uploader.submit()
    if (!mounted) originalTaskNotice(slot)
  } catch {
    if (!belongsToCurrent(slot)) originalTaskNotice(slot)
    else message.warning('文件上传结果尚未确认，请在当前上传槽重试，报告草稿已保留。')
  } finally {
    slot.uploading = false
    cleanupSlots()
  }
}
async function completeUpload(input: { slot: UploadSlot; selection: FileSelection }) {
  const { slot, selection } = input
  if (!belongsToCurrent(slot)) {
    originalTaskNotice(slot)
    return
  }
  if (selection.referenceKey !== slot.referenceKey) {
    message.error('平台文件引用与本次上传槽不一致，未加入报告草稿')
    return
  }
  slot.resolving = true
  const file: DraftFile = {
    uid: generateUUID(),
    slot,
    selection,
    fileName: '',
    state: 'READING',
    error: '',
    file: {
      artifactId: selection.artifactId,
      versionNo: selection.versionNo,
      referenceKey: selection.referenceKey
    }
  }
  draftFiles.value.push(file)
  try {
    await resolveFile(draftFiles.value[draftFiles.value.length - 1])
  } finally {
    slot.resolving = false
    if (belongsToCurrent(slot)) newSlot()
    cleanupSlots()
  }
}
async function resolveFile(file: DraftFile) {
  if (!belongsToCurrent(file.slot)) {
    originalTaskNotice(file.slot)
    return
  }
  file.state = 'READING'
  file.error = ''
  try {
    const artifact = await getArtifact(file.selection.artifactId, keyOf(file.slot))
    if (!belongsToCurrent(file.slot)) {
      originalTaskNotice(file.slot)
      return
    }
    if (!draftFiles.value.some((item) => item.uid === file.uid)) return
    file.fileName = artifact.name
    if (
      String(artifact.artifactId) !== String(file.selection.artifactId) ||
      artifact.reference.referenceKey !== file.selection.referenceKey ||
      artifact.reference.versionNo !== file.selection.versionNo
    ) {
      file.state = 'INVALID'
      file.error = '平台文件身份或版本不一致，请移除选择后重新上传'
      return
    }
    const dot = artifact.name.lastIndexOf('.')
    const extension = artifact.name.slice(dot + 1).toUpperCase()
    if (
      dot <= Math.max(0, artifact.name.lastIndexOf('/'), artifact.name.lastIndexOf('\\')) ||
      !['PDF', 'DOCX', 'DOC', 'XML'].includes(extension)
    ) {
      file.state = 'INVALID'
      file.error = '实际文件名不是 PDF、DOCX、DOC 或 XML 格式'
      return
    }
    file.formatCode = extension as FormatCode
    if (
      draftFiles.value.some(
        (item) =>
          item.uid !== file.uid && item.state === 'READY' && item.formatCode === file.formatCode
      )
    ) {
      file.state = 'INVALID'
      file.error = '此格式已有待提交文件，请移除其中一个后重新核对'
      return
    }
    file.state = 'READY'
  } catch {
    if (!belongsToCurrent(file.slot)) {
      originalTaskNotice(file.slot)
      return
    }
    file.state = 'ERROR'
    file.error = '文件已上传，平台名称读取失败；请重新核对'
  }
}
function removeFile(uid: string) {
  if (props.disabled || currentBusy.value) return
  draftFiles.value = draftFiles.value.filter((file) => file.uid !== uid)
}
function hasReference(file: ReportFileView) {
  return file.artifactId != null && file.fileVersionNo != null && !!file.referenceKey
}
function download(report: ReportView, file: ReportFileView) {
  if (canDownload.value && hasReference(file)) emit('download', { report, file })
}
function generate() {
  if (!props.disabled && canGenerate.value && !currentBusy.value)
    emit('generate', { explanation: generationExplanation.value.trim() })
}
function submitManual() {
  if (props.disabled || !canGenerate.value || currentBusy.value || !manualReady.value) return
  emit('submitManual', {
    explanation: manualExplanation.value.trim(),
    files: draftFiles.value.map((file) => ({
      formatCode: file.formatCode as FormatCode,
      file: { ...file.file }
    }))
  })
}
onBeforeUnmount(() => {
  mounted = false
  ++epoch
  emit('draftChanged', false)
})
</script>

<style scoped>
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.section-heading h2,
.section-heading h3,
.section-heading h4 {
  margin: 0;
}
.report-panel h2 {
  font-size: 15px;
}
.report-panel h3 {
  font-size: 14px;
}
.report-panel h4 {
  font-size: 13px;
}
.report-version {
  padding: 0;
  margin: 0;
}
.report-versions,
.report-writing {
  --el-collapse-border-color: #f0f2f5;
  --el-collapse-header-height: 46px;
}
.report-version-heading {
  flex: 1;
  margin-right: 12px;
}
.report-version :deep(.el-collapse-item__content) {
  padding: 0 0 16px;
}
.report-source {
  padding: 12px 16px;
  background: #fafbfc;
  overflow-wrap: anywhere;
}
.report-writing {
  margin-top: 16px;
}
.report-writing :deep(.el-collapse-item__header) {
  color: #1a2332;
  font-size: 14px;
  font-weight: 600;
}
.report-writing :deep(.el-form-item) {
  margin-bottom: 14px;
}
.inspection-section-title {
  display: inline-block;
  padding-bottom: 6px;
  margin: 0 0 10px;
  color: #1a2332;
  border-bottom: 2px solid var(--inspection-blue, #4a90e2);
}
.report-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.report-mode-titles {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding-top: 16px;
}
.report-mode-titles .inspection-section-title:not(.is-active) {
  color: var(--el-text-color-secondary);
  border-bottom-color: transparent;
}
.report-mode-titles button {
  padding: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
  background: none;
  border: 0;
}
.report-mode-titles button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.help-text {
  margin: 8px 0 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.draft-heading {
  margin: 16px 0 12px;
}
@media (max-width: 768px) {
  .report-version-heading {
    gap: 4px;
  }
  .report-tags {
    gap: 4px;
  }
  .report-tags :deep(.el-tag) {
    height: 20px;
  }
}
</style>
