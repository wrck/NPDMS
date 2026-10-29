<template>
  <section v-loading="loading" class="cutover-plan-panel">
    <el-alert title="使用在线模板编制割接方案，或上传已有完整方案。提交后冻结本次方案及引用信息供审批。" type="info" :closable="false" show-icon />
    <template v-if="plan && plan.planRevisionId !== null && !creatingReplacement">
      <details open class="cut-card">
      <summary class="cut-card__header plan-heading">
        <div>
          <h3><Icon icon="ep:document" />割接方案</h3>
          <small>{{ modeLabel }}，第 {{ plan.revisionNo }} 版</small>
        </div>
        <div class="plan-tags">
          <el-tag size="small">{{ plan.status === 'DRAFT' ? '草稿' : plan.status === 'SUBMITTED' ? '已提交' : plan.status === 'INVALIDATED' ? '已失效' : '未提交' }}</el-tag>
          <el-tag v-if="plan.approvalFact" :type="approvalTone">{{ approvalLabel }}</el-tag>
        </div>
      </summary>
      <div class="cut-card__body">
      <p v-if="deviceFillError" class="cut-help" role="status">{{ deviceFillError }}</p>
      <el-alert
        v-if="plan.approvalFact?.rejectionReason"
        :title="`审批意见：${plan.approvalFact.rejectionReason}`"
        type="warning"
        :closable="false"
      />
      <el-form v-if="draftContent" label-position="top">
        <el-form-item v-if="!props.readonly && plan.allowedActions.includes('SAVE_DRAFT')" label="方案编制方式">
          <el-radio-group
            data-testid="plan-edit-mode" :model-value="draftContent.editMode"
            :disabled="writing || pendingUploads || editorFileStatePending || !!planFileError" @update:model-value="switchEditMode">
            <el-radio value="FULL_FILE_UPLOAD">上传完整方案</el-radio>
            <el-radio :value="onlineMode">{{ onlineMode === 'ONLINE_TEMPLATE_SIMPLE_D' ? '在线填写D级简易方案' : '在线填写方案' }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="计划割接时间" required>
          <el-date-picker
            data-testid="plan-scheduled-time" :model-value="draftContent.scheduledTime"
            :disabled="props.readonly || writing || !plan.allowedActions.includes('SAVE_DRAFT')"
            type="datetime" value-format="x" placeholder="请选择计划割接时间" @update:model-value="updateScheduledTime" />
        </el-form-item>
      </el-form>
      <div v-if="planFileError" role="alert">
        <p>{{ planFileError }}</p>
        <el-button data-testid="retry-plan-upload-reference" :loading="planFileLoading" @click="readUploadedPlan">重新读取方案文件</el-button>
      </div>
      <section
        v-if="draftContent?.editMode !== 'FULL_FILE_UPLOAD' && uploadedContent?.fileArtifactFact" class="reference-plan">
        <h4 class="cut-section-title">原上传方案（参考）</h4>
        <PmsFileVersionReferenceList
          owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)" purpose-code="FULL_PLAN"
          :reference-key="uploadedContent.fileArtifactFact.referenceKey" :artifact-id="uploadedContent.fileArtifactFact.artifactId" :version-no="uploadedContent.fileArtifactFact.versionNo" />
      </section>
      <CutoverPlanEditor
        v-if="draftContent"
        ref="planEditor"
        v-model="draftContent"
        :support-validation-message="supportValidationMessage"
        :source-snapshot="sourceSnapshot"
        :survey-items="surveyItems"
        :task-id="taskId"
        :editable="!props.readonly && !writing && plan.allowedActions.includes('SAVE_DRAFT')"
        :patch-approved="!props.readonly && plan.allowedActions.includes('UPDATE_APPROVED_CONTACTS')"
        @patch-support="patchSupport"
        @upload-pending="editorUploadPending = $event"
        @file-state-pending="editorFileStatePending = $event"
      />

      </div>
      </details>
      <Teleport :to="actionsTarget || 'body'" :disabled="!actionsTarget">
      <footer v-if="visiblePlanActions" class="cut-actions plan-actions" :class="actionsTarget ? 'cut-actions--embedded' : 'cut-actions--sticky'">
        <span v-if="hasUnsavedChanges" class="draft-change-hint">内容已修改；生成方案、提交和离开页面前会先保存草稿。</span>
        <el-button
          v-if="!props.readonly && plan.allowedActions.includes('SAVE_DRAFT')"
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="save-plan"
          type="success"
          :loading="writing"
          :disabled="pendingUploads || editorFileStatePending"
          @click="save"
        >保存</el-button>
        <el-button
          v-if="canDownloadPlan"
          v-hasPermi="['pms:cutover-task:download-plan']"
          data-testid="download-plan"
          :loading="writing"
          :disabled="pendingUploads || editorFileStatePending"
          @click="download"
        >{{ plan.status === 'SUBMITTED' ? '下载方案' : '生成并下载方案' }}</el-button>
        <el-button
          v-if="!props.readonly && plan.allowedActions.includes('SUBMIT_PLAN')"
          v-hasPermi="['pms:cutover-task:submit-plan']"
          data-testid="submit-plan"
          type="primary"
          :loading="writing"
          :disabled="pendingUploads || editorFileStatePending"
          @click="submit"
        >下一步：割接审批</el-button>
        <el-button
          v-if="!props.readonly && plan.allowedActions.includes('REVISE_PLAN')"
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="revise-plan"
          type="warning"
          :loading="writing"
          @click="revise"
        >创建修订版</el-button>
        <el-button
          v-if="!props.readonly && plan.allowedActions.includes('WITHDRAW_APPROVAL')" data-testid="withdraw-plan"
          v-hasPermi="['pms:cutover-task:save-plan']" type="warning" :loading="writing" @click="withdraw">撤回审批并修订方案</el-button>
        <el-button v-if="canCreate" data-testid="replace-invalidated-plan" :disabled="writing" @click="creatingReplacement = true">重新编制方案</el-button>
      </footer>
      </Teleport>
      <el-descriptions v-if="downloaded" :column="1" border class="download-result">
        <el-descriptions-item label="初稿文件"><PmsFileVersionReferenceList
owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
          purpose-code="FULL_PLAN" :reference-key="downloaded.fileArtifactFact.referenceKey" :artifact-id="downloaded.fileArtifactFact.artifactId" :version-no="downloaded.fileArtifactFact.versionNo" /></el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ formatWireDateTime(downloaded.downloadedAt) }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <template v-else>
      <el-empty v-if="!canCreate" description="当前阶段尚无可读取的割接方案" />
      <details open v-else class="cut-card create-plan">
        <summary class="cut-card__header"><h3><Icon icon="ep:document" />割接方案</h3></summary>
        <div class="cut-card__body">
        <h4 class="cut-section-title">一、基本信息部分</h4>
        <p class="cut-help">是否已有割接方案</p>
        <el-radio-group v-model="createMode" data-testid="plan-create-mode" :disabled="writing">
          <el-radio value="FULL_FILE_UPLOAD">是，上传已有方案</el-radio>
          <el-radio v-if="manualGrade !== 'D'" value="ONLINE_TEMPLATE_STANDARD">否，填写方案模板</el-radio>
          <el-radio v-if="manualGrade === 'D'" value="ONLINE_TEMPLATE_SIMPLE_D">否，填写D级简易模板</el-radio>
        </el-radio-group>
        <div v-if="createMode === 'FULL_FILE_UPLOAD'" class="upload-create">
          <PmsFileUploader
            ref="createUploadRef"
            :disabled="writing"
            data-testid="create-plan-uploader"
            accept=".doc,.docx,.pdf"
            owner-context="CUT"
            object-type="CUTOVER_PLAN"
            :object-id="String(taskId)"
            purpose-code="FULL_PLAN"
            :reference-key="createFileFact?.referenceKey || `cutover-plan-${taskId}`"
            :artifact-id="(createFileFact?.artifactId as number | undefined)"
            :expected-reference-version="createFileFact?.fileFactVersion.referenceVersion"
            category-code="CUTOVER_PLAN"
            @completed="completeCreateUpload"
          />
          <p class="cut-help">支持Word、PDF，单文件不超过50MB。</p>
          <PmsFileVersionReferenceList
            v-if="createFileFact" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
            purpose-code="FULL_PLAN" :reference-key="createFileFact.referenceKey" :artifact-id="createFileFact.artifactId" :version-no="createFileFact.versionNo" />
          <el-checkbox v-model="ownershipConfirmed" data-testid="create-plan-ownership">确认本人有权使用并提交该完整方案文件</el-checkbox>
        </div>
        <Teleport :to="actionsTarget || 'body'" :disabled="!actionsTarget">
        <el-button
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="create-plan"
          type="primary"
          :disabled="pendingUploads || (createMode === 'FULL_FILE_UPLOAD' && (!createFileFact || !ownershipConfirmed))"
          :loading="writing"
          @click="create"
        >开始编制方案</el-button>
        </Teleport>
        </div>
      </details>
    </template>
    <DraftLeaveDialog v-if="leaveDialogVisible" @decision="resolveLeaveDecision" />
  </section>
</template>

<script setup lang="ts">
import * as CutoverApi from '@/api/pms/cutover'
import * as FileApi from '@/api/pms/platform/file/version-reference'
import { getCurrentDeviceSummaries, type DeviceCurrentSummaryVO } from '@/api/pms/asset/device'
import { checkPermi } from '@/utils/permission'
import type {
  CutoverPlanFileFact,
  CutoverPlanSourceSnapshot,
  CutoverPlanSupportArrangement,
  CutoverPlanView,
  DownloadCutoverPlanDraftResult,
  ManualGrade,
  WritableCutoverPlanContent
} from '@/api/pms/cutover'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'
import type { FileSelection } from '@/components/PmsFileArtifact'
import { useMessage } from '@/hooks/web/useMessage'
import DraftLeaveDialog from '@/components/DraftLeaveDialog/index.vue'
import { useDraftLeaveGuard } from '@/hooks/web/useDraftLeaveGuard'
import { useDraftUploads } from '@/hooks/web/useDraftUploads'
import { downloadSubmittedPlanFile } from '../shared/cutoverSubmittedPlanDownload'
import {
  createCutoverPlanIntentStore,
  createCutoverPlanWriteBarrier,
  cutoverPlanRecoveryAction,
  formatWireDateTime
} from '../shared/cutoverTaskInteraction'
import CutoverPlanEditor from './CutoverPlanEditor.vue'
import { planSupportValidationMessage } from '../shared/cutoverPlanValidation'
import { initialOnlinePlan, type CutoverPlanDraftContent } from '../shared/cutoverPlanDraft'
import { surveyPlanDescription, surveyPlanFile } from '../shared/cutoverPlanSurvey'

const props = defineProps<{ taskId: string | number; projectId: string | number; taskVersion: number; manualGrade: ManualGrade | null; readonly?: boolean; actionsTarget?: HTMLElement }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const uploads = useDraftUploads()
const loading = ref(false)
const deviceFillError = ref('')
const surveyItems = ref<import('@/api/pms/cutover').CutoverChecklistItem[]>([])
const writing = ref(false)
const creatingReplacement = ref(false)
const plan = ref<CutoverPlanView | null>(null)
const draftContent = ref<CutoverPlanDraftContent | null>(null)
const onlineContent = ref<Exclude<CutoverPlanDraftContent, { editMode: 'FULL_FILE_UPLOAD' }> | null>(null)
const uploadedContent = ref<Extract<CutoverPlanDraftContent, { editMode: 'FULL_FILE_UPLOAD' }> | null>(null)
const planFileLoading = ref(false)
const planFileError = ref('')
const planEditor = ref<InstanceType<typeof CutoverPlanEditor>>()
const supportValidationMessage = ref('')
watch(draftContent, () => { supportValidationMessage.value = '' }, { deep: true })
const downloaded = ref<DownloadCutoverPlanDraftResult | null>(null)
const editorUploadPending = ref(false)
const editorFileStatePending = ref(false)
const createUploadRef = ref<InstanceType<typeof PmsFileUploader>>()
const createFileReads = ref(0)
const pendingUploads = computed(() => planFileLoading.value || editorUploadPending.value || createFileReads.value > 0
  || !!createUploadRef.value && (createUploadRef.value.isBusy() || createUploadRef.value.hasPendingFile()))
const canDownloadPlan = computed(() => plan.value?.allowedActions.includes('DOWNLOAD_DRAFT') || plan.value?.status === 'SUBMITTED')
const visiblePlanActions = computed(() => canDownloadPlan.value || plan.value?.allowedActions.some(action => action === 'DOWNLOAD_DRAFT'
  || (!props.readonly && ['SAVE_DRAFT', 'SUBMIT_PLAN', 'REVISE_PLAN', 'WITHDRAW_APPROVAL', 'CREATE_DRAFT'].includes(action))))
const createMode = ref<'ONLINE_TEMPLATE_STANDARD' | 'ONLINE_TEMPLATE_SIMPLE_D' | 'FULL_FILE_UPLOAD'>(
  props.manualGrade === 'D' ? 'ONLINE_TEMPLATE_SIMPLE_D' : 'ONLINE_TEMPLATE_STANDARD'
)
const createFileFact = ref<CutoverPlanFileFact | null>(null)
const ownershipConfirmed = ref(false)
const intents = createCutoverPlanIntentStore()
const barrier = createCutoverPlanWriteBarrier()

const canCreate = computed(() => !props.readonly && (plan.value?.allowedActions.includes('CREATE_DRAFT') ?? false))
const sourceSnapshot = computed<CutoverPlanSourceSnapshot | null>(() => {
  const source = plan.value?.sourceSnapshot
  return source && 'snapshotVersion' in source ? source : null
})
const onlineMode = computed(() => sourceSnapshot.value?.grade === 'D' ? 'ONLINE_TEMPLATE_SIMPLE_D' : 'ONLINE_TEMPLATE_STANDARD')
const switchEditMode = async (mode: string | number | boolean | undefined) => {
  const current = draftContent.value
  if (!current || mode === current.editMode || props.readonly || loading.value || writing.value || pendingUploads.value
    || editorFileStatePending.value || planFileError.value || !plan.value?.allowedActions.includes('SAVE_DRAFT')) return
  if (current.editMode === 'FULL_FILE_UPLOAD') uploadedContent.value = current
  else onlineContent.value = current
  if (mode === 'FULL_FILE_UPLOAD') {
    deviceFillError.value = ''
    draftContent.value = { ...(uploadedContent.value || { editMode: 'FULL_FILE_UPLOAD', fileArtifactFact: null, ownershipConfirmed: false }), scheduledTime: current.scheduledTime }
  } else if (mode === onlineMode.value && sourceSnapshot.value) {
    const taskId = props.taskId
    const currentPlan = plan.value
    loading.value = true
    try {
      const filled = await fillPlanFromSources({ ...(onlineContent.value || initialOnlinePlan(sourceSnapshot.value)), scheduledTime: current.scheduledTime })
      if (String(taskId) === String(props.taskId) && currentPlan === plan.value) draftContent.value = filled
    } finally { if (String(taskId) === String(props.taskId) && currentPlan === plan.value) loading.value = false }
  }
}

let sourceSequence = 0
const fillPlanFromSources = async (content: CutoverPlanDraftContent): Promise<CutoverPlanDraftContent> => {
  const sequence = ++sourceSequence
  const taskId = props.taskId
  const currentPlan = plan.value
  let sourceError = ''
  deviceFillError.value = ''
  surveyItems.value = []
  if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD' || props.readonly || plan.value?.status !== 'DRAFT'
    || !plan.value.allowedActions.includes('SAVE_DRAFT')) return content
  const details = new Map((content.overview.deviceDetails || []).map(row => [String(row.deviceId), row]))
  const missing = checkPermi(['pms:device:query']) ? content.overview.deviceSummary.filter(device => {
    const row = details.get(String(device.deviceId))
    return !row?.productModel?.trim() || !row.currentVersion?.trim()
  }) : []
  const targetMissing = content.overview.deviceSummary.filter(device => !details.get(String(device.deviceId))?.targetVersion?.trim())
  const readTargetVersions = async () => {
    const checklistId = sourceSnapshot.value?.checklistId
    if (!targetMissing.length || checklistId == null) return null
    try {
      const state = await CutoverApi.getCutoverExternalState(props.taskId, checklistId)
      if (String(state.checklistId) !== String(checklistId)) throw new Error('调研清单版本不匹配')
      return state
    } catch {
      sourceError = '调研目标版本暂时无法读取，可手动补充或刷新重试。'
      return null
    }
  }
  const readSurvey = async () => {
    const source = sourceSnapshot.value
    if (source?.checklistId == null) return null
    try {
      const survey = await CutoverApi.getCutoverChecklist(props.taskId, source.checklistId)
      if (String(survey.checklistId) !== String(source.checklistId) || String(survey.taskId) !== String(props.taskId)
        || survey.checklistVersion !== source.checklistVersion || survey.status !== 'SUBMITTED') throw new Error('调研来源不匹配')
      return survey
    } catch {
      sourceError = '调研资料暂时无法读取，已有填写保留，可刷新重试。'
      return null
    }
  }
  const [results, targetState, survey] = await Promise.all([
    missing.length ? getCurrentDeviceSummaries(missing.map(device => device.deviceId), props.projectId).catch(() => []) : [], readTargetVersions(), readSurvey()
  ])
  if (sequence !== sourceSequence || String(taskId) !== String(props.taskId) || currentPlan !== plan.value
    || props.readonly || plan.value?.status !== 'DRAFT' || !plan.value.allowedActions.includes('SAVE_DRAFT')) return content
  deviceFillError.value = sourceError
  surveyItems.value = survey?.items || []
  let changed = false
  const byId = new Map<string, DeviceCurrentSummaryVO>(results.map(summary => [String(summary.deviceId), summary] as const))
  missing.forEach(device => {
    const summary = byId.get(String(device.deviceId))
    if (!summary || String(summary.deviceId) !== String(device.deviceId) || summary.sn !== device.serialNumber) {
      deviceFillError.value = '部分设备信息暂时无法读取，可手动补充或刷新重试。'
      return
    }
    const existing = details.get(String(device.deviceId)) || { deviceId: device.deviceId, productModel: '', currentVersion: '', targetVersion: '', remark: '' }
    const row = { ...existing,
      productModel: existing.productModel?.trim() ? existing.productModel : summary.productModel || '',
      currentVersion: existing.currentVersion?.trim() ? existing.currentVersion : summary.conpVersion || '' }
    if (row.productModel !== existing.productModel || row.currentVersion !== existing.currentVersion) {
      details.set(String(device.deviceId), row)
      changed = true
    }
  })
  for (const device of targetMissing) {
    const source = targetState?.devices.find(row => String(row.deviceId) === String(device.deviceId))
    if (!source) continue
    if (source.sn !== device.serialNumber) {
      deviceFillError.value = '调研目标版本暂时无法读取，可手动补充或刷新重试。'
      continue
    }
    if (!source.targetSoftwareVersion?.trim()) continue
    const existing = details.get(String(device.deviceId)) || { deviceId: device.deviceId, productModel: '', currentVersion: '', targetVersion: '', remark: '' }
    details.set(String(device.deviceId), { ...existing, targetVersion: source.targetSoftwareVersion })
    changed = true
  }
  const description = !content.overview.projectDescription.trim() ? surveyPlanDescription(surveyItems.value) : ''
  if (description.length > 4000) deviceFillError.value = '调研概述超过方案字数限制，请手动整理，原调研内容保留。'
  const fillDescription = description && description.length <= 4000
  return changed || fillDescription ? { ...content, overview: { ...content.overview,
    ...(changed ? { deviceDetails: [...details.values()] } : {}),
    ...(fillDescription ? { projectDescription: description } : {}) } } : content
}
const modeLabel = computed(() => ({
  ONLINE_TEMPLATE_STANDARD: '在线标准方案',
  ONLINE_TEMPLATE_SIMPLE_D: 'D级简易方案',
  FULL_FILE_UPLOAD: '完整方案文件'
}[draftContent.value?.editMode || plan.value?.content?.editMode || 'ONLINE_TEMPLATE_STANDARD']))
const approvalLabel = computed(() => ({
  PENDING: '审批中', PAUSED_SOURCE_INVALIDATED: '来源变化，审批暂停', WITHDRAWN: '已撤回', APPROVED: '审批通过', REJECTED: '审批驳回'
}[plan.value?.approvalFact?.status || 'PENDING']))
const approvalTone = computed(() => plan.value?.approvalFact?.status === 'APPROVED'
  ? 'success'
  : plan.value?.approvalFact?.status === 'REJECTED'
    ? 'danger'
    : 'warning')

let refreshSequence = 0
const refresh = async (options: { preserveDraft?: boolean } = {}) => {
  const sequence = ++refreshSequence
  const taskId = props.taskId
  const retained = options.preserveDraft && draftContent.value
    ? JSON.parse(JSON.stringify(draftContent.value)) as CutoverPlanDraftContent : null
  loading.value = true
  try {
    const current = await CutoverApi.getCutoverPlan(props.taskId)
    if (sequence !== refreshSequence || String(taskId) !== String(props.taskId)) return
    if (retained && plan.value && (String(current.planRevisionId) !== String(plan.value.planRevisionId)
      || current.planVersion !== plan.value.planVersion || current.status !== 'DRAFT')) {
      message.warning('方案已被其他操作更新，当前填写已保留；请先核对新版本，不能直接覆盖。')
      throw new Error('Draft changed concurrently')
    }
    plan.value = current
    creatingReplacement.value = false
    downloaded.value = plan.value.generatedDraft ?? null
    const content = plan.value.content
    const nextContent = retained || (content
      ? JSON.parse(JSON.stringify(content)) as CutoverPlanDraftContent
      : null)
    const filled = nextContent ? await fillPlanFromSources(nextContent) : null
    if (sequence !== refreshSequence || String(taskId) !== String(props.taskId)) return
    draftContent.value = filled
    if (draftContent.value?.editMode === 'FULL_FILE_UPLOAD') uploadedContent.value = draftContent.value
    else if (draftContent.value) onlineContent.value = draftContent.value
    if (!props.readonly && current.allowedActions.includes('SAVE_DRAFT')) await readUploadedPlan()
  } finally {
    if (sequence === refreshSequence) loading.value = false
  }
}

const passBarrier = async () => {
  const result = await barrier.beforeWrite()
  if (result === 'PROCEED') return true
  message[result === 'REFRESHED' ? 'success' : 'warning'](result === 'REFRESHED'
    ? '上次命令结果已刷新，请按最新方案继续操作'
    : '上次命令已成功，但页面仍未刷新；不会重复发送业务命令')
  return false
}

const runIntent = async <T,>(intent: string, call: (key: string) => Promise<T>) => {
  if (loading.value) return null
  if (pendingUploads.value) { message.warning('请完成附件上传和绑定后再保存或提交'); return null }
  if (!(await passBarrier())) return null
  supportValidationMessage.value = ''
  writing.value = true
  const key = intents.key(intent)
  try {
    const result = await call(key)
    intents.complete(intent)
    try {
      await refresh()
      emit('changed')
    } catch {
      barrier.register(async () => { await refresh(); emit('changed') })
      message.warning('命令已成功，但页面刷新失败；下一次写操作只重试刷新')
    }
    return result
  } catch (error) {
    const recovery = cutoverPlanRecoveryAction(error)
    const response = (error as { response?: { data?: { msg?: string; data?: { reasonCode?: string } } } })?.response
    if (recovery !== 'RETRY_SAME_KEY') {
      const reasonCode = response?.data?.data?.reasonCode
      const serverMessage = response?.data?.msg
      const genericPlanRejection = reasonCode === 'PLAN_SECTION_INCOMPLETE'
        && (!serverMessage || serverMessage === '方案内容尚未完整')
      if (reasonCode === 'SUPPORT_ARRANGEMENT_INCOMPLETE' || genericPlanRejection) {
        supportValidationMessage.value = planSupportValidationMessage(draftContent.value?.editMode === 'FULL_FILE_UPLOAD' ? null : draftContent.value)
      }
      message.error(supportValidationMessage.value || (genericPlanRejection
        ? '方案内容尚未完整，请补充方案各章节及步骤后再生成或提交。'
        : serverMessage || '方案操作失败，请检查填写内容后重试。'))
    }
    if (recovery === 'START_NEW_INTENT') intents.complete(intent)
    if (recovery === 'REFRESH_AGGREGATE' || recovery === 'REFRESH_OWNER_FACTS') {
      intents.complete(intent)
      try {
        await refresh({ preserveDraft: true })
        message.warning('来源和版本已刷新，未提交的填写已保留；请核对后重新保存草稿。')
      } catch { message.warning('权威事实刷新失败，当前填写已保留，请稍后重试。') }
    } else if (recovery === 'RETRY_SAME_KEY') {
      message.warning('响应未知或命令处理中，已保留本次幂等键')
    }
    return null
  } finally {
    writing.value = false
    if (supportValidationMessage.value) {
      await nextTick()
      planEditor.value?.focusSupportValidation()
    }
  }
}

const create = async () => {
  const data = createMode.value === 'FULL_FILE_UPLOAD'
    ? { editMode: createMode.value, fileArtifactFact: createFileFact.value!, ownershipConfirmed: true as const }
    : { editMode: createMode.value }
  const result = await runIntent(`create:${JSON.stringify(data)}`, (key) =>
    CutoverApi.createCutoverPlanDraft({ taskId: props.taskId, taskVersion: props.taskVersion, data: data, idempotencyKey: key }))
  if (result) message.success('方案草稿已创建')
}
const save = async () => {
  if (editorFileStatePending.value) { message.warning('文件状态正在读取，请稍后保存草稿。'); return false }
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined || !draftContent.value) return false
  if (draftContent.value.editMode === 'FULL_FILE_UPLOAD'
    && (!draftContent.value.fileArtifactFact || !draftContent.value.ownershipConfirmed || planFileError.value)) {
    message.warning('请上传完整方案并确认有权使用该文件后再保存。'); return false
  }
  const content = contentForSave(JSON.parse(JSON.stringify(draftContent.value)) as WritableCutoverPlanContent)
  const result = await runIntent(`save:${plan.value.planRevisionId}:${plan.value.planVersion}:${JSON.stringify(content)}`,
    (key) => CutoverApi.saveCutoverPlanDraft({ taskId: props.taskId, taskVersion: props.taskVersion, planVersion: plan.value!.planVersion!, data: content, idempotencyKey: key }))
  if (result) message.success('方案草稿已保存')
  return !!result
}

const updateScheduledTime = (value: string | number | null) => {
  if (!draftContent.value) return
  draftContent.value = { ...draftContent.value, scheduledTime: value == null || value === '' ? null : Number(value) }
}
const contentForSave = <T extends CutoverPlanDraftContent>(content: T): T => {
  if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') return content
  return {
    ...content,
    ...(content.rollbackDescription !== undefined ? { rollbackDescription: content.rollbackDescription.trim() } : {}),
    steps: content.steps.map(row => ({ ...row, content: row.content.trim(), ...(row.details ? { details: {
      stepName: row.details.stepName.trim(), testResult: row.details.testResult.trim(), businessImpactTime: row.details.businessImpactTime.trim(),
      operator: row.details.operator.trim(), operationTime: row.details.operationTime.trim()
    } } : {}) })),
    overview: { ...content.overview, ...(content.overview.deviceDetails ? { deviceDetails: content.overview.deviceDetails.map(row => ({
      ...row, productModel: row.productModel.trim(), currentVersion: row.currentVersion.trim(), targetVersion: row.targetVersion.trim(), remark: row.remark.trim()
    })) } : {}) },
    supportArrangements: content.supportArrangements.map(row => ({
      ...row, personName: row.personName.trim(), dutyDescription: row.dutyDescription.trim(), phone: row.phone.trim()
    }))
  }
}
const hasUnsavedChanges = computed(() => !!draftContent.value && !!plan.value?.content
  && (JSON.stringify(contentForSave(draftContent.value)) !== JSON.stringify(contentForSave(plan.value.content))
    || draftContent.value.editMode === 'ONLINE_TEMPLATE_STANDARD' && !draftContent.value.overview.preTopologyFile
      && !!surveyPlanFile(surveyItems.value, 'SURVEY_CURRENT_TOPOLOGY')))
const { requestLeave: saveBeforeLeave, hasPendingDraft, leaveDialogVisible, resolveLeaveDecision } = useDraftLeaveGuard({
  hasPending: () => hasUnsavedChanges.value || pendingUploads.value || uploads.pending.value,
  stateKey: () => JSON.stringify([props.taskId, plan.value?.planVersion, draftContent.value, pendingUploads.value, uploads.pending.value]),
  isBusy: () => loading.value || writing.value || planFileLoading.value || createFileReads.value > 0
    || editorFileStatePending.value || uploads.busy.value || editorUploadPending.value && !uploads.pending.value,
  warnBusy: () => message.warning('请等待当前读取、上传或保存完成后再离开。'),
  save: async () => {
    if (uploads.pending.value) { message.warning('存在未上传成功的文件，请重试上传或移除后再保存。'); return false }
    if (!plan.value?.allowedActions.includes('SAVE_DRAFT') || props.readonly) {
      message.warning('当前填写尚未保存，权限或方案状态已变化；填写已保留。')
      return false
    }
    return save()
  },
  discard: () => {
    uploads.discard()
    draftContent.value = plan.value?.content ? JSON.parse(JSON.stringify(plan.value.content)) : null
    onlineContent.value = draftContent.value?.editMode !== 'FULL_FILE_UPLOAD' ? draftContent.value : null
    uploadedContent.value = draftContent.value?.editMode === 'FULL_FILE_UPLOAD' ? draftContent.value : null
  }
})
defineExpose({ saveBeforeLeave, hasPendingDraft })
const download = async () => {
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  if (plan.value.status === 'SUBMITTED') {
    if (writing.value || !plan.value.planRevisionId || !plan.value.content) return
    writing.value = true
    try { await downloadSubmittedPlanFile({ taskId: props.taskId, planRevisionId: plan.value.planRevisionId, content: plan.value.content }) }
    catch { message.warning('方案下载失败，请刷新方案后重试。') }
    finally { writing.value = false }
    return
  }
  const target = openDownloadTarget()
  if (!target) return
  if (hasUnsavedChanges.value && !(await save())) return target.close()
  const result = await runIntent(`download:${plan.value.planRevisionId}:${plan.value.planVersion}`,
    (key) => CutoverApi.downloadCutoverPlanDraft({ taskId: props.taskId, planVersion: plan.value!.planVersion!, idempotencyKey: key }))
  if (!result) return target.close()
  downloaded.value = result
  await accessGeneratedDraft(result, target)
}
const openDownloadTarget = () => {
  const target = window.open('about:blank', '_blank')
  if (!target) {
    message.warning('浏览器已阻止新窗口，请允许弹窗后重试')
    return null
  }
  target.opener = null
  return target
}
const accessGeneratedDraft = async (result: DownloadCutoverPlanDraftResult, openedTarget?: Window) => {
  const target = openedTarget || openDownloadTarget()
  if (!target) return
  try {
    const fact = result.fileArtifactFact
    const ticket = await FileApi.createAccessTicket(
      fact.artifactId,
      fact.versionNo,
      'DOWNLOAD',
      fileKey(fact.referenceKey)
    )
    target.location.replace(ticket.shortLivedUrl)
    message.success('初稿已生成并开始下载')
  } catch {
    target.close()
    message.warning('初稿已生成，但文件访问暂不可用；再次点击只重试下载，不会重新生成初稿')
  }
}
const submit = async () => {
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  if (hasUnsavedChanges.value && !(await save())) return
  const result = await runIntent(`submit:${plan.value.planRevisionId}:${plan.value.planVersion}`,
    (key) => CutoverApi.submitCutoverPlan({ taskId: props.taskId, taskVersion: props.taskVersion, planVersion: plan.value!.planVersion!, idempotencyKey: key }))
  if (result) message.success('方案已提交审批')
}
const revise = async () => {
  if (!plan.value?.planRevisionId) return
  const reason = plan.value.status === 'INVALIDATED'
    ? plan.value.approvalFact?.status === 'PAUSED_SOURCE_INVALIDATED' ? 'SOURCE_REPLACED' : 'USER_REVISION'
    : 'APPROVAL_REJECTED'
  const result = await runIntent(`revise:${plan.value.planRevisionId}:${reason}`,
    (key) => CutoverApi.reviseCutoverPlan({ taskId: props.taskId, taskVersion: props.taskVersion, data: { sourcePlanRevisionId: plan.value!.planRevisionId!, reason }, idempotencyKey: key }))
  if (result) message.success('修订草稿已创建')
}
const withdraw = async () => {
  if (!sourceSnapshot.value?.projectScopeVersion || !plan.value?.allowedActions.includes('WITHDRAW_APPROVAL')) return
  let reason: string
  try { reason = (await message.prompt('撤回将取消未完成的审批节点，保留已办意见，并生成新的方案草稿。请输入撤回原因。', '撤回审批')).value.trim() }
  catch { return }
  if (!reason || reason.length > 1000) { message.warning('请填写 1～1000 字的撤回原因'); return }
  const command = { taskId: props.taskId, taskVersion: props.taskVersion, targetStage: 'P4' as const,
    data: { expectedProjectScopeVersion: sourceSnapshot.value.projectScopeVersion, reason } }
  if (await runIntent(JSON.stringify(['withdraw', command]), idempotencyKey => CutoverApi.reviseCutoverTask({ ...command, idempotencyKey }))) {
    message.success('审批已撤回，方案修订草稿已创建')
  }
}
const patchSupport = async (row: CutoverPlanSupportArrangement) => {
  if (!row.arrangementId || plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  let reason: string
  try { const response = await message.prompt('请输入本次联系人、电话或到位时间的变更原因', '更新保障联系人'); reason = response.value.trim() } catch { return }
  if (!reason) { message.warning('请填写变更原因'); return }
  const data = { personName: row.personName, phone: row.phone, arrivalTime: row.arrivalTime, reason,
    customerContactReference: row.customerContactReference ?? null, personReference: row.personReference ?? null }
  const result = await runIntent(`contact:${row.arrangementId}:${plan.value.planVersion}:${JSON.stringify(data)}`,
    (key) => CutoverApi.patchApprovedCutoverPlanContact({ taskId: props.taskId, arrangementId: row.arrangementId!, planVersion: plan.value!.planVersion!, data: data, idempotencyKey: key }))
  if (result) message.success('批准联系人已更新')
}

const fileKey = (referenceKey: string) => ({
  ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: String(props.taskId),
  purposeCode: 'FULL_PLAN', referenceKey
})
const readUploadedPlan = async () => {
  planFileLoading.value = true
  planFileError.value = ''
  try {
    const referenceKey = uploadedContent.value?.fileArtifactFact?.referenceKey || `cutover-plan-${props.taskId}`
    const reference = await FileApi.getCurrentReference(fileKey(referenceKey))
    if (!reference || reference.status !== 'ACTIVE') return
    const artifact = await FileApi.getArtifact(reference.artifactId, fileKey(referenceKey))
    const versions = await FileApi.getVersions(reference.artifactId, { ...fileKey(referenceKey), pageSize: 20 })
    const version = versions.items.find(row => row.versionNo === reference.versionNo)
    if (!version || reference.referenceVersion !== artifact.reference.referenceVersion) throw new Error('方案文件引用已变化')
    const previous = uploadedContent.value
    const known = previous?.fileArtifactFact
    if (known && String(known.artifactId) === String(reference.artifactId) && known.versionNo === reference.versionNo
      && known.referenceKey === referenceKey && String(known.scopeVersion) === String(artifact.reference.scopeVersion)
      && known.sha256 === version.sha256 && known.fileFactVersion.artifactVersion === artifact.artifactVersion
      && known.fileFactVersion.referenceVersion === reference.referenceVersion
      && known.fileFactVersion.availabilityVersion === version.availabilityVersion) return
    uploadedContent.value = { editMode: 'FULL_FILE_UPLOAD', scheduledTime: draftContent.value?.scheduledTime,
      ownershipConfirmed: !!previous?.ownershipConfirmed && previous.fileArtifactFact?.sha256 === version.sha256,
      fileArtifactFact: { artifactId: reference.artifactId, versionNo: reference.versionNo, referenceKey,
        scopeVersion: artifact.reference.scopeVersion, sha256: version.sha256, fileFactVersion: {
          artifactVersion: artifact.artifactVersion, referenceVersion: reference.referenceVersion, availabilityVersion: version.availabilityVersion
        } } }
    if (draftContent.value?.editMode === 'FULL_FILE_UPLOAD') draftContent.value = uploadedContent.value
  } catch {
    planFileError.value = '方案文件状态读取失败，原附件保留，请重新读取后再切换编制方式或上传。'
  } finally { planFileLoading.value = false }
}
const completeCreateUpload = async (selection: FileSelection) => {
  createFileReads.value++
  try {
  const artifact = await FileApi.getArtifact(selection.artifactId, fileKey(selection.referenceKey))
  const versions = await FileApi.getVersions(selection.artifactId, { ...fileKey(selection.referenceKey), pageSize: 20 })
  const version = versions.items.find((row) => row.versionNo === selection.versionNo)
  if (!version) throw new Error('PLT 未返回刚完成的方案文件版本')
  createFileFact.value = {
    artifactId: selection.artifactId, versionNo: selection.versionNo, referenceKey: selection.referenceKey,
    scopeVersion: artifact.reference.scopeVersion, sha256: version.sha256,
    fileFactVersion: {
      artifactVersion: artifact.artifactVersion,
      referenceVersion: artifact.reference.referenceVersion,
      availabilityVersion: version.availabilityVersion
    }
  }
  } finally { createFileReads.value-- }
}

watch(() => props.taskId, () => { void refresh() }, { immediate: true })
onBeforeUnmount(() => { ++sourceSequence; ++refreshSequence })
</script>

<style scoped>
.cutover-plan-panel { min-width: 0; }
.plan-heading > div:first-child { display: grid; gap: 4px; }
.plan-tags, .plan-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.draft-change-hint { flex: 1; align-self: center; color: #888; font-size: 12px; }
.download-result { margin-top: 16px; }
.upload-create { margin: 16px 0; }
@media (max-width: 767px) { .plan-heading { flex-direction: column; } }
</style>
