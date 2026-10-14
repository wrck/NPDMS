<template>
  <section v-loading="loading" class="closure-panel" v-hasPermi="['pms:cutover-task:query-closure']">
    <el-alert title="由一线统一填写割接结果。提交后结束割接流程，归档记录及附件版本保留供历史查看。" type="info" :closable="false" show-icon />
    <template v-if="closure">
      <section class="cut-card">
        <header class="cut-card__header"><h3><Icon icon="ep:circle-check" />割接结果与闭环</h3><small>{{ closure.closureStatus === 'SUBMITTED' ? '已归档，只读' : '草稿，保存不推进流程' }}</small></header>
        <div class="cut-card__body">
          <CutoverClosureForm ref="closureFormRef" v-model="content" :closure-id="closure.closureId" :editable="canSave && !writing"
            @upload-pending="attachmentPending = $event" @attachment-completed="onAttachmentCompleted" />
          <h4 class="cut-section-title">8. 割接结果<span class="cut-required"> *</span></h4>
          <el-radio-group :model-value="selectedResult ?? undefined" :disabled="!canAttemptSubmit || writing" data-testid="closure-final-result" aria-label="割接最终结果" @update:model-value="selectedResult = $event as 'SUCCESS' | 'FAILED'">
            <el-radio value="SUCCESS">成功</el-radio><el-radio value="FAILED">失败</el-radio>
          </el-radio-group>
          <p class="cut-help result-help">成功与失败均可归档；项目、资产和外部系统的处理结果以实际回执为准。</p>
        </div>
        <footer v-if="canSave || canAttemptSubmit" class="cut-actions cut-actions--sticky">
          <el-button v-if="canSave" v-hasPermi="['pms:cutover-task:save-closure']" data-testid="save-closure"
            :loading="writing" :disabled="attachmentPending" @click="save">{{ closure.closureId === null ? '创建闭环草稿' : '保存' }}</el-button>
          <el-button v-if="canAttemptSubmit" v-hasPermi="['pms:cutover-task:submit-closure']" data-testid="submit-closure" type="primary"
            :loading="writing" :disabled="attachmentPending || !selectedResult" @click="submitSelected"><Icon icon="ep:upload" />提交（结束流程）</el-button>
        </footer>
      </section>
      <details v-if="closure.closureId !== null" class="cut-card collection-details">
        <summary>采集记录与失败恢复<span class="cut-muted">{{ closure.collectionEvidence.length }} 条记录</span></summary>
        <div class="cut-card__body">
          <CutoverClosureEvidencePanel :task-id="taskId" :closure-id="closure.closureId" :evidence="closure.collectionEvidence" :devices="devices"
            :can-request="!props.readonly && closure.allowedActions.includes('REQUEST_COLLECTION')"
            :can-link-manual="!props.readonly && closure.allowedActions.includes('LINK_MANUAL_RESULT')" @refresh="refreshEvidence" @manual="linkManual" />
        </div>
      </details>
      <el-descriptions v-if="closure.closureStatus === 'SUBMITTED'" :column="1" border class="archive-summary">
        <el-descriptions-item label="闭环结果">{{ closure.content?.finalResult === 'SUCCESS' ? '割接成功' : '割接失败' }}</el-descriptions-item>
        <el-descriptions-item label="稳定结果引用">{{ closure.resultRef || '—' }}</el-descriptions-item>
        <el-descriptions-item label="归档时间">{{ formatWireDateTime(closure.archivedAt) }}</el-descriptions-item>
      </el-descriptions>
      <section v-if="closure.closureStatus === 'SUBMITTED' && closure.content?.finalResult === 'SUCCESS'" class="archive-summary">
        <h3>项目与资产结果</h3>
        <el-button :loading="progressLoading" @click="loadProgress">刷新处理结果</el-button>
        <el-alert v-if="progressError" :title="progressError" type="warning" :closable="false" />
        <template v-if="progress">
          <p>当前项目范围已覆盖 {{ progress.projectCoverage.coveredDeviceIds.length }} / {{ progress.projectCoverage.requiredDevices.length }} 台设备。{{ progress.projectCoverage.complete ? '覆盖已完整，阶段推进由项目规则判定。' : '尚未覆盖全部范围。' }}</p>
          <el-table :data="progress.assetReceipts" empty-text="资产结果正在等待处理，可稍后刷新">
            <el-table-column prop="serialNumber" label="设备序列号" />
            <el-table-column label="资产处理"><template #default="{ row }">{{ row.refreshStatus === 'REFRESHED' ? '已记录割接服务事实' : '设备归属已变化，保留原项目历史' }}</template></el-table-column>
            <el-table-column label="处理时间"><template #default="{ row }">{{ formatWireDateTime(row.refreshedAt) }}</template></el-table-column>
          </el-table>
        </template>
      </section>
    </template>
  </section>
</template>
<script setup lang="ts">
import * as CutoverApi from '@/api/pms/cutover/workbench'
import type { DeviceScopeEntry, CutoverClosureContent, CutoverClosureFileFact, CutoverClosureView, LinkCutoverClosureManualResultRequest, WireLong } from '@/api/pms/cutover/workbench'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import { createCutoverPlanIntentStore, createCutoverPlanWriteBarrier, cutoverPlanRecoveryAction, formatWireDateTime } from '../cutoverTaskInteraction'
import CutoverClosureEvidencePanel from './CutoverClosureEvidencePanel.vue'
import CutoverClosureForm from './CutoverClosureForm.vue'

const props = defineProps<{ taskId: WireLong; devices: DeviceScopeEntry[]; readonly?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const loading = ref(false)
const writing = ref(false)
const closureFormRef = ref<InstanceType<typeof CutoverClosureForm>>()
const attachmentPending = ref(false)
const selectedResult = ref<'SUCCESS' | 'FAILED' | null>(null)
const closure = ref<CutoverClosureView | null>(null)
const progress = ref<CutoverApi.CutoverResultProgress | null>(null)
const progressLoading = ref(false)
const progressError = ref('')
const loadProgress = async () => {
  progressLoading.value = true
  try { progress.value = await CutoverApi.getResultProgress(props.taskId); progressError.value = '' }
  catch { progressError.value = '结果处理状态暂时无法读取，请稍后刷新。割接归档事实已保留。' }
  finally { progressLoading.value = false }
}
const content = ref<CutoverClosureContent>(emptyContent())
const onAttachmentCompleted = (fact: CutoverClosureFileFact) => {
  const attachments = content.value.attachments.filter(row => row.purposeCode !== fact.purposeCode)
  attachments.push(fact)
  attachments.sort((a, b) => a.purposeCode.localeCompare(b.purposeCode) || a.referenceKey.localeCompare(b.referenceKey))
  content.value = { ...content.value, attachments }
}
const intents = createCutoverPlanIntentStore()
const barrier = createCutoverPlanWriteBarrier()
const canSave = computed(() => !props.readonly && !!closure.value && (closure.value.allowedActions.includes('CREATE_CLOSURE') || closure.value.allowedActions.includes('SAVE_CLOSURE')))
const canSubmit = computed(() => !props.readonly && (closure.value?.allowedActions.includes('SUBMIT_CLOSURE') ?? false))
const canAttemptSubmit = computed(() => canSubmit.value || (canSave.value && checkPermi(['pms:cutover-task:submit-closure'])))

function emptyContent(): CutoverClosureContent {
  return {
    preCheckNormal: null, preCheckDetail: null, executionNormal: null, executionDetail: null,
    testNormal: null, testDetail: null, rollbackOccurred: null, rollbackSuccessful: null,
    rollbackReason: null, legacyItems: null, finalResult: null, attachments: []
  }
}
const refresh = async () => {
  loading.value = true
  try {
    closure.value = await CutoverApi.getCutoverClosure(props.taskId)
    content.value = closure.value.content ? JSON.parse(JSON.stringify(closure.value.content)) : emptyContent()
    if (closure.value.closureStatus === 'SUBMITTED') selectedResult.value = closure.value.content?.finalResult || null
    if (closure.value.closureStatus === 'SUBMITTED' && closure.value.content?.finalResult === 'SUCCESS') await loadProgress()
  } finally { loading.value = false }
}
const run = async <T,>(intent: string, call: (key: string) => Promise<T>) => {
  if (closureFormRef.value?.hasPendingUploads()) {
    message.warning('请等待闭环附件上传和绑定完成后再保存或归档')
    return null
  }
  const before = await barrier.beforeWrite()
  if (before !== 'PROCEED') {
    message[before === 'REFRESHED' ? 'success' : 'warning'](before === 'REFRESHED'
      ? '上次命令结果已刷新，请按最新闭环记录继续操作'
      : '上次命令已成功，但页面仍未刷新；不会重复发送业务命令')
    return null
  }
  writing.value = true
  const key = intents.key(intent)
  try {
    const result = await call(key)
    intents.complete(intent)
    try { await refresh(); emit('changed') }
    catch { barrier.register(async () => { await refresh(); emit('changed') }); message.warning('命令已成功，但刷新失败；下一次写操作只重试刷新') }
    return result
  } catch (error) {
    const recovery = cutoverPlanRecoveryAction(error)
    if (recovery === 'START_NEW_INTENT') intents.complete(intent)
    if (recovery === 'REFRESH_AGGREGATE' || recovery === 'REFRESH_OWNER_FACTS') {
      intents.complete(intent)
      try { await refresh(); emit('changed') } catch { message.warning('权威事实刷新失败，请稍后重试') }
    } else if (recovery === 'RETRY_SAME_KEY') message.warning('响应未知或命令处理中，已保留本次幂等键')
    return null
  } finally { writing.value = false }
}
const requireDraftVersion = () => {
  if (closure.value?.closureVersion === null || closure.value?.closureVersion === undefined) throw new Error('闭环草稿尚未创建')
  return closure.value.closureVersion
}
const save = async () => {
  if (!closure.value) return
  const payload = JSON.parse(JSON.stringify(content.value)) as CutoverClosureContent
  payload.finalResult = null
  const result = await run(`save:${closure.value.closureVersion ?? 'new'}:${JSON.stringify(payload)}`, (key) =>
    CutoverApi.saveCutoverClosure(props.taskId, closure.value!.taskVersion, closure.value!.closureVersion, payload, key))
  if (result) message.success('闭环草稿已保存')
  return result
}
const refreshEvidence = async () => { closure.value = await CutoverApi.getCutoverClosure(props.taskId) }
const linkManual = async (data: LinkCutoverClosureManualResultRequest) => {
  if (!closure.value) return
  const result = await run(`manual:${JSON.stringify(data)}`, (key) =>
    CutoverApi.linkCutoverClosureManualResult(props.taskId, closure.value!.taskVersion, requireDraftVersion(), data, key))
  if (result) message.success('人工采集结果已关联')
}
const submit = async (finalResult: 'SUCCESS' | 'FAILED') => {
  if (!closure.value || attachmentPending.value || writing.value) return
  if (closure.value.closureId === null || JSON.stringify({ ...closure.value.content, finalResult: null }) !== JSON.stringify({ ...content.value, finalResult: null })) {
    if (!(await save())) return
  }
  if (!canSubmit.value) { message.warning('闭环草稿已保存，请补齐检查、执行、测试、回退及两项必需附件后再提交。'); return }
  const result = await run(`submit:${requireDraftVersion()}:${finalResult}`, (key) =>
    CutoverApi.submitCutoverClosure(props.taskId, closure.value!.taskVersion, requireDraftVersion(), finalResult, key))
  if (result) message.success(finalResult === 'SUCCESS' ? '割接成功并已归档' : '割接失败并已归档')
}
const submitSelected = async () => { if (selectedResult.value) await submit(selectedResult.value) }
onMounted(refresh)
</script>

<style scoped>
.closure-panel { min-width: 0; }
.result-help { margin-top: 6px; }
.collection-details > summary { display: flex; justify-content: space-between; gap: 12px; padding: 14px 20px; color: #1a2332; font-size: 13px; cursor: pointer; }
.archive-summary { margin-top: 16px; padding: 20px; border-radius: 8px; background: #fff; }
</style>
