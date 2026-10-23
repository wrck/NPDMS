<template>
  <section class="attachment-panel" aria-label="普通附件与客户获准证明">
    <header class="attachment-heading">
      <h2>普通附件 / 客户获准证明（可选）</h2>
      <el-button
        v-if="canQuery"
        :loading="current?.loading"
        :disabled="uploadBusy"
        @click="refreshCurrent"
      >
        刷新附件
      </el-button>
    </header>
    <p class="attachment-help"
      >可补充普通业务附件或客户获准证明。每次上传新增独立附件，保留已有附件。</p
    >
    <el-alert
      v-if="!canQuery || current?.error"
      :title="!canQuery ? queryPermissionNotice : current?.error"
      type="warning"
      show-icon
      :closable="false"
      class="mb-12px"
    />
    <template v-if="canQuery">
      <p
        v-for="entry in unlistedUploads"
        :key="entry.referenceKey"
        class="attachment-notice"
        role="status"
      >
        {{
          entry.completed?.referenceKey === entry.referenceKey
            ? `文件已上传并绑定任务 ${current?.taskCode}（工件 ${entry.completed?.artifactId}，V${entry.completed?.versionNo}）。列表尚未确认，请刷新核对，无需重复上传。`
            : '平台返回的文件引用尚未确认，请刷新核对当前任务附件。'
        }}
      </p>
      <el-skeleton v-if="current?.loading && !current.items.length" :rows="3" animated />
      <el-table
        v-else
        :data="current?.items || []"
        :row-key="attachmentKey"
        empty-text="尚无附件；可按需补充普通附件或客户获准证明"
        :aria-busy="current?.loading || false"
      >
        <el-table-column prop="name" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="文件版本" width="100">
          <template #default="{ row }">V{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="大小" min-width="140">
          <template #default="{ row }">{{
            row.sizeBytes == null ? '未提供' : `${row.sizeBytes} 字节`
          }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">{{ availabilityLabel(row.availabilityStatus) }}</template>
        </el-table-column>
        <el-table-column v-if="canDownload" label="操作" width="130">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="downloading.has(attachmentKey(row))"
              :disabled="row.availabilityStatus !== 'AVAILABLE' || current?.loading"
              @click="downloadAttachment(row)"
              >下载此版本</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- Keep old upload instances mounted with their original task and slot while a request can finish. -->
    <div
      v-for="entry in allSlots"
      v-show="entry.scope === current && entry.slot === currentSlot && canAttach"
      :key="entry.slot.referenceKey"
      class="attachment-upload"
    >
      <h3>新增附件</h3>
      <p class="attachment-help">支持 PDF、PNG、JPG、JPEG、TXT、DOC、DOCX，单文件不超过 50MB。</p>
      <PmsFileUploader
        v-if="!entry.slot.completed || entry.slot.uploading"
        :ref="(instance) => setUploader(entry.slot, instance)"
        hide-submit
        accept=".pdf,.png,.jpg,.jpeg,.txt,.doc,.docx"
        owner-context="SRV"
        object-type="INSPECTION_TASK"
        :object-id="String(entry.scope.taskId)"
        purpose-code="ATTACHMENT"
        :reference-key="entry.slot.referenceKey"
        category-code="INSPECTION_ATTACHMENT"
        :disabled="
          disabled ||
          entry.scope !== current ||
          !canAttach ||
          !entry.scope.ready ||
          entry.scope.loading ||
          entry.slot.uploading
        "
        @completed="(selection) => completed(entry.scope, entry.slot, selection)"
      />
      <p v-if="entry.slot.error" class="attachment-notice" role="status">{{ entry.slot.error }}</p>
      <el-button
        type="primary"
        :loading="entry.slot.uploading || entry.slot.uploader?.isBusy()"
        :disabled="
          disabled || !current?.ready || current?.loading || !entry.slot.uploader?.hasPendingFile()
        "
        @click="upload(entry.scope, entry.slot)"
        >上传并新增附件</el-button
      >
    </div>
    <p v-if="otherPending.length" class="attachment-notice" role="status">
      原任务
      {{ otherPending.map((scope) => scope.taskCode).join('、') }}
      的未完成上传已保留，请切回原任务处理。
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import { generateUUID } from '@/utils'
import fileDownload from '@/utils/download'
import PmsFileUploader from '@/components/PmsFileArtifact/PmsFileUploader.vue'
import type { FileSelection } from '@/components/PmsFileArtifact/types'
import * as InspectionTaskApi from '@/api/pms/inspection/tasks'
import type { AttachmentView, TaskDetail, WireLong } from '@/api/pms/inspection/tasks'
import * as FileVersionApi from '@/api/pms/platform/file/version-reference'

defineOptions({ name: 'InspectionAttachmentPanel' })
const props = defineProps<{ task: TaskDetail; disabled: boolean }>()
const emit = defineEmits<{ busyChanged: [value: boolean]; draftChanged: [value: boolean] }>()
interface UploadSlot {
  referenceKey: string
  uploader?: { submit: () => Promise<void>; hasPendingFile: () => boolean; isBusy: () => boolean }
  uploading: boolean
  completed?: Pick<AttachmentView, 'artifactId' | 'versionNo' | 'referenceKey'>
  error: string
}
interface TaskScope {
  taskId: WireLong
  taskCode: string
  items: AttachmentView[]
  slots: UploadSlot[]
  request: number
  loading: boolean
  ready: boolean
  error: string
}
const message = useMessage()
const scopes = ref<TaskScope[]>([])
const current = ref<TaskScope>()
const downloading = ref(new Set<string>())
let alive = true
let epoch = 0
const canQuery = computed(
  () => checkPermi(['pms:inspection-task:query']) && checkPermi(['pms:file:query'])
)
const queryPermissionNotice = computed(() =>
  !checkPermi(['pms:file:query'])
    ? '缺少文件查询权限，无法读取或新增附件。'
    : '缺少巡检任务查询权限，无法读取或新增附件。'
)
const canAttach = computed(
  () =>
    canQuery.value &&
    !['ARCHIVED', 'CANCELLED'].includes(props.task.statusCode) &&
    checkPermi(['pms:inspection-task:prepare']) &&
    checkPermi(['pms:file:upload'])
)
const canDownload = computed(() => checkPermi(['pms:file:download']))
const allSlots = computed(() =>
  scopes.value.flatMap((scope) => scope.slots.map((slot) => ({ scope, slot })))
)
const currentSlot = computed(() => current.value?.slots.find((slot) => !slot.completed))
const uploadBusy = computed(() =>
  allSlots.value.some(({ slot }) => slot.uploading || slot.uploader?.isBusy())
)
const dirty = computed(() =>
  allSlots.value.some(({ slot }) => !slot.completed && slot.uploader?.hasPendingFile())
)
const busy = computed(
  () =>
    uploadBusy.value || scopes.value.some((scope) => scope.loading) || downloading.value.size > 0
)
const otherPending = computed(() =>
  scopes.value.filter(
    (scope) =>
      scope !== current.value &&
      scope.slots.some(
        (slot) => slot.uploading || (!slot.completed && slot.uploader?.hasPendingFile())
      )
  )
)
const listed = (scope: TaskScope, slot: UploadSlot) =>
  scope.items.some(
    (row) =>
      slot.completed &&
      String(row.artifactId) === String(slot.completed.artifactId) &&
      row.versionNo === slot.completed.versionNo &&
      row.referenceKey === slot.referenceKey
  )
const unlistedUploads = computed(
  () => current.value?.slots.filter((slot) => slot.completed && !listed(current.value!, slot)) || []
)
watch(busy, (value) => emit('busyChanged', value), { immediate: true, flush: 'sync' })
watch(dirty, (value) => emit('draftChanged', value), { immediate: true, flush: 'sync' })

const attachmentKey = (row: AttachmentView) =>
  `${row.artifactId}:${row.versionNo}:${row.referenceKey}`
const availabilityLabel = (status: string) =>
  ({ AVAILABLE: '可下载', VALIDATING: '校验中', REJECTED: '校验未通过', WITHDRAWN: '已撤回' })[
    status
  ] || status
const newSlot = (): UploadSlot => ({
  referenceKey: `attachment:${generateUUID()}`,
  uploading: false,
  error: ''
})
function setUploader(slot: UploadSlot, instance: unknown) {
  slot.uploader = instance ? (instance as UploadSlot['uploader']) : undefined
}
function originalTaskNotice(scope: TaskScope) {
  message.warning(
    `本次附件操作属于原任务 ${scope.taskCode}，结果未加入当前任务，请切回原任务刷新查看。`
  )
}
async function load(scope: TaskScope) {
  const request = ++scope.request
  scope.ready = false
  scope.error = ''
  if (!canQuery.value) {
    scope.loading = false
    return
  }
  scope.loading = true
  try {
    const items = await InspectionTaskApi.attachments(scope.taskId)
    if (!alive || request !== scope.request) return
    scope.items = items
    // A lost completion response can be reconciled by the task's authoritative attachment list.
    for (const slot of scope.slots) {
      if (!slot.completed && !slot.uploading && slot.error) {
        const bound = items.find((row) => row.referenceKey === slot.referenceKey)
        if (bound)
          slot.completed = {
            artifactId: bound.artifactId,
            versionNo: bound.versionNo,
            referenceKey: bound.referenceKey
          }
      }
    }
    scope.ready = scope.slots.every((slot) => !slot.completed || listed(scope, slot))
    if (!scope.ready) scope.error = '已完成上传的附件尚未在列表中确认，请刷新核对后再新增附件。'
    forgetConfirmedSlots(scope)
    if (scope.ready && !scope.slots.some((slot) => !slot.completed)) scope.slots.push(newSlot())
  } catch {
    if (alive && request === scope.request)
      scope.error = '附件列表读取失败，请刷新后再上传；未完成的上传已保留。'
  } finally {
    if (request === scope.request) scope.loading = false
  }
}
function forgetConfirmedSlots(scope: TaskScope) {
  scope.slots = scope.slots.filter(
    (slot) => !slot.completed || !listed(scope, slot) || slot.uploading || slot.uploader?.isBusy()
  )
}
function refreshCurrent() {
  if (current.value && !uploadBusy.value && !current.value.loading) void load(current.value)
}
async function upload(scope: TaskScope, slot: UploadSlot) {
  if (
    !alive ||
    props.disabled ||
    !canAttach.value ||
    scope !== current.value ||
    !scope.ready ||
    scope.loading ||
    slot.completed ||
    uploadBusy.value ||
    !slot.uploader?.hasPendingFile()
  )
    return
  slot.uploading = true
  slot.error = ''
  try {
    await slot.uploader.submit()
  } catch {
    slot.error = '上传结果尚未确认，请在当前槽位重试；原上传信息已保留。'
    if (scope !== current.value || !alive) originalTaskNotice(scope)
  } finally {
    slot.uploading = false
    forgetConfirmedSlots(scope)
  }
}
function completed(scope: TaskScope, slot: UploadSlot, selection: FileSelection) {
  slot.completed = selection
  if (selection.referenceKey !== slot.referenceKey) {
    scope.ready = false
    scope.error = '平台返回的附件引用与上传槽不一致，请刷新核对，暂不能继续上传。'
    return
  }
  if (scope !== current.value || !alive) originalTaskNotice(scope)
  if (alive) void load(scope)
}
async function downloadAttachment(row: AttachmentView) {
  const scope = current.value
  const key = attachmentKey(row)
  if (
    !scope ||
    !canQuery.value ||
    !canDownload.value ||
    row.availabilityStatus !== 'AVAILABLE' ||
    downloading.value.has(key)
  )
    return
  const selected = { ...row }
  const capturedEpoch = epoch
  downloading.value.add(key)
  try {
    const ticket = await FileVersionApi.createAccessTicket(
      selected.artifactId,
      selected.versionNo,
      'DOWNLOAD',
      {
        ownerContext: 'SRV',
        objectType: 'INSPECTION_TASK',
        objectId: String(scope.taskId),
        purposeCode: 'ATTACHMENT',
        referenceKey: selected.referenceKey
      }
    )
    const response = await fetch(ticket.shortLivedUrl, {
      credentials: 'omit',
      referrerPolicy: 'no-referrer'
    })
    if (!response.ok) throw new Error('ATTACHMENT_DOWNLOAD_FAILED')
    const content = await response.blob()
    if (!alive || capturedEpoch !== epoch || scope !== current.value)
      return originalTaskNotice(scope)
    fileDownload.file(content, selected.name)
  } catch {
    if (!alive || capturedEpoch !== epoch || scope !== current.value) originalTaskNotice(scope)
    else message.error('附件下载未完成，请刷新后重试此文件版本。')
  } finally {
    downloading.value.delete(key)
  }
}
watch(
  () => [String(props.task.id), canQuery.value],
  () => {
    ++epoch
    let scope = scopes.value.find((item) => String(item.taskId) === String(props.task.id))
    if (!scope) {
      scopes.value.push({
        taskId: props.task.id,
        taskCode: props.task.taskCode,
        items: [],
        slots: [newSlot()],
        request: 0,
        loading: false,
        ready: false,
        error: ''
      })
      scope = scopes.value[scopes.value.length - 1]
    }
    current.value = scope
    void load(scope)
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  alive = false
  ++epoch
  emit('busyChanged', false)
  emit('draftChanged', false)
})
</script>

<style scoped>
.attachment-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.attachment-panel h2 {
  margin: 0;
  font-size: 18px;
}
.attachment-panel h3 {
  margin: 0;
  font-size: 16px;
}
.attachment-help {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin: 12px 0;
}
.attachment-upload {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color);
}
.attachment-notice {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}
</style>
