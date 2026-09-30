<template>
  <el-dialog v-model="visible" title="交付件提交与判定" width="min(780px, 94vw)" :before-close="beforeClose" destroy-on-close>
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-if="detail">
      <h3>{{ detail.name }}</h3>
      <p>状态：{{ statusLabel(detail.status) }} · 至少 {{ detail.configuration.minimumQuantity ?? 1 }} 项有效材料</p>
      <p class="hint">提交后按模板条件自动判定。文件或成果有效且配置条件满足时，交付件满足门禁。</p>
      <el-alert v-if="!detail.writable" title="当前交付件只读：请确认项目仍在进行中，且具有该项目的材料管理权限。" type="info" :closable="false" />
      <el-alert v-if="detail.configuration.automaticSources?.length" title="业务页面上传的匹配文档会自动归集；归集完成后刷新查看，无需重复上传。" type="info" :closable="false" />
      <el-alert v-if="outcome" :title="outcome" :type="satisfied ? 'success' : 'warning'" :closable="false" show-icon />
      <el-alert v-if="detail.automaticSource" type="info" :closable="false" :title="detail.automaticSource === 'ACCEPTANCE_REPORT' ? '材料由验收报告自动关联，请在验收报告中办理和查看版本历史。' : '材料由满意度结果自动关联，请在满意度业务中办理和查看结果历史。'" />
      <el-form v-if="detail.writable && !detail.automaticSource" label-position="top" class="submission-form">
        <el-form-item label="材料来源">
          <el-radio-group v-model="sourceType" :disabled="busy || uploadBusy" @change="clearAttempt">
            <el-radio-button v-if="detail.configuration.allowedSources?.includes('UPLOAD')" value="UPLOAD">上传文件</el-radio-button>
            <el-radio-button v-if="detail.configuration.allowedSources?.includes('BUSINESS_RESULT')" value="BUSINESS_RESULT">关联业务成果</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="sourceType === 'UPLOAD'">
          <PmsFileUploader ref="uploader" :key="slotKey" v-bind="uploadKey(slotKey)" category-code="PROJECT_DELIVERABLE_DOCUMENT"
            :disabled="busy" @completed="uploaded" />
          <div v-for="file in selectedFiles" :key="file.referenceKey" class="selected-file">
            <PmsFileReferenceList v-bind="uploadKey(file.referenceKey)" :artifact-id="file.artifactId" :version-no="file.versionNo" />
            <el-button link :disabled="busy" @click="removeSelection(file.referenceKey)">从本次提交移除</el-button>
          </div>
        </template>
        <template v-else-if="sourceType === 'BUSINESS_RESULT'">
          <el-form-item label="业务成果类型">
            <el-select v-model="typeIndex" :disabled="busy" aria-label="业务成果类型" @change="loadCandidates(false)">
              <el-option v-for="(item, index) in types" :key="index" :value="index" :label="typeLabel(item.type)" />
            </el-select>
          </el-form-item>
          <el-form-item label="有效成果">
            <el-select v-model="resultIndex" :disabled="busy" :loading="candidatesLoading" aria-label="有效成果" placeholder="选择当前项目的有效成果">
              <el-option v-for="(item, index) in candidates" :key="`${item.objectId}:${item.resultId}`" :value="index"
                :label="`业务对象 ${item.objectId} · 成果 ${item.resultId}${item.businessRevision ? ' · V' + item.businessRevision : ''}`" />
            </el-select>
            <el-button v-if="!candidatesComplete" link :loading="candidatesLoading" @click="loadCandidates(true)">加载更多</el-button>
            <p v-if="!candidatesLoading && !candidates.length" class="hint">当前项目暂无此类有效成果，请先在业务入口完成办理。</p>
          </el-form-item>
        </template>
        <el-button v-hasPermi="['pms:project:update']" type="primary" :loading="busy" :disabled="!canSubmit || uploadBusy" @click="submit">提交材料</el-button>
      </el-form>
      <el-button v-if="detail.writable" v-hasPermi="['pms:project:update']" :disabled="busy || uploadBusy" @click="refresh">重新检测</el-button>
      <el-divider>提交历史</el-divider>
      <el-empty v-if="!detail.history.length" description="尚无提交记录" :image-size="56" />
      <el-collapse v-else>
        <el-collapse-item v-for="item in detail.history" :key="item.id" :name="String(item.id)"
          :title="`${sourceLabel(item.sourceType)} · ${formatDate(item.submittedAt)}`">
          <p v-for="material in item.materials" :key="material.id" class="hint">
            <template v-if="material.materialKind === 'BUSINESS_RESULT'">
              业务成果：{{ typeLabel({ resultType: businessResultLine(material).type }) }} · 成果 {{ businessResultLine(material).resultId }}
            </template>
            <template v-else>
              文件材料：{{ material.fileName || '未命名文件' }}<template v-if="material.versionNo"> · v{{ material.versionNo }}</template><template v-if="material.businessObjectType"> · 来源 {{ material.businessObjectType }}</template>
            </template>
          </p>
        </el-collapse-item>
      </el-collapse>
    </template>
    <template #footer><el-button :disabled="busy || uploadBusy" @click="beforeClose(() => visible = false)">关闭</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { generateUUID } from '@/utils'
import { computed, ref } from 'vue'
import * as Api from '@/api/pms/acceptance/project-deliverable'
import { type FileSelection as UploadedSelection } from '@/components/PmsFileArtifact/types'
import { PmsFileUploader, PmsFileReferenceList } from '@/components/PmsFileArtifact'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const visible = ref(false), loading = ref(false), busy = ref(false), error = ref(''), outcome = ref(''), satisfied = ref(false)
const detail = ref<Api.DeliverableDetail>()
const sourceType = ref('UPLOAD'), slotKey = ref('')
const selectedFiles = ref<UploadedSelection[]>([])
const uploader = ref<InstanceType<typeof PmsFileUploader>>()
const uploadBusy = computed(() => uploader.value?.isBusy() ?? false)
const types = ref<Api.ResultTypeDescriptor[]>([]), candidates = ref<Api.BusinessResult[]>([])
const typeIndex = ref<number>(), resultIndex = ref<number>()
const candidatesLoading = ref(false), candidatesComplete = ref(true), after = ref<string>()
let attempt: { signature: string; key: string } | undefined
// 统一交付材料上传锚：PLT/DELIVERY_MATERIAL/ACC:project_deliverable:{projectId}，purposeCode=交付件编码。
const uploadKey = (referenceKey: string) => ({
  ownerContext: 'PLT', objectType: 'DELIVERY_MATERIAL',
  objectId: `ACC:project_deliverable:${props.projectId}`,
  purposeCode: detail.value?.code ?? '', referenceKey
})
const clearAttempt = () => { attempt = undefined }
const typeLabel = (type: { resultType: string }) => ({ SURVEY_CONFIRMED: '工勘确认成果', REQUIREMENT_ANALYSIS_COMPLETED: '需求分析成果', IMPLEMENTATION_PLAN_APPROVED: '批准方案成果', REPORT_EFFECTIVE: '生效验收报告' }[type.resultType] || type.resultType)
const statusLabel = (status: string) => ({ ACCEPTED: '已满足', CONFIRMED: '已确认' }[status] || '待满足')
const sourceLabel = (sourceType: string) =>
  ({ UPLOAD: '文件提交', BUSINESS_DOCUMENT: '业务文档自动归集', BUSINESS_RESULT: '业务成果关联', AUTO_PROJECTION: '业务单据投影' }[sourceType] || sourceType)
const businessResultLine = (material: Api.MaterialLine) => {
  const segments = (material.businessObjectId || '').split('|')
  return { type: segments[0] || '', resultId: segments[2] || '' }
}
const uploaded = (selection: UploadedSelection) => { selectedFiles.value.push(selection); slotKey.value = generateUUID(); clearAttempt() }
const removeSelection = (key: string) => { selectedFiles.value = selectedFiles.value.filter(file => file.referenceKey !== key); clearAttempt() }
const canSubmit = computed(() => sourceType.value === 'UPLOAD' ? selectedFiles.value.length > 0 : resultIndex.value !== undefined)
const reasonLabel = (reason: string) => ({ DELIVERABLE_RULE_NOT_SATISFIED: '材料已保存，模板配置的业务条件尚未满足', DELIVERABLE_RULE_SATISFIED: '文件或业务成果有效，模板条件已满足', DELIVERABLE_SOURCE_MISSING: '尚未提交材料', FILE_EVIDENCE_UNAVAILABLE: '提交的文件已失效或引用已变化', DELIVERABLE_BUSINESS_RESULT_INVALID: '关联成果已失效或被替换', DELIVERABLE_QUANTITY_NOT_MET: '有效材料数量未达到模板要求', DELIVERABLE_SOURCE_REQUIRES_SUBMISSION: '请按模板规则关联业务成果或提交文件' }[reason] || `暂未满足：${reason}`)
const showOutcome = (result: { satisfied: boolean; reason: string }) => { satisfied.value = result.satisfied; outcome.value = reasonLabel(result.reason) }
const reload = async () => { if (detail.value) detail.value = await Api.getDetail(props.projectId, detail.value.id) }
const open = async (id: number) => {
  visible.value = true; loading.value = true; error.value = ''; outcome.value = ''; detail.value = undefined
  selectedFiles.value = []; candidates.value = []; resultIndex.value = undefined; typeIndex.value = undefined; clearAttempt()
  slotKey.value = generateUUID()
  try {
    detail.value = await Api.getDetail(props.projectId, id)
    sourceType.value = detail.value.configuration.allowedSources?.includes('UPLOAD') ? 'UPLOAD' : 'BUSINESS_RESULT'
    if (detail.value.configuration.allowedSources?.includes('BUSINESS_RESULT')) types.value = await Api.getTypes(props.projectId, id)
  } catch { error.value = '交付件加载失败，请关闭后重试' } finally { loading.value = false }
}
const loadCandidates = async (append: boolean) => {
  if (!detail.value || typeIndex.value === undefined) return
  clearAttempt(); resultIndex.value = undefined; candidatesLoading.value = true
  if (!append) { candidates.value = []; after.value = undefined }
  try {
    const page = await Api.getCandidates(props.projectId, detail.value.id, types.value[typeIndex.value].type, after.value)
    candidates.value.push(...page.observations.flatMap(item => item.result?.validity === 'CURRENT' ? [item.result] : []))
    after.value = page.nextCursor; candidatesComplete.value = page.complete
  } finally { candidatesLoading.value = false }
}
const submit = async () => {
  if (!detail.value || !canSubmit.value || uploadBusy.value) return
  busy.value = true
  try {
    const selected = resultIndex.value === undefined ? undefined : candidates.value[resultIndex.value]
    const data: Api.Submission = { planVersionId: detail.value.planVersionId, expectedVersion: detail.value.version, sourceType: sourceType.value,
      files: sourceType.value === 'UPLOAD' ? selectedFiles.value.map(file => ({ referenceId: file.referenceId })) : [],
      ...(sourceType.value === 'BUSINESS_RESULT' && selected ? { businessResult: { tenantId: selected.tenantId, projectId: selected.projectId, type: selected.type, objectId: selected.objectId, resultId: selected.resultId } } : {}) }
    const signature = JSON.stringify(data)
    if (!attempt || attempt.signature !== signature) attempt = { signature, key: generateUUID() }
    const result = await Api.submit(props.projectId, detail.value.id, data, attempt.key)
    showOutcome(result.evaluation); selectedFiles.value = []; resultIndex.value = undefined; clearAttempt()
    await reload(); emit('changed')
  } finally { busy.value = false }
}
const refresh = async () => {
  if (!detail.value) return
  busy.value = true
  try { showOutcome(await Api.evaluate(props.projectId, detail.value.id)); await reload(); emit('changed') } finally { busy.value = false }
}
const beforeClose = async (done: () => void) => {
  if (busy.value || uploadBusy.value) return
  if (selectedFiles.value.length || uploader.value?.hasPendingFile()) {
    try { await message.confirm('本次材料尚未提交，确定关闭？已上传文件会保留。') } catch { return }
  }
  done()
}
defineExpose({ open })
</script>
<style scoped>
.hint { color: var(--el-text-color-secondary); line-height: 1.6; }
.submission-form { margin-top: 16px; }
.selected-file { margin: 12px 0; }
.el-select { width: 100%; }
</style>
