<template>
  <article class="checklist-field" :class="{ required: item.required, 'is-table-row': tableRow, 'is-embedded': embedded }">
    <header v-if="!tableRow && !embedded">
      <div>
        <h4>{{ item.itemName }}<span v-if="item.required" aria-label="必填"> *</span></h4>
        <p v-if="item.itemDescription">{{ item.itemDescription }}</p>
      </div>
      <div class="field-tags">
        <el-tag v-if="item.sourceCode === 'CUSTOM'" size="small" effect="plain">自定义项</el-tag>
        <el-tag v-if="item.currentResult" size="small" type="success">
          {{ resultLabel }}
        </el-tag>
        <el-button
          v-if="!readonly && allowSave && item.sourceCode === 'CUSTOM' && !item.currentResult"
          data-testid="remove-custom-item"
          link
          type="danger"
          @click="emit('remove', item.stableItemKey)"
        >移出</el-button>
      </div>
    </header>
    <div v-if="tableRow && (item.sourceCode === 'CUSTOM' || item.currentResult)" class="result-toolbar"><small class="cut-muted"><template v-if="item.sourceCode === 'CUSTOM'">自定义项<template v-if="item.currentResult">，</template></template><template v-if="item.currentResult">{{ resultLabel }}</template></small><el-button v-if="!readonly && allowSave && item.sourceCode === 'CUSTOM' && !item.currentResult" data-testid="remove-custom-item" link type="danger" @click="emit('remove', item.stableItemKey)">删除自定义项</el-button></div>

    <div v-if="item.workModeCode === 'COLLECTION'" class="collection-area">
      <el-select v-model="selectedDeviceId" placeholder="选择任务设备" :disabled="readonly || !allowCollection">
        <el-option v-for="device in devices" :key="String(device.deviceId)" :label="device.serialNumber" :value="String(device.deviceId)" />
      </el-select>
      <el-button :disabled="!selectedDeviceId" @click="collectionDialog?.open(item.itemId)">{{ readonly ? '查看采集日志' : '设备采集与日志' }}</el-button>
      <el-button v-if="!readonly && allowCollection" @click="loadExecutions">选择已有采集记录</el-button>
      <Teleport to="body">
        <BusinessCollectionDialog ref="collectionDialog" entry="cutover-checklist" :device-id="selectedDeviceId" align-center @accepted="acceptExecution" @closed="emit('refresh')" />
      </Teleport>
      <el-dialog v-model="executionsVisible" title="选择本项采集记录" width="min(900px, 94vw)" append-to-body align-center>
        <el-table :data="executions" v-loading="executionsLoading">
          <el-table-column label="设备" prop="task.deviceName" min-width="160" />
          <el-table-column label="命令模板" prop="templateName" min-width="180" />
          <el-table-column label="发起时间" prop="createdAt" min-width="170" />
          <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ executionStatus(row.task.status) }}</template></el-table-column>
          <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click="selectExecution(row)">选用</el-button></template></el-table-column>
        </el-table>
        <Pagination v-model:page="executionsPage" :limit="10" :total="executionsTotal" @pagination="loadExecutions" />
      </el-dialog>
    </div>
    <CutoverExternalBulletinField
      v-if="item.workModeCode === 'EXTERNAL'" :state="externalState || null" :model-value="externalFeedback || []"
      :readonly="readonly || !allowSave" :busy="externalBusy" :version-dirty="versionDirty" :device-names="externalDeviceNames"
      @update:model-value="emit('externalFeedback', item.stableItemKey, $event)" @load="emit('externalLoad', item.stableItemKey)" @save="emit('externalSave', item.stableItemKey)" />
    <CutoverSchemaInput v-if="item.workModeCode === 'DIRECT' || collectionReady" :schema="schema" :model-value="structuredValue" :disabled="readonly || !allowSave" :background-layout="embedded" @update:model-value="updateStructured" />
    <p v-if="item.currentResult?.factDescription" class="fact-description">{{ item.currentResult.factDescription }}</p>
    <PmsFileVersionReferenceList v-if="manualFile" owner-context="CUT" object-type="CUTOVER_CHECKLIST_ITEM" :object-id="String(item.itemId)" purpose-code="MANUAL_EVIDENCE" :reference-key="manualFile.referenceKey" :artifact-id="manualFile.artifactId" :version-no="manualFile.versionNo" />

    <details v-if="!readonly && allowSave && canUseManual" class="manual-area">
      <summary>{{ item.workModeCode === 'MANUAL' ? '上传本项材料' : '改用人工核验证据' }}</summary>
      <el-input v-model="factDescription" placeholder="材料说明（选填）" />
      <PmsFileUploader
        data-testid="manual-uploader"
        owner-context="CUT"
        object-type="CUTOVER_CHECKLIST_ITEM"
        :object-id="String(item.itemId)"
        purpose-code="MANUAL_EVIDENCE"
        :reference-key="`cutover-checklist-${item.stableItemKey}`"
        category-code="CUTOVER_CHECKLIST_MANUAL_EVIDENCE"
        @completed="completeUpload"
      />
      <p class="cut-help">支持PDF及图片，单文件不超过50MB。</p>
    </details>
  </article>
</template>

<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file/version-reference'
import type {
  ChecklistFileHandle,
  CutoverChecklistItem,
  CutoverExternalItemState,
  CutoverExternalFeedback,
  DeviceScopeEntry
} from '@/api/pms/cutover'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'
import type { FileSelection } from '@/components/PmsFileArtifact'

import CutoverSchemaInput from './CutoverSchemaInput.vue'
import CutoverExternalBulletinField from './CutoverExternalBulletinField.vue'
import BusinessCollectionDialog from '@/components/DeviceCollection/BusinessCollectionDialog.vue'
import * as CollectionApi from '@/api/pms/platform/deviceCollection'

const props = withDefaults(defineProps<{
  item: CutoverChecklistItem
  directValue: string
  readonly: boolean
  allowSave?: boolean
  allowCollection?: boolean
  tableRow?: boolean
  embedded?: boolean
  devices?: DeviceScopeEntry[]
  externalState?: CutoverExternalItemState | null
  externalFeedback?: CutoverExternalFeedback[]
  externalBusy?: boolean
  versionDirty?: boolean
}>(), { allowSave: false, allowCollection: false, devices: () => [] })
const emit = defineEmits<{
  direct: [stableItemKey: string, value: string]
  manual: [stableItemKey: string, file: ChecklistFileHandle, factDescription: string]
  collection: [stableItemKey: string, deviceId: string, commandTemplateId: string | null, executionId: string]
  refresh: []
  remove: [stableItemKey: string]
  externalLoad: [stableItemKey: string]
  externalSave: [stableItemKey: string]
  externalFeedback: [stableItemKey: string, value: CutoverExternalFeedback[]]
}>()
const canUseManual = computed(() => {
  if (props.item.workModeCode === 'MANUAL') return true
  if (props.item.workModeCode === 'COLLECTION') {
    const result = props.item.currentResult
    return !!result && ((result.resultSourceCode === 'COLLECTION' && !!result.loadFailureCode)
      || (result.resultSourceCode === 'MANUAL' && !!result.originalFailureCode))
  }
  return props.item.workModeCode === 'EXTERNAL' && !props.versionDirty
    && props.externalState?.manualFallback && props.externalState.contextCurrent
    && props.externalState.queries.some(query => query.succeeded === false
      && props.externalState?.selectedQueryIds.some(id => String(id) === String(query.id)))
})
const externalDeviceNames = computed(() => Object.fromEntries(props.devices.map(device => [String(device.deviceId), device.serialNumber])))
const factDescription = ref('')
const selectedDeviceId = ref('')
const collectionDialog = ref<InstanceType<typeof BusinessCollectionDialog>>()
const executionsVisible = ref(false)
const executionsLoading = ref(false)
const executions = ref<CollectionApi.Execution[]>([])
const executionsPage = ref(1)
const executionsTotal = ref(0)
const executionStatus = (status: string) => ({ CREATED: '已创建', AUTHORIZED: '已授权', RUNNING: '采集中', RESULT_AVAILABLE: '结果已就绪', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[status] || '处理中')
const loadExecutions = async () => {
  executionsVisible.value = true
  executionsLoading.value = true
  try { const data = await CollectionApi.page('cutover-checklist', props.item.itemId, executionsPage.value); executions.value = data.list; executionsTotal.value = data.total }
  finally { executionsLoading.value = false }
}
const selectExecution = (execution: CollectionApi.Execution) => {
  if (props.readonly || !props.allowCollection) return
  emit('collection', props.item.stableItemKey, execution.task.deviceId,
    execution.task.templateId === `manual-${props.item.itemId}` ? null : execution.task.templateId, String(execution.id))
  executionsVisible.value = false
}
const acceptExecution = (execution: CollectionApi.Execution) => selectExecution(execution)
const schema = computed(() => { try { return JSON.parse(props.item.interfaceSchemaSnapshot || '{}') } catch { return {} } })
const structuredValue = computed(() => { if (!props.directValue) return null; try { return JSON.parse(props.directValue) } catch { return props.directValue } })
const updateStructured = (value: unknown) => emit('direct', props.item.stableItemKey, JSON.stringify(value ?? null))
const collectionReady = computed(() => props.item.currentResult?.resultSourceCode === 'COLLECTION' && !!props.item.currentResult.collectionResultReferenceId && !props.item.currentResult.loadFailureCode)
const manualFile = computed(() => {
  if (props.item.currentResult?.resultSourceCode !== 'MANUAL') return null
  try { const snapshot = JSON.parse(props.item.currentResult.answerSnapshot); return snapshot.file || snapshot } catch { return null }
})

const storedCollection = computed(() => {
  if (props.item.currentResult?.resultSourceCode !== 'COLLECTION') return null
  try {
    return JSON.parse(props.item.currentResult.answerSnapshot) as {
      deviceId?: string | number
      commandTemplateId?: string | number
      technicalStatus?: string
    }
  } catch {
    return null
  }
})
const collectionPending = computed(() => ['ACCEPTED', 'RUNNING']
  .includes(storedCollection.value?.technicalStatus || ''))

watch([() => props.item.currentResult?.answerSnapshot, () => props.devices], () => {
  if (collectionPending.value && storedCollection.value?.deviceId && storedCollection.value?.commandTemplateId) {
    selectedDeviceId.value = String(storedCollection.value.deviceId)
    return
  }
  if (!selectedDeviceId.value && props.devices[0]) {
    selectedDeviceId.value = String(props.devices[0].deviceId)
  }
}, { immediate: true, deep: true })

const resultLabel = computed(() =>
  props.item.currentResult?.resultSourceCode === 'MANUAL'
    ? '人工证据'
    : props.item.currentResult?.resultSourceCode === 'COLLECTION'
      ? props.item.currentResult.loadFailureCode
        ? `采集失败：${props.item.currentResult.loadFailureCode}`
        : props.item.currentResult.collectionResultReferenceId
          ? '日志已就绪，需填写核查结论'
          : '采集中'
      : props.item.currentResult?.resultSourceCode === 'EXTERNAL'
        ? !props.externalState?.contextCurrent ? '查询条件已变化'
          : props.item.currentResult.loadFailureCode ? '外部加载失败'
            : props.externalState.feedbackSaved ? '已保存核查反馈' : '已加载，待核查反馈'
        : '已填写'
)
const fileKey = (referenceKey: string) => ({
  ownerContext: 'CUT',
  objectType: 'CUTOVER_CHECKLIST_ITEM',
  objectId: String(props.item.itemId),
  purposeCode: 'MANUAL_EVIDENCE',
  referenceKey
})
const completeUpload = async (selection: FileSelection) => {
  const [artifact, versions] = await Promise.all([
    FileApi.getArtifact(selection.artifactId, fileKey(selection.referenceKey)),
    FileApi.getVersions(selection.artifactId, { ...fileKey(selection.referenceKey), pageSize: 20 })
  ])
  const version = versions.items.find((entry) => entry.versionNo === selection.versionNo)
  if (!version) throw new Error('PLT 未返回刚完成的文件版本')
  emit('manual', props.item.stableItemKey, {
    artifactId: selection.artifactId,
    versionNo: selection.versionNo,
    referenceKey: selection.referenceKey,
    scopeVersion: artifact.reference.scopeVersion,
    fileFactVersion: {
      artifactVersion: artifact.artifactVersion,
      referenceVersion: artifact.reference.referenceVersion,
      availabilityVersion: version.availabilityVersion
    }
  }, factDescription.value)
}
</script>

<style scoped>
.checklist-field.is-table-row, .checklist-field.is-embedded { margin: 0; padding: 0; border: 0; border-radius: 0; }
.result-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 6px; }
.checklist-field { padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.checklist-field + .checklist-field { margin-top: 12px; }
.checklist-field header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.checklist-field h4, .checklist-field p { margin: 0; }
.checklist-field h4 span { color: var(--el-color-danger); }
.checklist-field p { margin-top: 4px; color: var(--el-text-color-secondary); }
.field-tags { display: flex; align-items: flex-start; gap: 6px; }
.fact-description { white-space:pre-wrap; }
.manual-area { margin-top: 14px; }
.collection-area { display: grid; grid-template-columns: minmax(180px, 1fr) auto auto; gap: 10px; }
.manual-area summary { margin-bottom: 10px; cursor: pointer; color: var(--el-color-primary); }
.manual-area :deep(.pms-file-uploader) { margin-top: 10px; }
@media (max-width: 767px) {
  .checklist-field header { flex-direction: column; }
  .collection-area { grid-template-columns: 1fr; }
}
</style>
