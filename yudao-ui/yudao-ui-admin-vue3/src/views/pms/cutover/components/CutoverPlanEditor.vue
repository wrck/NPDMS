<template>
  <section class="plan-editor">
    <template v-if="modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD'">
      <h4 class="cut-section-title">一、基本信息部分</h4>
      <el-form label-position="top"><el-form-item label="项目与割接概述"><el-input data-testid="plan-project-description" :model-value="modelValue.overview.projectDescription" :disabled="!editable" type="textarea" :rows="3" @update:model-value="updateOverview('projectDescription', $event)" /></el-form-item></el-form>
      <h4 class="cut-section-title">项目割接计划</h4>
      <el-table :data="modelValue.overview.scheduleTable" empty-text="暂无计划项">
        <el-table-column label="日期" width="250"><template #default="{ row, $index }"><el-date-picker :model-value="row.plannedAt" :disabled="!editable" type="datetime" value-format="x" @update:model-value="updateSchedule($index, 'plannedAt', $event)" /></template></el-table-column>
        <el-table-column label="工作内容" min-width="300"><template #default="{ row, $index }"><el-input :model-value="row.content" :disabled="!editable" @update:model-value="updateSchedule($index, 'content', $event)" /></template></el-table-column>
        <el-table-column v-if="editable" label="操作" width="76"><template #default="{ $index }"><el-button link type="danger" @click="removeSchedule($index)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button v-if="editable" class="table-add" @click="addSchedule"><Icon icon="ep:plus" />增加计划项</el-button>
      <h4 class="cut-section-title">网络拓扑与组网配置</h4>
      <div class="standard-file-grid">
        <div v-for="slot in standardFileSlots" :key="slot.field" class="file-slot">
          <label>{{ slot.label }}</label>
          <PmsFileUploader v-if="editable" ref="standardUploadRefs" :data-testid="'plan-file-' + slot.field" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
            purpose-code="FULL_PLAN" :reference-key="modelValue.overview[slot.field]?.referenceKey || 'cutover-plan-' + taskId + '-' + slot.referenceSuffix"
            :artifact-id="modelValue.overview[slot.field]?.artifactId as number | undefined"
            :expected-reference-version="modelValue.overview[slot.field]?.fileFactVersion.referenceVersion"
            category-code="CUTOVER_PLAN" @completed="completeStandardUpload(slot.field, $event)" />
          <p v-if="editable" class="cut-help">支持PDF及图片，单文件不超过50MB。</p>
          <PmsFileVersionReferenceList v-if="modelValue.overview[slot.field]" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
            purpose-code="FULL_PLAN" :reference-key="modelValue.overview[slot.field]!.referenceKey" :artifact-id="modelValue.overview[slot.field]!.artifactId" :version-no="modelValue.overview[slot.field]!.versionNo" />
          <p v-else-if="!editable" class="cut-help">未上传</p>
        </div>
      </div>
      <h4 class="cut-section-title">割接设备清单</h4>
      <el-table :data="modelValue.overview.deviceSummary" empty-text="当前方案未记录设备">
        <el-table-column v-for="column in deviceColumns" :key="column.field" :label="column.label" min-width="200">
          <template #default="{ row }"><el-input :data-testid="'plan-device-' + row.deviceId + '-' + column.field"
            :model-value="deviceDetail(row.deviceId)?.[column.field] || ''" :disabled="!editable"
            @update:model-value="updateDeviceDetail({ deviceId: row.deviceId, field: column.field, value: $event })" />
            <small v-if="column.field === 'productModel'" class="cut-muted">{{ row.serialNumber }}</small>
          </template>
        </el-table-column>
      </el-table>
      <h4 class="cut-section-title">割接保障人员安排</h4>
      <CutoverSupportArrangements :model-value="modelValue.supportArrangements" :readonly="!editable" :patch-approved="patchApproved"
        :contacts="contacts" @update:model-value="updateSupport" @patch="$emit('patch-support', $event)" />
      <h4 class="cut-section-title">二、执行操作部分</h4>
      <section v-if="sourceSnapshot?.failedRiskFacts.length">
        <h4 class="cut-section-title">预估风险及应对措施</h4>
        <p class="cut-help">调研及风险考察中未通过的检查项带入本表。</p>
        <el-table :data="sourceSnapshot.failedRiskFacts">
          <el-table-column label="风险项" min-width="280"><template #default="{ row }"><strong>{{ row.itemName }}</strong><p class="risk-detail">{{ row.factDescription }}</p></template></el-table-column>
          <el-table-column label="应对措施" min-width="360"><template #default="{ row }"><el-input :model-value="mitigation(row.stableItemKey)" :disabled="!editable" type="textarea" :rows="2" @update:model-value="updateMitigation(row, $event)" /></template></el-table-column>
        </el-table>
      </section>
      <section v-for="section in standardSections" :key="section.code" class="editor-section">
        <template v-if="section.code === 'ROLLBACK'">
          <h4 class="cut-section-title">回退方案说明</h4>
          <el-input data-testid="plan-rollback-description" :model-value="modelValue.rollbackDescription || ''" :disabled="!editable"
            type="textarea" :rows="3" @update:model-value="updateRollbackDescription" />
        </template>
        <h4 class="cut-section-title">{{ section.label }}</h4>
        <el-table :data="stepsFor(section.code)" :empty-text="'暂无' + section.label">
          <el-table-column prop="stepNo" label="序号" width="68" />
          <el-table-column v-for="column in tableColumns[section.code]" :key="column.field" :label="column.label" :min-width="column.width">
            <template #default="{ row }">
              <el-input :data-testid="'plan-step-' + section.code + '-' + row.stepNo + '-' + column.field"
                :model-value="column.field === 'content' ? row.content : row.details?.[column.field] || ''" :disabled="!editable"
                type="textarea" :autosize="{ minRows: 1, maxRows: 6 }"
                @update:model-value="updateTableStep({ row, field: column.field, value: $event })" />
            </template>
          </el-table-column>
          <el-table-column v-if="editable" label="操作" width="76"><template #default="{ row }"><el-button link type="danger" @click="removeStep(section.code, row.stepNo)">删除</el-button></template></el-table-column>
        </el-table>
        <el-button v-if="editable" class="table-add" @click="addStep(section.code)"><Icon icon="ep:plus" />增加步骤</el-button>
      </section>
    </template>
    <template v-else-if="modelValue.editMode === 'ONLINE_TEMPLATE_SIMPLE_D'">
      <el-alert title="D级简易割接仅填写操作步骤及回退步骤。" type="warning" :closable="false" show-icon />
      <section v-for="section in simpleSections" :key="section.code" class="editor-section">
        <h4 class="cut-section-title">{{ section.label }}</h4>
        <el-table :data="stepsFor(section.code)" empty-text="暂无步骤">
          <el-table-column prop="stepNo" label="序号" width="68" />
          <el-table-column label="步骤与操作内容" min-width="460"><template #default="{ row }"><el-input :data-testid="'plan-step-' + section.code + '-' + row.stepNo" :model-value="row.content" :disabled="!editable" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" @update:model-value="updateStep(section.code, row.stepNo, $event)" /></template></el-table-column>
          <el-table-column v-if="editable" label="操作" width="76"><template #default="{ row }"><el-button link type="danger" @click="removeStep(section.code, row.stepNo)">删除</el-button></template></el-table-column>
        </el-table>
        <el-button v-if="editable" class="table-add" @click="addStep(section.code)"><Icon icon="ep:plus" />增加步骤</el-button>
      </section>
    </template>
    <template v-else-if="modelValue.editMode === 'FULL_FILE_UPLOAD'">
      <h4 class="cut-section-title">已有割接方案</h4>
      <PmsFileUploader v-if="editable" ref="uploadedPlanRef" data-testid="plan-file-uploader" accept=".doc,.docx,.pdf" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)" purpose-code="FULL_PLAN"
        :reference-key="modelValue.fileArtifactFact?.referenceKey || 'cutover-plan-' + taskId"
        :artifact-id="modelValue.fileArtifactFact?.artifactId as number | undefined"
        :expected-reference-version="modelValue.fileArtifactFact?.fileFactVersion.referenceVersion"
        category-code="CUTOVER_PLAN" @completed="completeUpload" />
      <p v-if="editable" class="cut-help">支持Word、PDF，单文件不超过50MB。</p>
      <PmsFileVersionReferenceList v-if="modelValue.fileArtifactFact" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)" purpose-code="FULL_PLAN"
        :reference-key="modelValue.fileArtifactFact.referenceKey" :artifact-id="modelValue.fileArtifactFact.artifactId" :version-no="modelValue.fileArtifactFact.versionNo" />
      <el-checkbox data-testid="plan-ownership-confirmed" :model-value="modelValue.ownershipConfirmed" :disabled="!editable" @update:model-value="updateOwnership">确认本人有权使用并提交该完整方案文件</el-checkbox>
    </template>
  </section>
</template>
<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file/version-reference'
import type {
  CutoverPlanRiskFact,
  CutoverPlanDeviceDetails,
  CutoverPlanSectionCode,
  CutoverPlanSourceSnapshot,
  CutoverPlanStep,
  CutoverPlanStepDetails,
  StandardCutoverPlanContent,
  WireDateTime,
  WireLong,
  WritableCutoverPlanContent
} from '@/api/pms/cutover'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'
import type { FileSelection } from '@/components/PmsFileArtifact'
import CutoverSupportArrangements from './CutoverSupportArrangements.vue'

// PLT 上传器的 ID 类型声明较窄；模板断言仅兼容声明，原样传递 Snowflake 字符串，不能转换为 Number。

const props = defineProps<{
  modelValue: WritableCutoverPlanContent
  sourceSnapshot: CutoverPlanSourceSnapshot | null
  taskId: string | number
  editable: boolean
  patchApproved?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: WritableCutoverPlanContent]
  'patch-support': [value: StandardCutoverPlanContent['supportArrangements'][number]]
  'upload-pending': [pending: boolean]
}>()
const standardUploadRefs = ref<Array<InstanceType<typeof PmsFileUploader>>>([])
const uploadedPlanRef = ref<InstanceType<typeof PmsFileUploader>>()
const fileFactReads = ref(0)
const pendingUploads = computed(() => fileFactReads.value > 0
  || standardUploadRefs.value.some(uploader => uploader.isBusy() || uploader.hasPendingFile())
  || !!uploadedPlanRef.value && (uploadedPlanRef.value.isBusy() || uploadedPlanRef.value.hasPendingFile()))
watch(pendingUploads, pending => emit('upload-pending', pending), { immediate: true })

const contacts = ref<import('@/api/pms/cutover').ProjectContactChoice[]>([])
watch([() => props.taskId, () => props.editable, () => props.patchApproved], async () => {
  if (props.editable || props.patchApproved) contacts.value = await import('@/api/pms/cutover').then(api => api.getProjectContactChoices(props.taskId))
}, { immediate: true })

const standardSections: Array<{ code: CutoverPlanSectionCode; label: string }> = [
  { code: 'PRE_OPERATION', label: '割接前操作清单' },
  { code: 'OPERATION', label: '割接执行操作清单' },
  { code: 'CLOSING_COLLECTION', label: '割接收尾收集清单' },
  { code: 'POST_BUSINESS_TEST', label: '割接后业务测试表' },
  { code: 'ROLLBACK', label: '回退方案操作步骤' },
  { code: 'POST_CUTOVER_SUPPORT', label: '割接后保障' }
]
const simpleSections = standardSections.filter((section) => ['OPERATION', 'ROLLBACK'].includes(section.code))
type TableStepField = 'content' | keyof CutoverPlanStepDetails
const tableColumns: Record<CutoverPlanSectionCode, Array<{ field: TableStepField; label: string; width: number }>> = {
  PRE_OPERATION: [
    { field: 'content', label: '操作项目', width: 280 },
    { field: 'testResult', label: '测试结果', width: 220 },
    { field: 'operator', label: '责任人', width: 140 }
  ],
  OPERATION: [
    { field: 'stepName', label: '割接步骤', width: 160 },
    { field: 'content', label: '操作内容', width: 280 },
    { field: 'businessImpactTime', label: '业务影响时间', width: 160 },
    { field: 'operator', label: '实施人员', width: 140 },
    { field: 'operationTime', label: '操作时间', width: 140 }
  ],
  CLOSING_COLLECTION: [
    { field: 'content', label: '操作项目', width: 280 },
    { field: 'testResult', label: '测试结果', width: 220 },
    { field: 'operator', label: '责任人', width: 140 }
  ],
  POST_BUSINESS_TEST: [
    { field: 'content', label: '操作项目', width: 280 },
    { field: 'testResult', label: '测试结果', width: 220 },
    { field: 'operator', label: '责任人', width: 140 }
  ],
  ROLLBACK: [
    { field: 'stepName', label: '回退步骤', width: 180 },
    { field: 'content', label: '操作内容', width: 320 },
    { field: 'operator', label: '实施人员', width: 140 },
    { field: 'operationTime', label: '操作时间', width: 140 }
  ],
  POST_CUTOVER_SUPPORT: [
    { field: 'operator', label: '责任人', width: 180 },
    { field: 'content', label: '时间', width: 400 }
  ]
}
const standardFileSlots = [
  { field: 'preTopologyFile', label: '割接前拓扑', referenceSuffix: 'pre-topology' },
  { field: 'postTopologyFile', label: '割接后拓扑', referenceSuffix: 'post-topology' },
  { field: 'networkConfigurationFile', label: '组网配置', referenceSuffix: 'network-configuration' }
] as const
const deviceColumns = [
  { field: 'productModel', label: '涉及产品型号' },
  { field: 'currentVersion', label: '在网软件版本' },
  { field: 'targetVersion', label: '升级版本信息' },
  { field: 'remark', label: '备注' }
] as const
const deviceDetail = (deviceId: WireLong) => props.modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD'
  ? props.modelValue.overview.deviceDetails?.find(row => String(row.deviceId) === String(deviceId)) : undefined
const updateDeviceDetail = (change: { deviceId: WireLong; field: Exclude<keyof CutoverPlanDeviceDetails, 'deviceId'>; value: string }) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  const deviceDetails = props.modelValue.overview.deviceSummary.map(device => {
    const existing = deviceDetail(device.deviceId) || { deviceId: device.deviceId, productModel: '', currentVersion: '', targetVersion: '', remark: '' }
    return String(device.deviceId) === String(change.deviceId) ? { ...existing, [change.field]: change.value } : existing
  })
  update({ ...props.modelValue, overview: { ...props.modelValue.overview, deviceDetails } })
}

const update = (value: WritableCutoverPlanContent) => emit('update:modelValue', value)
const updateOverview = (field: 'projectDescription', value: string) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  update({ ...props.modelValue, overview: { ...props.modelValue.overview, [field]: value } })
}
const updateRollbackDescription = (rollbackDescription: string) => {
  if (props.modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD') update({ ...props.modelValue, rollbackDescription })
}
const addSchedule = () => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  const scheduleTable = [...props.modelValue.overview.scheduleTable, {
    sequenceNo: props.modelValue.overview.scheduleTable.length + 1,
    plannedAt: Date.now() as WireDateTime,
    content: ''
  }]
  update({ ...props.modelValue, overview: { ...props.modelValue.overview, scheduleTable } })
}
const removeSchedule = (index: number) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  const scheduleTable = props.modelValue.overview.scheduleTable.filter((_, rowIndex) => rowIndex !== index)
    .map((row, rowIndex) => ({ ...row, sequenceNo: rowIndex + 1 }))
  update({ ...props.modelValue, overview: { ...props.modelValue.overview, scheduleTable } })
}
const updateSchedule = (index: number, field: 'plannedAt' | 'content', value: string | number) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  const normalized = field === 'plannedAt' ? Number(value) : value
  if (field === 'plannedAt' && (!Number.isSafeInteger(normalized) || Number(normalized) <= 0)) return
  const scheduleTable = props.modelValue.overview.scheduleTable.map((row, rowIndex) => rowIndex === index
    ? { ...row, [field]: normalized }
    : row)
  update({ ...props.modelValue, overview: { ...props.modelValue.overview, scheduleTable } })
}
const stepsFor = (sectionCode: CutoverPlanSectionCode) => props.modelValue.editMode === 'FULL_FILE_UPLOAD'
  ? []
  : props.modelValue.steps.filter((row) => row.sectionCode === sectionCode).sort((left, right) => left.stepNo - right.stepNo)
const updateStep = (sectionCode: CutoverPlanSectionCode, stepNo: number, content: string) => {
  if (props.modelValue.editMode === 'FULL_FILE_UPLOAD') return
  const steps = props.modelValue.steps.map((row) => row.sectionCode === sectionCode && row.stepNo === stepNo
    ? { ...row, content }
    : row)
  update({ ...props.modelValue, steps })
}
const updateTableStep = (change: { row: CutoverPlanStep; field: TableStepField; value: string }) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  const details = { stepName: '', testResult: '', businessImpactTime: '', operator: '', operationTime: '', ...change.row.details }
  const steps = props.modelValue.steps.map(row => row.sectionCode === change.row.sectionCode && row.stepNo === change.row.stepNo
    ? change.field === 'content' ? { ...row, content: change.value } : { ...row, details: { ...details, [change.field]: change.value } }
    : row)
  update({ ...props.modelValue, steps })
}
const addStep = (sectionCode: CutoverPlanSectionCode) => {
  if (props.modelValue.editMode === 'FULL_FILE_UPLOAD') return
  const stepNo = Math.max(0, ...stepsFor(sectionCode).map((step) => step.stepNo)) + 1
  update({ ...props.modelValue, steps: [...props.modelValue.steps, { sectionCode, stepNo, content: '' }] })
}
const removeStep = (sectionCode: CutoverPlanSectionCode, stepNo: number) => {
  if (props.modelValue.editMode === 'FULL_FILE_UPLOAD') return
  update({ ...props.modelValue, steps: props.modelValue.steps.filter((row) =>
    row.sectionCode !== sectionCode || row.stepNo !== stepNo) })
}
const mitigation = (key: string) => props.modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD'
  ? props.modelValue.riskMitigations.find((row) => row.riskFact.stableItemKey === key)?.mitigation || ''
  : ''
const updateMitigation = (riskFact: CutoverPlanRiskFact, mitigationText: string) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  const others = props.modelValue.riskMitigations.filter((row) => row.riskFact.stableItemKey !== riskFact.stableItemKey)
  update({ ...props.modelValue, riskMitigations: mitigationText
    ? [...others, { riskFact, mitigation: mitigationText }]
    : others })
}
const updateSupport = (supportArrangements: StandardCutoverPlanContent['supportArrangements']) => {
  if (props.modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD') {
    update({ ...props.modelValue, supportArrangements })
  }
}
const fileKey = (referenceKey: string) => ({
  ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: String(props.taskId),
  purposeCode: 'FULL_PLAN', referenceKey
})
const completeUpload = async (selection: FileSelection) => {
  if (props.modelValue.editMode !== 'FULL_FILE_UPLOAD') return
  fileFactReads.value++
  try {
    const fact = await resolveFileFact(selection)
    if (props.modelValue.editMode === 'FULL_FILE_UPLOAD') update({ editMode: 'FULL_FILE_UPLOAD', fileArtifactFact: fact, ownershipConfirmed: props.modelValue.ownershipConfirmed })
  } finally { fileFactReads.value-- }
}
const completeStandardUpload = async (
  field: typeof standardFileSlots[number]['field'],
  selection: FileSelection
) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  fileFactReads.value++
  try {
    const fact = await resolveFileFact(selection)
    if (props.modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD') update({ ...props.modelValue, overview: { ...props.modelValue.overview, [field]: fact } })
  } finally { fileFactReads.value-- }
}
const resolveFileFact = async (selection: FileSelection) => {
  const artifact = await FileApi.getArtifact(selection.artifactId, fileKey(selection.referenceKey))
  const versions = await FileApi.getVersions(selection.artifactId, { ...fileKey(selection.referenceKey), pageSize: 20 })
  const version = versions.items.find((row) => row.versionNo === selection.versionNo)
  if (!version) throw new Error('PLT 未返回刚完成的方案文件版本')
  return {
    artifactId: selection.artifactId,
    versionNo: selection.versionNo,
    referenceKey: selection.referenceKey,
    scopeVersion: artifact.reference.scopeVersion,
    sha256: version.sha256,
    fileFactVersion: {
      artifactVersion: artifact.artifactVersion,
      referenceVersion: artifact.reference.referenceVersion,
      availabilityVersion: version.availabilityVersion
    }
  }
}
const updateOwnership = (value: boolean | string | number) => {
  if (props.modelValue.editMode === 'FULL_FILE_UPLOAD' && Boolean(value)) {
    update({ ...props.modelValue, ownershipConfirmed: true })
  }
}
</script>

<style scoped>
.plan-editor { min-width: 0; }
.editor-section { margin-top: 24px; }
.standard-file-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.file-slot { min-width: 0; }
.file-slot:last-child { grid-column: 1 / -1; }
.file-slot > label { display: block; margin-bottom: 8px; color: #888; font-size: 12px; }
.file-slot :deep(.pms-file-uploader) { padding: 12px; border: 1px dashed #d9d9d9; border-radius: 4px; background: #fafafa; }
.table-add { margin-top: 10px; }
.risk-detail { margin: 4px 0 0; color: #888; font-size: 12px; }
@media (width <= 767px) { .standard-file-grid { grid-template-columns: 1fr; } }
</style>
