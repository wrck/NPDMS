<template>
  <section class="issue-panel" aria-label="巡检问题处理">
    <header class="inspection-card__header">
      <h2><Icon icon="ep:warning" /> 问题基本信息</h2>
      <el-tag :type="issue.statusCode === 'CLOSED' ? 'success' : 'warning'">{{
        statusLabel(issue.statusCode)
      }}</el-tag>
    </header>
    <el-descriptions :column="smallScreen ? 1 : 2" class="issue-summary" border>
      <el-descriptions-item label="所属任务"
        >{{ issue.taskCode }} · {{ issue.taskName }}</el-descriptions-item
      >
      <el-descriptions-item label="问题编号">{{ issue.id }}</el-descriptions-item>
      <el-descriptions-item label="问题描述">{{
        issue.description || '未提供'
      }}</el-descriptions-item>
      <el-descriptions-item label="责任人">{{
        userLabel({ name: issue.responsibleName, id: issue.responsibleUserId })
      }}</el-descriptions-item>
      <el-descriptions-item label="严重级别">{{
        severityLabel(issue.severityCode)
      }}</el-descriptions-item>
      <el-descriptions-item label="计划完成时间">{{
        formatDate(issue.dueAt) || '未提供'
      }}</el-descriptions-item>
      <el-descriptions-item v-if="issue.closureDescription" label="闭环申请说明">{{
        issue.closureDescription
      }}</el-descriptions-item>
      <el-descriptions-item v-if="issue.closureRequestedAt" label="申请时间">{{
        formatDate(issue.closureRequestedAt)
      }}</el-descriptions-item>
      <el-descriptions-item v-if="issue.closedAt" label="闭环时间">{{
        formatDate(issue.closedAt)
      }}</el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="archived"
      title="所属任务已归档，问题与处理历史只读。"
      type="info"
      :closable="false"
      class="mt-16px"
    />
    <el-alert
      v-else-if="issue.taskStatus === 'CANCELLED' && issue.statusCode !== 'CLOSED'"
      title="所属任务已取消，未闭问题仍可按责任继续处理。"
      type="info"
      :closable="false"
      class="mt-16px"
    />

    <section class="history-section" aria-label="处理历史">
      <h3 class="inspection-section-title">处理历史与证据</h3>
      <el-empty v-if="!issue.remediations.length" description="尚无处理记录" :image-size="64" />
      <article v-for="record in history" :key="String(record.id)" class="history-record">
        <div class="section-heading">
          <h4>{{ actionLabel(record.actionCode) }}</h4>
          <span class="help-text"
            >{{ formatDate(record.actionAt) || '时间未提供' }} · 操作人
            {{ userLabel({ name: record.actorName, id: record.actorId }) }}</span
          >
        </div>
        <p class="record-content">{{ record.content || '未提供说明' }}</p>
        <el-table
          v-if="evidenceFor(record.id).length"
          :data="evidenceFor(record.id)"
          :row-key="(file: EvidenceView) => String(file.id)"
        >
          <el-table-column prop="fileName" label="处理证据" min-width="220" show-overflow-tooltip />
          <el-table-column prop="fileVersionNo" label="文件版本" width="100" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }"
              ><el-button
                v-hasPermi="['pms:file:download']"
                link
                type="primary"
                @click="download(row)"
                >下载此版本证据</el-button
              ></template
            >
          </el-table-column>
        </el-table>
      </article>
    </section>

    <section v-if="canProcess" class="write-section" aria-label="追加问题处理记录">
      <h3 class="inspection-section-title">追加处理记录</h3>
      <el-form label-position="top" :disabled="disabled || currentBusy">
        <el-form-item label="处理内容" required
          ><el-input
            v-model="draft.content"
            type="textarea"
            :rows="4"
            maxlength="8000"
            show-word-limit
        /></el-form-item>
      </el-form>
      <p class="help-text"
        >可附处理证据，每次最多 20 个、单文件不超过 50MiB。支持 PDF、PNG、JPEG、纯文本、DOC 和
        DOCX，格式由平台策略校验。</p
      >
    </section>
    <template v-for="slot in uploadSlots" :key="slot.referenceKey">
      <div v-show="canProcess && slot.referenceKey === activeReferenceKey">
        <PmsFileUploader
          v-if="canUpload"
          :ref="(instance) => setUploader(slot, instance)"
          hide-submit
          :accept="evidenceAccept"
          owner-context="SRV"
          object-type="INSPECTION_TASK"
          :object-id="String(slot.context.taskId)"
          purpose-code="EVIDENCE"
          :reference-key="slot.referenceKey"
          category-code="INSPECTION_EVIDENCE"
          :disabled="
            disabled || !canProcess || slot.uploading || slot.resolving || draft.files.length >= 20
          "
          @completed="(selection) => completeUpload({ slot, selection })"
        />
        <el-button
          v-if="canUpload"
          v-hasPermi="['pms:file:upload']"
          class="mt-12px"
          :loading="slot.uploading || slot.resolving"
          :disabled="
            disabled || currentBusy || draft.files.length >= 20 || !slot.uploader?.hasPendingFile()
          "
          @click="upload(slot)"
          >上传处理证据</el-button
        >
      </div>
    </template>
    <section v-if="canProcess" class="evidence-draft">
      <p v-if="!canUpload" class="help-text">没有平台文件上传权限，可先录入处理文字。</p>
      <div class="section-heading"
        ><h4>本次待提交证据</h4><el-tag type="info">{{ draft.files.length }} / 20</el-tag></div
      >
      <el-table :data="draft.files" row-key="uid" empty-text="本次未选择处理证据">
        <el-table-column label="文件名" min-width="200"
          ><template #default="{ row }">{{
            row.fileName || '平台文件名称待核对'
          }}</template></el-table-column
        >
        <el-table-column label="状态" min-width="220"
          ><template #default="{ row }">{{ fileState(row) }}</template></el-table-column
        >
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button
              v-if="row.state === 'ERROR'"
              link
              type="primary"
              :disabled="disabled || currentBusy"
              @click="resolveEvidence({ context: draft, file: row })"
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
      <p class="help-text">平台上传不等于处理记录已保存；移除选择仅清理本次清单，平台文件保留。</p>
      <el-button
        v-hasPermi="['pms:service-issue:remediate']"
        type="primary"
        :disabled="disabled || currentBusy || !processReady"
        @click="recordProcess"
        >保存处理记录</el-button
      >
    </section>

    <section v-if="canProcess" class="write-section" aria-label="申请问题闭环">
      <h3 class="inspection-section-title">申请闭环</h3>
      <p class="help-text">申请前需有已保存的处理记录和有效处理证据，服务端会重新核对。</p>
      <el-form label-position="top" :disabled="disabled || currentBusy">
        <el-form-item label="闭环申请说明" required
          ><el-input
            v-model="draft.closureDescription"
            type="textarea"
            :rows="3"
            maxlength="8000"
            show-word-limit
        /></el-form-item>
      </el-form>
      <el-button
        v-hasPermi="['pms:service-issue:remediate']"
        type="success"
        :disabled="disabled || currentBusy || !draft.closureDescription.trim()"
        @click="requestClosure"
        >申请闭环</el-button
      >
    </section>

    <section v-if="canConfirm" class="write-section" aria-label="确认或退回问题闭环">
      <h3 class="inspection-section-title">确认或退回闭环</h3>
      <el-form label-position="top" :disabled="disabled || currentBusy">
        <el-form-item label="确认或退回说明" required
          ><el-input
            v-model="draft.confirmationDescription"
            type="textarea"
            :rows="3"
            maxlength="8000"
            show-word-limit
        /></el-form-item>
      </el-form>
      <el-button
        v-hasPermi="['pms:service-issue:confirm']"
        type="success"
        :disabled="disabled || currentBusy || !draft.confirmationDescription.trim()"
        @click="confirmClosure('confirm-closure')"
        >确认闭环</el-button
      >
      <el-button
        v-hasPermi="['pms:service-issue:confirm']"
        type="warning"
        :disabled="disabled || currentBusy || !draft.confirmationDescription.trim()"
        @click="confirmClosure('return-closure')"
        >退回申请</el-button
      >
    </section>

    <section
      v-if="retiredDrafts.some((context) => context.files.length)"
      class="write-section"
      aria-label="原问题的上传结果"
    >
      <h3 class="inspection-section-title">原问题的上传结果</h3>
      <p class="help-text"
        >以下上传结果按原任务和问题保留，不会提交到当前问题；平台文件仍保留原业务引用。</p
      >
      <article
        v-for="context in retiredDrafts.filter((item) => item.files.length)"
        :key="context.epoch"
        class="history-record"
      >
        <h4>{{ context.taskCode }} · 问题 {{ context.issueId }} · 原版本 {{ context.version }}</h4>
        <ul
          ><li v-for="file in context.files" :key="file.uid"
            >{{ file.fileName || '文件名称待核对' }} · {{ fileState(file) }}</li
          ></ul
        >
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { generateUUID } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { getStrDictOptions } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { PmsFileUploader, type FileSelection } from '@/components/PmsFileArtifact'
import { getArtifact } from '@/api/pms/platform/file/version-reference'
import type { FileBusinessKey } from '@/api/pms/platform/file'
import type { EvidenceView, FileReference, IssueDetail, WireLong } from '@/api/pms/inspection/tasks'

const props = defineProps<{ issue: IssueDetail; disabled: boolean }>()
type Action = 'record-process' | 'request-closure' | 'confirm-closure' | 'return-closure'
const emit = defineEmits<{
  action: [
    value: {
      action: Action
      data: { content?: string; description?: string; evidence?: FileReference[] }
    }
  ]
  download: [value: EvidenceView]
  draftChanged: [value: boolean]
}>()
const message = useMessage()
const smallScreen = useMediaQuery('(max-width: 768px)')
const archived = computed(() => props.issue.taskStatus === 'ARCHIVED')
const canProcess = computed(
  () =>
    !archived.value &&
    props.issue.responsible &&
    ['PENDING', 'PROCESSING'].includes(props.issue.statusCode) &&
    checkPermi(['pms:service-issue:remediate'])
)
const canConfirm = computed(
  () =>
    !archived.value &&
    props.issue.canConfirm &&
    props.issue.statusCode === 'CLOSURE_PENDING' &&
    checkPermi(['pms:service-issue:confirm'])
)
const canUpload = computed(() => checkPermi(['pms:file:upload']))
// InspectionFilePolicy.EVIDENCE_MEDIA and its 52_428_800-byte limit are authoritative.
const evidenceAccept =
  'application/pdf,image/png,image/jpeg,text/plain,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'
const statusLabels: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  CLOSURE_PENDING: '待确认闭环',
  CLOSED: '已闭环'
}
const actionLabels: Record<string, string> = {
  PROCESS: '处理记录',
  REQUEST_CLOSURE: '申请闭环',
  CONFIRM_CLOSURE: '确认闭环',
  RETURN_CLOSURE: '退回闭环申请'
}
const statusLabel = (value: string) => statusLabels[value] || value
const actionLabel = (value?: string) => (value ? actionLabels[value] || value : '处理记录')
const severities = computed(() => getStrDictOptions('pms_inspection_rule_severity'))
const severityLabel = (code?: string) =>
  severities.value.find((item) => item.value === code)?.label || code || '未提供'
const userLabel = (user: { name?: string; id?: WireLong }) => {
  const name = user.name?.trim()
  return user.id != null ? `${name || '用户'}（编号${user.id}）` : name || '未提供'
}
const history = computed(() =>
  [...props.issue.remediations].sort((left, right) => (right.actionAt ?? 0) - (left.actionAt ?? 0))
)
const evidenceFor = (id: WireLong) =>
  props.issue.evidence.filter((file) => String(file.remediationId) === String(id))
interface DraftEvidence {
  uid: string
  selection: FileSelection
  file: FileReference
  fileName: string
  state: 'READING' | 'READY' | 'ERROR' | 'INVALID'
  error: string
}
interface DraftContext {
  epoch: number
  taskId: WireLong
  taskCode: string
  issueId: WireLong
  version: WireLong
  content: string
  closureDescription: string
  confirmationDescription: string
  files: DraftEvidence[]
  noticeSent: boolean
}
interface UploadSlot {
  context: DraftContext
  referenceKey: string
  uploading: boolean
  resolving: boolean
  uploader?: { submit: () => Promise<void>; hasPendingFile: () => boolean }
}
let epoch = 0
let mounted = true
function emptyDraft(): DraftContext {
  return {
    epoch,
    taskId: props.issue.taskId,
    taskCode: props.issue.taskCode,
    issueId: props.issue.id,
    version: props.issue.version,
    content: '',
    closureDescription: '',
    confirmationDescription: '',
    files: [],
    noticeSent: false
  }
}
const draft = ref<DraftContext>(emptyDraft())
const retiredDrafts = ref<DraftContext[]>([])
const uploadSlots = ref<UploadSlot[]>([])
const activeReferenceKey = ref('')
const currentSlot = computed(() =>
  uploadSlots.value.find((slot) => slot.referenceKey === activeReferenceKey.value)
)
const currentBusy = computed(
  () =>
    !!currentSlot.value?.uploading ||
    !!currentSlot.value?.resolving ||
    draft.value.files.some((file) => file.state === 'READING')
)
const processReady = computed(
  () =>
    !!draft.value.content.trim() &&
    draft.value.files.length <= 20 &&
    draft.value.files.every((file) => file.state === 'READY')
)
const dirty = computed(
  () =>
    !!draft.value.content ||
    !!draft.value.closureDescription ||
    !!draft.value.confirmationDescription ||
    !!draft.value.files.length ||
    currentBusy.value ||
    !!currentSlot.value?.uploader?.hasPendingFile()
)
watch(dirty, (value) => emit('draftChanged', value), { immediate: true, flush: 'sync' })

function belongsToCurrent(context: DraftContext) {
  return (
    mounted &&
    context.epoch === draft.value.epoch &&
    String(context.taskId) === String(props.issue.taskId) &&
    String(context.issueId) === String(props.issue.id)
  )
}
function originalIssueNotice(context: DraftContext) {
  if (context.noticeSent) return
  context.noticeSent = true
  message.warning(
    `本次文件操作属于原任务 ${context.taskCode}、问题 ${context.issueId}，按原业务引用保留，未加入当前问题。`
  )
}
function newSlot(context: DraftContext) {
  const referenceKey = `issue:${context.issueId}:${generateUUID()}`
  uploadSlots.value.push({ context, referenceKey, uploading: false, resolving: false })
  activeReferenceKey.value = referenceKey
}
function cleanupSlots() {
  uploadSlots.value = uploadSlots.value.filter(
    (slot) => slot.referenceKey === activeReferenceKey.value || slot.uploading || slot.resolving
  )
}
watch(
  () => [String(props.issue.taskId), String(props.issue.id), String(props.issue.version)],
  () => {
    const old = draft.value
    const pending = uploadSlots.value.some(
      (slot) => slot.context.epoch === old.epoch && (slot.uploading || slot.resolving)
    )
    old.content = ''
    old.closureDescription = ''
    old.confirmationDescription = ''
    old.files = old.files.filter((file) => file.state === 'READING')
    if (pending || old.files.length) retiredDrafts.value.push(old)
    ++epoch
    draft.value = emptyDraft()
    newSlot(draft.value)
    cleanupSlots()
  },
  { immediate: true }
)
function setUploader(slot: UploadSlot, instance: unknown) {
  slot.uploader = instance ? (instance as UploadSlot['uploader']) : undefined
}
function keyOf(input: { context: DraftContext; referenceKey: string }): FileBusinessKey {
  return {
    ownerContext: 'SRV',
    objectType: 'INSPECTION_TASK',
    objectId: String(input.context.taskId),
    purposeCode: 'EVIDENCE',
    referenceKey: input.referenceKey
  }
}
async function upload(slot: UploadSlot) {
  if (
    props.disabled ||
    !canProcess.value ||
    !canUpload.value ||
    currentBusy.value ||
    draft.value.files.length >= 20 ||
    !belongsToCurrent(slot.context) ||
    !slot.uploader?.hasPendingFile()
  )
    return
  slot.uploading = true
  try {
    await slot.uploader.submit()
    if (!mounted) originalIssueNotice(slot.context)
  } catch {
    if (!belongsToCurrent(slot.context)) originalIssueNotice(slot.context)
    else message.warning('证据上传结果尚未确认，请在原上传槽重试，处理草稿已保留。')
  } finally {
    slot.uploading = false
    cleanupSlots()
  }
}
async function completeUpload(input: { slot: UploadSlot; selection: FileSelection }) {
  const { slot, selection } = input
  const context = slot.context
  if (selection.referenceKey !== slot.referenceKey) {
    message.error('平台证据引用与本次上传槽不一致')
    return
  }
  if (
    context.files.some(
      (file) =>
        file.file.referenceKey === selection.referenceKey &&
        file.file.versionNo === selection.versionNo
    )
  )
    return
  slot.resolving = true
  context.files.push({
    uid: generateUUID(),
    selection,
    file: {
      artifactId: selection.artifactId,
      versionNo: selection.versionNo,
      referenceKey: selection.referenceKey
    },
    fileName: '',
    state: 'READING',
    error: ''
  })
  if (!belongsToCurrent(context)) originalIssueNotice(context)
  try {
    await resolveEvidence({ context, file: context.files[context.files.length - 1] })
  } finally {
    slot.resolving = false
    if (belongsToCurrent(context)) newSlot(context)
    cleanupSlots()
  }
}
async function resolveEvidence(input: { context: DraftContext; file: DraftEvidence }) {
  const { context, file } = input
  file.state = 'READING'
  file.error = ''
  try {
    const artifact = await getArtifact(
      file.selection.artifactId,
      keyOf({ context, referenceKey: file.selection.referenceKey })
    )
    if (!context.files.some((item) => item.uid === file.uid)) return
    file.fileName = artifact.name
    if (
      String(artifact.artifactId) !== String(file.selection.artifactId) ||
      artifact.reference.referenceKey !== file.selection.referenceKey ||
      artifact.reference.versionNo !== file.selection.versionNo
    ) {
      file.state = 'INVALID'
      file.error = '平台证据身份或版本不一致，请移除选择后重新上传'
    } else {
      file.state = 'READY'
    }
  } catch {
    file.state = 'ERROR'
    file.error = '文件已上传，平台名称读取失败；请重新核对'
  } finally {
    if (!belongsToCurrent(context)) originalIssueNotice(context)
  }
}
function fileState(file: DraftEvidence) {
  return file.state === 'READY'
    ? '待提交处理记录'
    : file.state === 'READING'
      ? '正在核对平台文件'
      : file.error
}
function removeFile(uid: string) {
  if (props.disabled || currentBusy.value || !canProcess.value) return
  draft.value.files = draft.value.files.filter((file) => file.uid !== uid)
}
function download(file: EvidenceView) {
  if (checkPermi(['pms:file:download'])) emit('download', file)
}
function recordProcess() {
  if (props.disabled || currentBusy.value || !canProcess.value || !processReady.value) return
  emit('action', {
    action: 'record-process',
    data: {
      content: draft.value.content.trim(),
      evidence: draft.value.files.map((file) => ({ ...file.file }))
    }
  })
}
function requestClosure() {
  if (
    props.disabled ||
    currentBusy.value ||
    !canProcess.value ||
    !draft.value.closureDescription.trim()
  )
    return
  emit('action', {
    action: 'request-closure',
    data: { description: draft.value.closureDescription.trim() }
  })
}
function confirmClosure(action: 'confirm-closure' | 'return-closure') {
  if (
    props.disabled ||
    currentBusy.value ||
    !canConfirm.value ||
    !draft.value.confirmationDescription.trim()
  )
    return
  emit('action', { action, data: { description: draft.value.confirmationDescription.trim() } })
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
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-heading h2,
.section-heading h4 {
  margin: 0;
}
.inspection-card__header h2 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 15px;
}
.issue-panel :deep(.el-table) {
  font-size: 13px;
  --el-table-border-color: #f0f2f5;
  --el-table-header-bg-color: #fafbfc;
  --el-table-row-hover-bg-color: #f8faff;
}
.issue-panel :deep(.el-table th.el-table__cell) {
  color: #666;
  font-size: 12px;
  font-weight: 600;
}
.inspection-section-title {
  display: inline-block;
  padding-bottom: 6px;
  margin: 0 0 12px;
  color: var(--inspection-title, #1a2332);
  font-size: 14px;
  font-weight: 600;
  border-bottom: 2px solid var(--inspection-blue, #4a90e2);
}
.issue-summary {
  margin-bottom: 16px;
}
.issue-summary :deep(.el-descriptions__label) {
  width: 110px;
  white-space: nowrap;
}
.issue-summary :deep(.el-descriptions__content) {
  overflow-wrap: anywhere;
}
.history-section,
.write-section {
  margin-top: 20px;
}
.history-record {
  padding: 12px 0 16px;
  border-bottom: 1px solid #f0f2f5;
}
.history-record:last-child {
  border-bottom: 0;
}
.history-record h4,
.evidence-draft h4 {
  font-size: 13px;
  font-weight: 600;
}
.write-section :deep(.el-form-item) {
  margin-bottom: 14px;
}
.record-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.help-text {
  margin: 8px 0 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.evidence-draft {
  margin-top: 16px;
}
@media (max-width: 768px) {
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
