<template>
  <section v-loading="loading" class="cutover-plan-panel">
    <template v-if="plan && plan.planRevisionId !== null">
      <header class="plan-heading">
        <div>
          <span>Revision {{ plan.revisionNo }} · Version {{ plan.planVersion }}</span>
          <h3>{{ modeLabel }}</h3>
        </div>
        <div class="plan-tags">
          <el-tag>{{ plan.status }}</el-tag>
          <el-tag v-if="plan.approvalFact" :type="approvalTone">{{ approvalLabel }}</el-tag>
        </div>
      </header>

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
        :editable="plan.allowedActions.includes('SAVE_DRAFT')"
        :patch-approved="plan.allowedActions.includes('UPDATE_APPROVED_CONTACTS')"
        @patch-support="patchSupport"
      />
      <section v-else-if="plan.content?.editMode === 'LEGACY_READ_ONLY'" class="legacy-plan">
        <el-alert title="该方案来自历史前向迁移，仅供查看" type="info" :closable="false" />
        <article v-for="step in plan.content.steps" :key="`${step.sectionCode}:${step.stepNo}`">
          <strong>{{ sectionLabels[step.sectionCode] }}</strong>
          <p>{{ step.content }}</p>
        </article>
      </section>

      <footer class="plan-actions">
        <el-button
          v-if="plan.allowedActions.includes('SAVE_DRAFT')"
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="save-plan"
          type="primary"
          :loading="writing"
          @click="save"
        >保存方案</el-button>
        <el-button
          v-if="plan.allowedActions.includes('DOWNLOAD_DRAFT')"
          v-hasPermi="['pms:cutover-task:download-plan']"
          data-testid="download-plan"
          :loading="writing"
          @click="download"
        >生成并下载初稿</el-button>
        <el-button
          v-if="plan.allowedActions.includes('SUBMIT_PLAN')"
          v-hasPermi="['pms:cutover-task:submit-plan']"
          data-testid="submit-plan"
          type="success"
          :loading="writing"
          @click="submit"
        >提交审批</el-button>
        <el-button
          v-if="plan.allowedActions.includes('REVISE_PLAN')"
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="revise-plan"
          type="warning"
          :loading="writing"
          @click="revise"
        >创建修订版</el-button>
      </footer>
      <el-descriptions v-if="downloaded" :column="1" border class="download-result">
        <el-descriptions-item label="初稿稳定引用">{{ downloaded.fileArtifactFact.referenceKey }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ formatWireDateTime(downloaded.downloadedAt) }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <template v-else>
      <el-empty v-if="!canCreate" description="当前阶段尚无可读取的割接方案" />
      <section v-else class="create-plan">
        <h3>选择方案编制方式</h3>
        <el-radio-group v-model="createMode" data-testid="plan-create-mode">
          <el-radio-button v-if="manualGrade !== 'D'" value="ONLINE_TEMPLATE_STANDARD">在线标准方案</el-radio-button>
          <el-radio-button v-if="manualGrade === 'D'" value="ONLINE_TEMPLATE_SIMPLE_D">D级简易方案</el-radio-button>
          <el-radio-button value="FULL_FILE_UPLOAD">上传已有完整方案</el-radio-button>
        </el-radio-group>
        <div v-if="createMode === 'FULL_FILE_UPLOAD'" class="upload-create">
          <PmsFileUploader
            data-testid="create-plan-uploader"
            owner-context="CUT"
            object-type="CUTOVER_PLAN"
            :object-id="String(taskId)"
            purpose-code="FULL_PLAN"
            :reference-key="`cutover-plan-${taskId}`"
            category-code="CUTOVER_PLAN"
            @completed="completeCreateUpload"
          />
          <p v-if="createFileFact">已选择 {{ createFileFact.referenceKey }} / v{{ createFileFact.versionNo }}</p>
          <el-checkbox v-model="ownershipConfirmed" data-testid="create-plan-ownership">确认本人有权使用并提交该完整方案文件</el-checkbox>
        </div>
        <el-button
          v-hasPermi="['pms:cutover-task:save-plan']"
          data-testid="create-plan"
          type="primary"
          :disabled="createMode === 'FULL_FILE_UPLOAD' && (!createFileFact || !ownershipConfirmed)"
          :loading="writing"
          @click="create"
        >创建方案草稿</el-button>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import * as CutoverApi from '@/api/pms/cutover/cutover-task'
import * as FileApi from '@/api/pms/platform/file'
import type {
  CutoverPlanFileFact,
  CutoverPlanSourceSnapshot,
  CutoverPlanSupportArrangement,
  CutoverPlanView,
  DownloadCutoverPlanDraftResult,
  ManualGrade,
  WritableCutoverPlanContent
} from '@/api/pms/cutover/cutover-task'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'
import { useMessage } from '@/hooks/web/useMessage'
import {
  createCutoverPlanIntentStore,
  createCutoverPlanWriteBarrier,
  cutoverPlanRecoveryAction,
  formatWireDateTime
} from '../cutoverTaskInteraction'
import CutoverPlanEditor from './CutoverPlanEditor.vue'

const props = defineProps<{ taskId: string | number; taskVersion: number; manualGrade: ManualGrade | null }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const loading = ref(false)
const writing = ref(false)
const plan = ref<CutoverPlanView | null>(null)
const draftContent = ref<WritableCutoverPlanContent | null>(null)
const downloaded = ref<DownloadCutoverPlanDraftResult | null>(null)
const createMode = ref<'ONLINE_TEMPLATE_STANDARD' | 'ONLINE_TEMPLATE_SIMPLE_D' | 'FULL_FILE_UPLOAD'>(
  props.manualGrade === 'D' ? 'ONLINE_TEMPLATE_SIMPLE_D' : 'ONLINE_TEMPLATE_STANDARD'
)
const createFileFact = ref<CutoverPlanFileFact | null>(null)
const ownershipConfirmed = ref(false)
const intents = createCutoverPlanIntentStore()
const barrier = createCutoverPlanWriteBarrier()

const canCreate = computed(() => plan.value?.allowedActions.includes('CREATE_DRAFT') ?? false)
const sourceSnapshot = computed<CutoverPlanSourceSnapshot | null>(() => {
  const source = plan.value?.sourceSnapshot
  return source && 'snapshotVersion' in source ? source : null
})
const modeLabel = computed(() => ({
  ONLINE_TEMPLATE_STANDARD: '在线标准方案',
  ONLINE_TEMPLATE_SIMPLE_D: 'D级简易方案',
  FULL_FILE_UPLOAD: '完整方案文件',
  LEGACY_READ_ONLY: '历史只读方案'
}[plan.value?.content?.editMode || 'LEGACY_READ_ONLY']))
const approvalLabel = computed(() => ({
  PENDING: '审批中', PAUSED_SOURCE_INVALIDATED: '来源变化，审批暂停', APPROVED: '审批通过', REJECTED: '审批驳回'
}[plan.value?.approvalFact?.status || 'PENDING']))
const approvalTone = computed(() => plan.value?.approvalFact?.status === 'APPROVED'
  ? 'success'
  : plan.value?.approvalFact?.status === 'REJECTED'
    ? 'danger'
    : 'warning')
const sectionLabels: Record<string, string> = {
  PRE_OPERATION: '操作前准备', OPERATION: '阶段操作', CLOSING_COLLECTION: '收尾与信息采集',
  POST_BUSINESS_TEST: '割接后业务测试', ROLLBACK: '回退步骤', POST_CUTOVER_SUPPORT: '割接后保障'
}

const refresh = async () => {
  loading.value = true
  try {
    plan.value = await CutoverApi.getCutoverPlan(props.taskId)
    const content = plan.value.content
    draftContent.value = content && content.editMode !== 'LEGACY_READ_ONLY'
      ? hydrateContent(JSON.parse(JSON.stringify(content)) as WritableCutoverPlanContent)
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
    CutoverApi.createCutoverPlanDraft(props.taskId, props.taskVersion, data, key))
  if (result) message.success('方案草稿已创建')
}
const save = async () => {
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined || !draftContent.value) return
  const content = contentForSave(JSON.parse(JSON.stringify(draftContent.value)) as WritableCutoverPlanContent)
  const result = await runIntent(`save:${plan.value.planRevisionId}:${plan.value.planVersion}:${JSON.stringify(content)}`,
    (key) => CutoverApi.saveCutoverPlanDraft(props.taskId, props.taskVersion, plan.value!.planVersion!, content, key))
  if (result) message.success('方案草稿已保存')
}

const supportRoles: CutoverPlanSupportArrangement['roleCode'][] = [
  'CUSTOMER', 'DP_FIRST_LINE', 'DP_SECOND_LINE', 'DP_RND'
]
const hydrateContent = (content: WritableCutoverPlanContent): WritableCutoverPlanContent => {
  if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') return content
  const supportArrangements = supportRoles.map((roleCode) => content.supportArrangements
    .find((row) => row.roleCode === roleCode) || {
    arrangementId: null, roleCode, personName: '', dutyDescription: '', phone: '', arrivalTime: 0
  })
  return { ...content, supportArrangements }
}
const contentForSave = (content: WritableCutoverPlanContent): WritableCutoverPlanContent => {
  if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') return content
  return {
    ...content,
    supportArrangements: content.supportArrangements.filter((row) => row.personName.trim()
      && row.dutyDescription.trim() && row.phone.trim()
      && Number.isSafeInteger(Number(row.arrivalTime)) && Number(row.arrivalTime) > 0)
  }
}
const download = async () => {
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  const result = await runIntent(`download:${plan.value.planRevisionId}:${plan.value.planVersion}`,
    (key) => CutoverApi.downloadCutoverPlanDraft(props.taskId, plan.value!.planVersion!, key))
  if (result) { downloaded.value = result; message.success('初稿已生成') }
}
const submit = async () => {
  if (plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  const result = await runIntent(`submit:${plan.value.planRevisionId}:${plan.value.planVersion}`,
    (key) => CutoverApi.submitCutoverPlan(props.taskId, props.taskVersion, plan.value!.planVersion!, key))
  if (result) message.success('方案已提交审批')
}
const revise = async () => {
  if (!plan.value?.planRevisionId) return
  const reason = plan.value.status === 'INVALIDATED' ? 'SOURCE_REPLACED' : 'APPROVAL_REJECTED'
  const result = await runIntent(`revise:${plan.value.planRevisionId}:${reason}`,
    (key) => CutoverApi.reviseCutoverPlan(props.taskId, props.taskVersion,
      { sourcePlanRevisionId: plan.value!.planRevisionId!, reason }, key))
  if (result) message.success('修订草稿已创建')
}
const patchSupport = async (row: CutoverPlanSupportArrangement) => {
  if (!row.arrangementId || plan.value?.planVersion === null || plan.value?.planVersion === undefined) return
  const data = { personName: row.personName, phone: row.phone, arrivalTime: row.arrivalTime }
  const result = await runIntent(`contact:${row.arrangementId}:${plan.value.planVersion}:${JSON.stringify(data)}`,
    (key) => CutoverApi.patchApprovedCutoverPlanContact(
      props.taskId, row.arrangementId!, plan.value!.planVersion!, data, key))
  if (result) message.success('批准联系人已更新')
}

const fileKey = (referenceKey: string) => ({
  ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: String(props.taskId),
  purposeCode: 'FULL_PLAN', referenceKey
})
const completeCreateUpload = async (selection: FileSelection) => {
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
}

onMounted(refresh)
</script>

<style scoped>
.cutover-plan-panel { min-width: 0; }
.plan-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.plan-heading h3 { margin: 4px 0 0; }
.plan-heading span { color: var(--el-text-color-secondary); }
.plan-tags, .plan-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.plan-actions { margin-top: 20px; }
.download-result { margin-top: 16px; }
.legacy-plan article { margin-top: 16px; padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.legacy-plan p { margin: 8px 0 0; white-space: pre-wrap; }
.create-plan { padding: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; }
.upload-create { margin: 16px 0; }
@media (max-width: 767px) { .plan-heading { flex-direction: column; } }
</style>
