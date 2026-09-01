<template>
  <section class="evidence-panel">
    <header><h3>单设备采集证据</h3><el-tag>{{ evidence.length }} 条</el-tag></header>
    <div class="evidence-actions">
      <el-button v-if="canRequest" v-hasPermi="['pms:cutover-task:request-collection']" data-testid="open-collection" @click="collectionVisible = true">请求采集</el-button>
      <el-button v-if="canLinkManual" v-hasPermi="['pms:cutover-task:save-closure']" data-testid="open-manual-result" @click="manualVisible = true">补录失败采集结果</el-button>
    </div>
    <el-table :data="evidence" row-key="evidenceId">
      <el-table-column prop="deviceId" label="设备ID" min-width="130" />
      <el-table-column prop="collectionStage" label="阶段" min-width="120" />
      <el-table-column prop="evidenceType" label="结果" min-width="150" />
      <el-table-column prop="collectionTaskId" label="采集任务" min-width="190" />
    </el-table>

    <el-dialog v-model="collectionVisible" title="请求单设备采集" width="min(560px, 94vw)">
      <el-radio-group v-model="collection.authenticationMode" data-testid="collection-auth-mode">
        <el-radio-button value="SAVED_CREDENTIAL">已保存凭据</el-radio-button>
        <el-radio-button value="TRANSIENT_CREDENTIAL">临时凭据</el-radio-button>
      </el-radio-group>
      <el-input v-model="collection.deviceId" data-testid="collection-device" placeholder="设备ID" />
      <el-select v-model="collection.collectionStage" data-testid="collection-stage">
        <el-option v-for="stage in stages" :key="stage" :label="stage" :value="stage" />
      </el-select>
      <template v-if="collection.authenticationMode === 'SAVED_CREDENTIAL'">
        <el-input v-model="collection.credentialId" data-testid="collection-credential-id" placeholder="凭据ID" />
        <el-input v-model="collection.credentialVersion" placeholder="凭据版本" />
      </template>
      <template v-else>
        <el-input v-model="collection.loginName" placeholder="登录名" />
        <el-input v-model="collection.transientSecret" data-testid="collection-secret" type="password" placeholder="临时Secret" />
        <el-checkbox v-model="collection.saveAsCredential">保存为凭据</el-checkbox>
      </template>
      <el-input v-model="collection.templateCode" placeholder="采集模板编码" />
      <el-input v-model="collection.templateVersion" placeholder="模板版本" />
      <template #footer><el-button data-testid="request-collection" type="primary" @click="submitCollection">发送采集请求</el-button></template>
    </el-dialog>

    <el-dialog v-model="manualVisible" title="补录失败采集结果" width="min(560px, 94vw)">
      <el-select v-model="manual.originalFailedCollectionTaskId" data-testid="manual-failed-task">
        <el-option v-for="row in failedEvidence" :key="row.collectionTaskId" :label="row.collectionTaskId" :value="row.collectionTaskId" />
      </el-select>
      <el-input v-model="manual.deviceId" placeholder="设备ID" />
      <el-select v-model="manual.collectionStage"><el-option v-for="stage in stages" :key="stage" :label="stage" :value="stage" /></el-select>
      <PmsFileUploader
        data-testid="manual-result-uploader"
        owner-context="CUT"
        object-type="CUTOVER_CLOSURE"
        :object-id="String(taskId)"
        purpose-code="MANUAL_COLLECTION_RESULT"
        :reference-key="`cutover-closure-${taskId}-manual-result`"
        category-code="CUTOVER_CLOSURE"
        @completed="completeManualUpload"
      />
      <small>{{ manual.file?.referenceKey || '未选择人工结果文件' }}</small>
      <template #footer><el-button data-testid="link-manual-result" type="primary" :disabled="!manual.file" @click="submitManual">关联人工结果</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file'
import type { CutoverClosureCollectionRequest, CutoverClosureEvidence, CutoverClosureFileFact, CutoverClosureStage, LinkCutoverClosureManualResultRequest, WireLong } from '@/api/pms/cutover/cutover-task'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'

const props = defineProps<{ taskId: WireLong; evidence: CutoverClosureEvidence[]; canRequest: boolean; canLinkManual: boolean }>()
const emit = defineEmits<{ request: [value: CutoverClosureCollectionRequest]; manual: [value: LinkCutoverClosureManualResultRequest] }>()
const stages: CutoverClosureStage[] = ['PRE_CHECK', 'EXECUTION', 'TEST', 'ROLLBACK', 'POST_COLLECTION']
const collectionVisible = ref(false)
const manualVisible = ref(false)
const collection = reactive({
  authenticationMode: 'SAVED_CREDENTIAL' as 'SAVED_CREDENTIAL' | 'TRANSIENT_CREDENTIAL',
  deviceId: '', collectionStage: 'PRE_CHECK' as CutoverClosureStage,
  credentialId: '', credentialVersion: '', loginName: '', transientSecret: '', saveAsCredential: false,
  templateCode: '', templateVersion: ''
})
const manual = reactive<{ originalFailedCollectionTaskId: string; deviceId: string; collectionStage: CutoverClosureStage; file: CutoverClosureFileFact | null }>({
  originalFailedCollectionTaskId: '', deviceId: '', collectionStage: 'PRE_CHECK', file: null
})
const failedEvidence = computed(() => props.evidence.filter((row) => row.evidenceType === 'DISPATCH_FAILED' || row.evidenceType === 'CALLBACK_FAILED'))
const submitCollection = () => {
  const common = { deviceId: collection.deviceId, collectionStage: collection.collectionStage, templateCode: collection.templateCode, templateVersion: collection.templateVersion }
  const value: CutoverClosureCollectionRequest = collection.authenticationMode === 'SAVED_CREDENTIAL'
    ? { authenticationMode: 'SAVED_CREDENTIAL', ...common, credentialId: collection.credentialId, credentialVersion: collection.credentialVersion }
    : { authenticationMode: 'TRANSIENT_CREDENTIAL', ...common, loginName: collection.loginName, transientSecret: collection.transientSecret, saveAsCredential: collection.saveAsCredential }
  emit('request', value)
  collection.transientSecret = ''
  collectionVisible.value = false
}
const fileKey = (referenceKey: string) => ({ ownerContext: 'CUT', objectType: 'CUTOVER_CLOSURE', objectId: String(props.taskId), purposeCode: 'MANUAL_COLLECTION_RESULT', referenceKey })
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
