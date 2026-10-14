<template>
  <section v-loading="loading" class="cutover-plan-panel">
    <el-alert title="使用在线模板编制割接方案，或上传已有完整方案。提交后冻结本次方案及引用信息供审批。" type="info" :closable="false" show-icon />
    <template v-if="plan && plan.planRevisionId !== null">
      <section class="cut-card">
      <header class="cut-card__header plan-heading">
        <div>
          <h3><Icon icon="ep:document" />割接方案</h3>
          <small>{{ modeLabel }}，第 {{ plan.revisionNo }} 版</small>
        </div>
        <div class="plan-tags">
          <el-tag size="small">{{ plan.status === 'DRAFT' ? '草稿' : plan.status === 'SUBMITTED' ? '已提交' : plan.status === 'INVALIDATED' ? '已失效' : '未提交' }}</el-tag>
          <el-tag v-if="plan.approvalFact" :type="approvalTone">{{ approvalLabel }}</el-tag>
        </div>
      </header>
      <div class="cut-card__body">
      <el-alert
        v-if="plan.approvalFact?.rejectionReason"
        :title="`审批意见：${plan.approvalFact.rejectionReason}`"
        type="warning"
        :closable="false"
      />
      <CutoverPlanEditor
        v-if="draftContent"
        v-model="draftContent"
        :source-snapshot="sourceSnapshot"
        :task-id="taskId"
        :editable="!props.readonly && !writing && plan.allowedActions.includes('SAVE_DRAFT')"
        :patch-approved="!props.readonly && plan.allowedActions.includes('UPDATE_APPROVED_CONTACTS')"
        @patch-support="patchSupport"
        @upload-pending="editorUploadPending = $event"
      />

      </div>
      </section>
      <footer v-if="visiblePlanActions" class="cut-actions cut-actions--sticky plan-actions">
        <span v-if="hasUnsavedChanges" class="draft-change-hint">内容已修改，请先保存；生成方案会自动保存。</span>
        <el-button
          v-if="!props.readonly && plan.allowedActions.includes('SAVE_DRAFT')"
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="save-plan"
          type="success"
          :loading="writing"
          :disabled="pendingUploads"
          @click="save"
        >保存</el-button>
        <el-button
          v-if="plan.allowedActions.includes('DOWNLOAD_DRAFT')"
          v-hasPermi="['pms:cutover-task:download-plan']"
          data-testid="download-plan"
          :loading="writing"
          :disabled="pendingUploads"
          @click="download"
        >生成并下载方案</el-button>
        <el-button
          v-if="!props.readonly && plan.allowedActions.includes('SUBMIT_PLAN')"
          v-hasPermi="['pms:cutover-task:submit-plan']"
          data-testid="submit-plan"
          type="primary"
          :loading="writing"
          :disabled="pendingUploads || hasUnsavedChanges"
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
      </footer>
      <el-descriptions v-if="downloaded" :column="1" border class="download-result">
        <el-descriptions-item label="初稿文件"><PmsFileVersionReferenceList owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
          purpose-code="FULL_PLAN" :reference-key="downloaded.fileArtifactFact.referenceKey" :artifact-id="downloaded.fileArtifactFact.artifactId" :version-no="downloaded.fileArtifactFact.versionNo" /></el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ formatWireDateTime(downloaded.downloadedAt) }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <template v-else>
      <el-empty v-if="!canCreate" description="当前阶段尚无可读取的割接方案" />
      <section v-else class="cut-card create-plan">
        <header class="cut-card__header"><h3><Icon icon="ep:document" />割接方案</h3></header>
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
            :artifact-id="createFileFact?.artifactId as number | undefined"
            :expected-reference-version="createFileFact?.fileFactVersion.referenceVersion"
            category-code="CUTOVER_PLAN"
            @completed="completeCreateUpload"
          />
          <p class="cut-help">支持Word、PDF，单文件不超过50MB。</p>
          <PmsFileVersionReferenceList v-if="createFileFact" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
            purpose-code="FULL_PLAN" :reference-key="createFileFact.referenceKey" :artifact-id="createFileFact.artifactId" :version-no="createFileFact.versionNo" />
          <el-checkbox v-model="ownershipConfirmed" data-testid="create-plan-ownership">确认本人有权使用并提交该完整方案文件</el-checkbox>
        </div>
        <el-button
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="create-plan"
          type="primary"
          :disabled="pendingUploads || (createMode === 'FULL_FILE_UPLOAD' && (!createFileFact || !ownershipConfirmed))"
          :loading="writing"
          @click="create"
        >开始编制方案</el-button>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import * as CutoverApi from '@/api/pms/cutover'
import * as FileApi from '@/api/pms/platform/file/version-reference'
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
import {
  createCutoverPlanIntentStore,
  createCutoverPlanWriteBarrier,
  cutoverPlanRecoveryAction,
  formatWireDateTime
} from '../shared/cutoverTaskInteraction'
import CutoverPlanEditor from './CutoverPlanEditor.vue'

const props = defineProps<{ taskId: string | number; taskVersion: number; manualGrade: ManualGrade | null; readonly?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const loading = ref(false)
const writing = ref(false)
const plan = ref<CutoverPlanView | null>(null)
const draftContent = ref<WritableCutoverPlanContent | null>(null)
const downloaded = ref<DownloadCutoverPlanDraftResult | null>(null)
const editorUploadPending = ref(false)
const createUploadRef = ref<InstanceType<typeof PmsFileUploader>>()
const createFileReads = ref(0)
const pendingUploads = computed(() => editorUploadPending.value || createFileReads.value > 0
  || !!createUploadRef.value && (createUploadRef.value.isBusy() || createUploadRef.value.hasPendingFile()))
const visiblePlanActions = computed(() => plan.value?.allowedActions.some(action => action === 'DOWNLOAD_DRAFT'
  || (!props.readonly && ['SAVE_DRAFT', 'SUBMIT_PLAN', 'REVISE_PLAN'].includes(action))))
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
const modeLabel = computed(() => ({
  ONLINE_TEMPLATE_STANDARD: '在线标准方案',
  ONLINE_TEMPLATE_SIMPLE_D: 'D级简易方案',
  FULL_FILE_UPLOAD: '完整方案文件'
}[plan.value?.content?.editMode || 'ONLINE_TEMPLATE_STANDARD']))
const approvalLabel = computed(() => ({
  PENDING: '审批中', PAUSED_SOURCE_INVALIDATED: '来源变化，审批暂停', APPROVED: '审批通过', REJECTED: '审批驳回'
}[plan.value?.approvalFact?.status || 'PENDING']))
const approvalTone = computed(() => plan.value?.approvalFact?.status === 'APPROVED'
  ? 'success'
  : plan.value?.approvalFact?.status === 'REJECTED'
    ? 'danger'
    : 'warning')

const refresh = async () => {
  loading.value = true
  try {
    plan.value = await CutoverApi.getCutoverPlan(props.taskId)
    if (downloaded.value && (String(downloaded.value.planRevisionId) !== String(plan.value.planRevisionId)
      || downloaded.value.planVersion !== plan.value.planVersion)) downloaded.value = null
    const content = plan.value.content
    draftContent.value = content
      ? JSON.parse(JSON.stringify(content)) as WritableCutoverPlanContent
      : null
  } finally {
    loading.value = false
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
  if (pendingUploads.value) { message.warning('请完成附件上传和绑定后再保存或提交'); return null }
  if (!(await passBarrier())) return null
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
    if (recovery === 'START_NEW_INTENT') intents.complete(intent)
    if (recovery === 'REFRESH_AGGREGATE' || recovery === 'REFRESH_OWNER_FACTS') {
      intents.complete(intent)
      try { await refresh(); emit('changed') } catch { message.warning('权威事实刷新失败，请稍后重试') }
    } else if (recovery === 'RETRY_SAME_KEY') {
      message.warning('响应未知或命令处理中，已保留本次幂等键')
    }
    return null
  } finally {
    writing.value = false
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
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined || !draftContent.value) return false
  const content = contentForSave(JSON.parse(JSON.stringify(draftContent.value)) as WritableCutoverPlanContent)
  const result = await runIntent(`save:${plan.value.planRevisionId}:${plan.value.planVersion}:${JSON.stringify(content)}`,
    (key) => CutoverApi.saveCutoverPlanDraft({ taskId: props.taskId, taskVersion: props.taskVersion, planVersion: plan.value!.planVersion!, data: content, idempotencyKey: key }))
  if (result) message.success('方案草稿已保存')
  return !!result
}

const contentForSave = (content: WritableCutoverPlanContent): WritableCutoverPlanContent => {
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
  && JSON.stringify(contentForSave(draftContent.value)) !== JSON.stringify(contentForSave(plan.value.content)))
const download = async () => {
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  if (downloaded.value && String(downloaded.value.planRevisionId) === String(plan.value.planRevisionId)
    && downloaded.value.planVersion === plan.value.planVersion && !hasUnsavedChanges.value) {
    return accessGeneratedDraft(downloaded.value)
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
  const result = await runIntent(`submit:${plan.value.planRevisionId}:${plan.value.planVersion}`,
    (key) => CutoverApi.submitCutoverPlan({ taskId: props.taskId, taskVersion: props.taskVersion, planVersion: plan.value!.planVersion!, idempotencyKey: key }))
  if (result) message.success('方案已提交审批')
}
const revise = async () => {
  if (!plan.value?.planRevisionId) return
  const reason = plan.value.status === 'INVALIDATED' ? 'SOURCE_REPLACED' : 'APPROVAL_REJECTED'
  const result = await runIntent(`revise:${plan.value.planRevisionId}:${reason}`,
    (key) => CutoverApi.reviseCutoverPlan({ taskId: props.taskId, taskVersion: props.taskVersion, data: { sourcePlanRevisionId: plan.value!.planRevisionId!, reason }, idempotencyKey: key }))
  if (result) message.success('修订草稿已创建')
}
const patchSupport = async (row: CutoverPlanSupportArrangement) => {
  if (!row.arrangementId || plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  let reason: string
  try { const response = await message.prompt('请输入本次联系人、电话或到位时间的变更原因', '更新保障联系人'); reason = response.value.trim() } catch { return }
  if (!reason) { message.warning('请填写变更原因'); return }
  const data = { personName: row.personName, phone: row.phone, arrivalTime: row.arrivalTime, reason,
    customerContactReference: row.customerContactReference ?? null }
  const result = await runIntent(`contact:${row.arrangementId}:${plan.value.planVersion}:${JSON.stringify(data)}`,
    (key) => CutoverApi.patchApprovedCutoverPlanContact({ taskId: props.taskId, arrangementId: row.arrangementId!, planVersion: plan.value!.planVersion!, data: data, idempotencyKey: key }))
  if (result) message.success('批准联系人已更新')
}

const fileKey = (referenceKey: string) => ({
  ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: String(props.taskId),
  purposeCode: 'FULL_PLAN', referenceKey
})
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

onMounted(refresh)
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
