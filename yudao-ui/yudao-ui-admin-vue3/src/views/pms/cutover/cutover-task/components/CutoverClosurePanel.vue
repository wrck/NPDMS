<template>
  <section v-loading="loading" class="closure-panel" v-hasPermi="['pms:cutover-task:query-closure']">
    <header v-if="closure" class="closure-heading">
      <div><h2>P6 割接关闭</h2><p>Version {{ closure.closureVersion ?? '—' }} · {{ closure.closureStatus || '尚未创建' }}</p></div>
      <el-tag :type="closure.taskStatus === 'ARCHIVED' ? 'success' : 'warning'">{{ closure.taskStatus }}</el-tag>
    </header>
    <template v-if="closure">
      <CutoverClosureForm
        v-model="content"
        :closure-id="closure.closureId"
        :editable="canSave"
      />
      <div class="closure-actions">
        <el-button
          v-if="canSave"
          v-hasPermi="['pms:cutover-task:save-closure']"
          data-testid="save-closure"
          type="primary"
          :loading="writing"
          @click="save"
        >{{ closure.closureId === null ? '创建关闭草稿' : '保存关闭草稿' }}</el-button>
        <el-button
          v-if="canSubmit"
          v-hasPermi="['pms:cutover-task:submit-closure']"
          data-testid="submit-closure-success"
          type="success"
          :loading="writing"
          @click="submit('SUCCESS')"
        >确认成功并归档</el-button>
        <el-button
          v-if="canSubmit"
          v-hasPermi="['pms:cutover-task:submit-closure']"
          data-testid="submit-closure-failed"
          type="danger"
          :loading="writing"
          @click="submit('FAILED')"
        >确认失败并归档</el-button>
      </div>
      <CutoverClosureEvidencePanel
        v-if="closure.closureId !== null"
        :closure-id="closure.closureId"
        :evidence="closure.collectionEvidence"
        :can-request="closure.allowedActions.includes('REQUEST_COLLECTION')"
        :can-link-manual="closure.allowedActions.includes('LINK_MANUAL_RESULT')"
        @request="requestCollection"
        @manual="linkManual"
      />
      <el-descriptions v-if="closure.closureStatus === 'SUBMITTED'" :column="1" border class="archive-summary">
        <el-descriptions-item label="关闭结果">{{ closure.resultRef ? 'SUCCESS' : 'FAILED' }}</el-descriptions-item>
        <el-descriptions-item label="稳定结果引用">{{ closure.resultRef || '—' }}</el-descriptions-item>
        <el-descriptions-item label="归档时间">{{ formatWireDateTime(closure.archivedAt) }}</el-descriptions-item>
      </el-descriptions>
    </template>
  </section>
</template>

<script setup lang="ts">
import * as CutoverApi from '@/api/pms/cutover/cutover-task'
import type { CutoverClosureCollectionRequest, CutoverClosureContent, CutoverClosureView, LinkCutoverClosureManualResultRequest, WireLong } from '@/api/pms/cutover/cutover-task'
import { useMessage } from '@/hooks/web/useMessage'
import { createCutoverPlanIntentStore, createCutoverPlanWriteBarrier, cutoverPlanRecoveryAction, formatWireDateTime } from '../cutoverTaskInteraction'
import CutoverClosureEvidencePanel from './CutoverClosureEvidencePanel.vue'
import CutoverClosureForm from './CutoverClosureForm.vue'

const props = defineProps<{ taskId: WireLong }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const loading = ref(false)
const writing = ref(false)
const closure = ref<CutoverClosureView | null>(null)
const content = ref<CutoverClosureContent>(emptyContent())
const intents = createCutoverPlanIntentStore()
const barrier = createCutoverPlanWriteBarrier()
const canSave = computed(() => !!closure.value && (closure.value.allowedActions.includes('CREATE_CLOSURE') || closure.value.allowedActions.includes('SAVE_CLOSURE')))
const canSubmit = computed(() => closure.value?.allowedActions.includes('SUBMIT_CLOSURE') ?? false)

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
  } finally { loading.value = false }
}
const run = async <T,>(intent: string, call: (key: string) => Promise<T>) => {
  const before = await barrier.beforeWrite()
  if (before !== 'PROCEED') {
    message[before === 'REFRESHED' ? 'success' : 'warning'](before === 'REFRESHED'
      ? '上次命令结果已刷新，请按最新关闭记录继续操作'
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
  if (closure.value?.closureVersion === null || closure.value?.closureVersion === undefined) throw new Error('关闭草稿尚未创建')
  return closure.value.closureVersion
}
const save = async () => {
  if (!closure.value) return
  const payload = JSON.parse(JSON.stringify(content.value)) as CutoverClosureContent
  payload.finalResult = null
  const result = await run(`save:${closure.value.closureVersion ?? 'new'}:${JSON.stringify(payload)}`, (key) =>
    CutoverApi.saveCutoverClosure(props.taskId, closure.value!.taskVersion, closure.value!.closureVersion, payload, key))
  if (result) message.success('关闭草稿已保存')
}
const requestCollection = async (data: CutoverClosureCollectionRequest) => {
  if (!closure.value) return
  const sanitized = data.authenticationMode === 'TRANSIENT_CREDENTIAL' ? { ...data, transientSecret: '<transient>' } : data
  const result = await run(`collection:${JSON.stringify(sanitized)}`, (key) =>
    CutoverApi.requestCutoverClosureCollection(props.taskId, closure.value!.taskVersion, requireDraftVersion(), data, key))
  if (result) message.success('单设备采集请求已发送')
}
const linkManual = async (data: LinkCutoverClosureManualResultRequest) => {
  if (!closure.value) return
  const result = await run(`manual:${JSON.stringify(data)}`, (key) =>
    CutoverApi.linkCutoverClosureManualResult(props.taskId, closure.value!.taskVersion, requireDraftVersion(), data, key))
  if (result) message.success('人工采集结果已关联')
}
const submit = async (finalResult: 'SUCCESS' | 'FAILED') => {
  if (!closure.value) return
  const result = await run(`submit:${requireDraftVersion()}:${finalResult}`, (key) =>
    CutoverApi.submitCutoverClosure(props.taskId, closure.value!.taskVersion, requireDraftVersion(), finalResult, key))
  if (result) message.success(finalResult === 'SUCCESS' ? '割接成功并已归档' : '割接失败并已归档')
}
onMounted(refresh)
</script>

<style scoped>
.closure-panel { min-width: 0; }
.closure-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.closure-heading h2 { margin: 0; }
.closure-heading p { color: var(--el-text-color-secondary); }
.closure-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.archive-summary { margin-top: 18px; }
@media (max-width: 767px) { .closure-heading { flex-direction: column; } .closure-actions :deep(.el-button) { width: 100%; margin-left: 0; } }
</style>
