<template>
  <section class="evidence-panel">
    <header><h3>单设备采集证据</h3><el-tag>{{ evidence.length }} 条</el-tag></header>
    <div class="evidence-actions">
      <el-button v-if="canRequest" v-hasPermi="['pms:cutover-task:request-collection']" data-testid="open-collection" @click="collectionVisible = true">请求采集</el-button>
      <el-button v-if="canLinkManual" v-hasPermi="['pms:cutover-task:save-closure']" data-testid="open-manual-result" @click="manualVisible = true">补录失败采集结果</el-button>
    </div>
    <el-table :data="evidence" row-key="evidenceId">
      <el-table-column label="设备" min-width="180"><template #default="{ row }">{{ deviceName(row.deviceId) }}</template></el-table-column>
      <el-table-column label="环节" min-width="120"><template #default="{ row }">{{ stageLabels[row.collectionStage] }}</template></el-table-column>
      <el-table-column label="结果" min-width="150"><template #default="{ row }">{{ evidenceLabels[row.evidenceType] || row.evidenceType }}</template></el-table-column>
      <el-table-column label="日志与附件" min-width="230"><template #default="{ row }">
        <PmsFileVersionReferenceList
          v-if="row.manualFile" owner-context="CUT" object-type="CUTOVER_CLOSURE" :object-id="String(closureId)"
          purpose-code="MANUAL_COLLECTION_RESULT" :reference-key="row.manualFile.referenceKey" :artifact-id="row.manualFile.artifactId" :version-no="row.manualFile.versionNo" />
        <el-button v-else link type="primary" @click="viewEvidence(row)">查看日志</el-button>
      </template></el-table-column>
    </el-table>

    <el-dialog v-model="collectionVisible" title="选择采集对象与环节" width="min(560px, 94vw)" append-to-body>
      <el-select v-model="collectionDeviceId" placeholder="选择割接设备"><el-option v-for="device in devices" :key="String(device.deviceId)" :label="device.serialNumber" :value="String(device.deviceId)" /></el-select>
      <el-select v-model="collectionStage"><el-option v-for="stage in stages" :key="stage" :label="stageLabels[stage]" :value="stage" /></el-select>
      <template #footer><el-button type="primary" :disabled="!collectionDeviceId" @click="openCollection">进入平台采集窗口</el-button></template>
    </el-dialog>
    <BusinessCollectionDialog ref="collectionDialog" :entry="collectionEntry" :device-id="collectionDeviceId" @closed="emit('refresh')" @observed="observeExecutions" />

    <el-dialog v-model="manualVisible" title="补录失败采集结果" width="min(560px, 94vw)" append-to-body>
      <el-select v-model="manual.originalFailedCollectionTaskId" data-testid="manual-failed-task">
        <el-option v-for="row in failedEvidence" :key="row.collectionTaskId" :label="`${deviceName(row.deviceId)} · ${stageLabels[row.collectionStage]}`" :value="row.collectionTaskId" />
      </el-select>
      <p v-if="selectedFailure">{{ deviceName(selectedFailure.deviceId) }} · {{ stageLabels[selectedFailure.collectionStage] }}</p>
      <PmsFileUploader
        data-testid="manual-result-uploader"
        owner-context="CUT"
        object-type="CUTOVER_CLOSURE"
        :object-id="String(closureId)"
        purpose-code="MANUAL_COLLECTION_RESULT"
        :reference-key="`cutover-closure-${closureId}-manual-result`"
        category-code="CUTOVER_CLOSURE"
        @completed="completeManualUpload"
      />
      <PmsFileVersionReferenceList
        v-if="manual.file" owner-context="CUT" object-type="CUTOVER_CLOSURE" :object-id="String(closureId)"
        purpose-code="MANUAL_COLLECTION_RESULT" :reference-key="manual.file.referenceKey" :artifact-id="manual.file.artifactId" :version-no="manual.file.versionNo" />
      <small v-else>未选择人工结果文件</small>
      <template #footer><el-button data-testid="link-manual-result" type="primary" :disabled="!manual.file || !selectedFailure" @click="submitManual">关联人工结果</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file/version-reference'
import type { DeviceScopeEntry, CutoverClosureEvidence, CutoverClosureFileFact, CutoverClosureStage, LinkCutoverClosureManualResultRequest, WireLong } from '@/api/pms/cutover'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'
import type { FileSelection } from '@/components/PmsFileArtifact'

import BusinessCollectionDialog from '@/components/DeviceCollection/BusinessCollectionDialog.vue'
import * as CutoverApi from '@/api/pms/cutover'
import type { Execution } from '@/api/pms/platform/deviceCollection'
import type { Entry } from '@/api/pms/platform/deviceCollection'
import { newIntentKey } from '../shared/cutoverTaskInteraction'
const props = defineProps<{ taskId: WireLong; closureId: WireLong; evidence: CutoverClosureEvidence[]; canRequest: boolean; canLinkManual: boolean; devices: DeviceScopeEntry[] }>()
const emit = defineEmits<{ refresh: []; manual: [value: LinkCutoverClosureManualResultRequest] }>()
const stages: CutoverClosureStage[] = ['PRE_CHECK', 'EXECUTION', 'TEST', 'ROLLBACK', 'POST_COLLECTION']
const collectionVisible = ref(false)
const manualVisible = ref(false)
const collectionDialog = ref<InstanceType<typeof BusinessCollectionDialog>>()
const collectionDeviceId = ref('')
const collectionStage = ref<CutoverClosureStage>('PRE_CHECK')
let linking = false
const linkedStates = new Map<string, string>()
const pendingLinks = new Map<string, Parameters<typeof CutoverApi.linkClosureExecution>[0]>()
const message = useMessage()
const observeExecutions = async (executions: Execution[]) => {
  if (!props.canRequest || linking) return
  linking = true
  const entry = collectionEntry.value
  try {
    for (const execution of executions) {
      const key = `${entry}:${execution.id}`
      const signature = `${execution.task.status}:${execution.task.resultVersion ?? ''}:${execution.task.fileVersionId ?? ''}`
      if (linkedStates.get(key) === signature) continue
      const intent = `${key}:${signature}`
      let command = pendingLinks.get(intent)
      if (!command) {
        const current = await CutoverApi.getCutoverClosure(props.taskId)
        if (current.closureVersion === null || current.closureVersion === undefined) continue
        command = { taskId: props.taskId, taskVersion: current.taskVersion, closureVersion: current.closureVersion,
          entry, executionId: execution.id, idempotencyKey: newIntentKey() }
        pendingLinks.set(intent, command)
      }
      try { await CutoverApi.linkClosureExecution(command) }
      catch (error) {
        const recovery = (error as any)?.response?.data?.data?.recoveryAction
        if (recovery === 'REFRESH_AGGREGATE' || recovery === 'START_NEW_INTENT') pendingLinks.delete(intent)
        throw error
      }
      pendingLinks.delete(intent)
      linkedStates.set(key, signature)
    }
    emit('refresh')
  } catch { message.warning('采集执行已记录，闭环关联未完成，请刷新采集窗口后重试') } finally { linking = false }
}
const stageLabels: Record<string, string> = { PRE_CHECK: '割接前检查', EXECUTION: '割接执行', TEST: '业务测试', ROLLBACK: '回退', POST_COLLECTION: '割接后采集' }
const evidenceLabels: Record<string, string> = { DISPATCH_ACCEPTED: '已下发', DISPATCH_FAILED: '下发失败', CALLBACK_SUCCEEDED: '日志已回传', CALLBACK_FAILED: '执行失败', MANUAL_UPLOAD: '人工补录' }
const stageEntries: Record<CutoverClosureStage, Entry> = { PRE_CHECK: 'cutover-pre-check', EXECUTION: 'cutover-execution', TEST: 'cutover-test', ROLLBACK: 'cutover-rollback', POST_COLLECTION: 'cutover-post-collection' }
const collectionEntry = computed(() => stageEntries[collectionStage.value])
const deviceName = (id: WireLong) => props.devices.find(d => String(d.deviceId) === String(id))?.serialNumber || '历史设备'
const openCollection = async () => { collectionVisible.value = false; await nextTick(); await collectionDialog.value?.open(props.closureId) }
const viewEvidence = async (row: CutoverClosureEvidence) => { collectionDeviceId.value = String(row.deviceId); collectionStage.value = row.collectionStage; await nextTick(); await collectionDialog.value?.open(props.closureId) }
const manual = reactive<{ originalFailedCollectionTaskId: string; deviceId: string; collectionStage: CutoverClosureStage; file: CutoverClosureFileFact | null }>({
  originalFailedCollectionTaskId: '', deviceId: '', collectionStage: 'PRE_CHECK', file: null
})
const failedEvidence = computed(() => props.evidence.filter((row) => row.evidenceType === 'DISPATCH_FAILED' || row.evidenceType === 'CALLBACK_FAILED'))
const selectedFailure = computed(() => failedEvidence.value.find(e => String(e.collectionTaskId) === String(manual.originalFailedCollectionTaskId)))
watch(selectedFailure, row => { if (row) { manual.deviceId = String(row.deviceId); manual.collectionStage = row.collectionStage } })
const fileKey = (referenceKey: string) => ({ ownerContext: 'CUT', objectType: 'CUTOVER_CLOSURE', objectId: String(props.closureId), purposeCode: 'MANUAL_COLLECTION_RESULT', referenceKey })
const completeManualUpload = async (selection: FileSelection) => {
  const key = fileKey(selection.referenceKey)
  const artifact = await FileApi.getArtifact(selection.artifactId, key)
  const versions = await FileApi.getVersions(selection.artifactId, { ...key, pageSize: 20 })
  const version = versions.items.find((row) => row.versionNo === selection.versionNo)
  if (!version) throw new Error('PLT 未返回人工采集结果版本')
  manual.file = {
    purposeCode: 'MANUAL_COLLECTION_RESULT', artifactId: selection.artifactId, versionNo: selection.versionNo,
    referenceKey: selection.referenceKey, scopeVersion: artifact.reference.scopeVersion, sha256: version.sha256,
    fileFactVersion: { artifactVersion: artifact.artifactVersion, referenceVersion: artifact.reference.referenceVersion, availabilityVersion: version.availabilityVersion }
  }
}
const submitManual = () => {
  if (!manual.file) return
  emit('manual', { originalFailedCollectionTaskId: manual.originalFailedCollectionTaskId, deviceId: manual.deviceId, collectionStage: manual.collectionStage, file: manual.file })
  manualVisible.value = false
}
</script>

<style scoped>
.evidence-panel { margin-top: 18px; }
.evidence-panel header, .evidence-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.evidence-actions { justify-content: flex-start; margin-bottom: 12px; flex-wrap: wrap; }
:deep(.el-dialog__body) { display: grid; gap: 12px; }
</style>
