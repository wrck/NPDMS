<template>
  <section class="plan-editor">
    <template v-if="modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD'">
      <h4 class="cut-section-title">一、基本信息部分</h4>
      <el-form label-position="top"><el-form-item label="项目与割接概述"><el-input data-testid="plan-project-description" :model-value="modelValue.overview.projectDescription" :disabled="!editable" type="textarea" :rows="3" @update:model-value="updateOverview('projectDescription', $event)" /></el-form-item></el-form>
      <h4 class="cut-section-title">项目割接计划</h4>
      <el-table :data="modelValue.overview.scheduleTable" empty-text="暂无计划项">
        <el-table-column label="日期" width="250"><template #default="{ row, $index }"><el-date-picker :data-testid="`plan-schedule-${$index}-plannedAt`" :model-value="row.plannedAt" :disabled="!editable" type="datetime" value-format="x" @update:model-value="updateSchedule($index, 'plannedAt', $event)" /></template></el-table-column>
        <el-table-column label="工作内容" min-width="300"><template #default="{ row, $index }"><el-input :data-testid="`plan-schedule-${$index}-content`" :model-value="row.content" :disabled="!editable" @update:model-value="updateSchedule($index, 'content', $event)" /></template></el-table-column>
        <el-table-column v-if="editable" label="操作" width="76"><template #default="{ $index }"><el-button link type="danger" @click="removeSchedule($index)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button v-if="editable" class="table-add" @click="addSchedule"><Icon icon="ep:plus" />增加计划项</el-button>
      <h4 class="cut-section-title">网络拓扑与组网配置</h4>
      <p v-if="editable && recoveredStandardFiles" role="status">已恢复本任务已上传的当前材料版本，保存方案后生效。</p>
      <div v-if="editable && standardFileReferencesError" role="alert">
        <p>{{ standardFileReferencesError }}</p>
        <el-button data-testid="retry-plan-file-references" @click="readStandardFileReferences">重新读取文件状态</el-button>
      </div>
      <div class="standard-file-grid">
        <div v-for="slot in standardFileSlots" :key="slot.field" class="file-slot">
          <label>{{ slot.label }}</label>
          <template v-if="!modelValue.overview[slot.field] && slot.field === 'preTopologyFile' && surveyTopology">
            <small class="cut-muted">来自调研，保存时带入方案</small>
            <PmsFileVersionReferenceList
              owner-context="CUT" object-type="CUTOVER_CHECKLIST_ITEM" :object-id="String(surveyTopology.itemId)"
              purpose-code="MANUAL_EVIDENCE" :reference-key="surveyTopology.file.referenceKey" :artifact-id="surveyTopology.file.artifactId" :version-no="surveyTopology.file.versionNo" />
          </template>
          <PmsFileUploader
v-if="editable" ref="standardUploadRefs" :data-testid="'plan-file-' + slot.field" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
            :key="String(taskId) + ':' + (modelValue.overview[slot.field]?.referenceKey || slot.referenceSuffix)"
            purpose-code="FULL_PLAN" :reference-key="modelValue.overview[slot.field]?.referenceKey || 'cutover-plan-' + taskId + '-' + slot.referenceSuffix"
            :artifact-id="standardFileReferences[slot.field]?.artifactId ?? modelValue.overview[slot.field]?.artifactId"
            :expected-reference-version="standardFileReferences[slot.field]?.referenceVersion ?? modelValue.overview[slot.field]?.fileFactVersion.referenceVersion"
            :disabled="standardFileReferencesLoading || !!standardFileReferencesError"
            category-code="CUTOVER_PLAN" @completed="completeStandardUpload(slot.field, $event)" />
          <p v-if="editable" class="cut-help">支持PDF及图片，单文件不超过50MB。</p>
          <PmsFileVersionReferenceList
v-if="modelValue.overview[slot.field]" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)"
            purpose-code="FULL_PLAN" :reference-key="modelValue.overview[slot.field]!.referenceKey" :artifact-id="modelValue.overview[slot.field]!.artifactId" :version-no="modelValue.overview[slot.field]!.versionNo" />
          <p v-else-if="!editable" class="cut-help">未上传</p>
        </div>
      </div>
      <h4 class="cut-section-title">割接设备清单</h4>
      <el-table :data="modelValue.overview.deviceSummary" empty-text="当前方案未记录设备">
        <el-table-column v-for="column in deviceColumns" :key="column.field" :label="column.label" min-width="200">
          <template #default="{ row }"><el-input
:data-testid="'plan-device-' + row.deviceId + '-' + column.field"
            :model-value="deviceDetail(row.deviceId)?.[column.field] || ''" :disabled="!editable"
            @update:model-value="updateDeviceDetail({ deviceId: row.deviceId, field: column.field, value: $event })" />
            <small v-if="column.field === 'productModel'" class="cut-muted">{{ row.serialNumber }}</small>
          </template>
        </el-table-column>
      </el-table>
      <section ref="supportValidationRoot" class="support-validation" :class="{ 'has-validation-error': !!supportValidationMessage }" tabindex="-1">
      <h4 class="cut-section-title">割接保障人员安排</h4>
      <p v-if="supportValidationMessage" class="validation-message" role="alert">{{ supportValidationMessage }}</p>
      <CutoverSupportArrangements
:model-value="modelValue.supportArrangements" :readonly="!editable" :patch-approved="patchApproved"
        :person-choices="personChoices" @update:model-value="updateSupport" @patch="$emit('patch-support', $event)" />
      </section>
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
          <el-input
data-testid="plan-rollback-description" :model-value="modelValue.rollbackDescription || ''" :disabled="!editable"
            type="textarea" :rows="3" @update:model-value="updateRollbackDescription" />
        </template>
        <h4 class="cut-section-title">{{ section.label }}</h4>
        <el-table :data="stepsFor(section.code)" :empty-text="'暂无' + section.label">
          <el-table-column prop="stepNo" label="序号" width="68" />
          <el-table-column v-for="column in tableColumns[section.code]" :key="column.field" :label="column.label" :min-width="column.width">
            <template #default="{ row }">
              <template v-if="isDateTimeColumn(section.code, column.field)">
                <el-date-picker
                  :data-testid="'plan-step-' + section.code + '-' + row.stepNo + '-' + column.field"
                  :model-value="dateTimeValue(column.field === 'content' ? row.content : row.details?.operationTime)"
                  :disabled="!editable" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                  @update:model-value="updateTableStep({ row, field: column.field, value: $event || '' })" />
                <small v-if="(column.field === 'content' ? row.content : row.details?.operationTime) && !dateTimeValue(column.field === 'content' ? row.content : row.details?.operationTime)" class="cut-muted">
                  原填写：{{ column.field === 'content' ? row.content : row.details?.operationTime }}
                </small>
              </template>
              <el-input
                v-else
:data-testid="'plan-step-' + section.code + '-' + row.stepNo + '-' + column.field"
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
      <PmsFileUploader
v-if="editable" ref="uploadedPlanRef" data-testid="plan-file-uploader" accept=".doc,.docx,.pdf" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)" purpose-code="FULL_PLAN"
        :reference-key="modelValue.fileArtifactFact?.referenceKey || 'cutover-plan-' + taskId"
        :artifact-id="(modelValue.fileArtifactFact?.artifactId as number | undefined)"
        :expected-reference-version="modelValue.fileArtifactFact?.fileFactVersion.referenceVersion"
        category-code="CUTOVER_PLAN" @completed="completeUpload" />
      <p v-if="editable" class="cut-help">支持Word、PDF，单文件不超过50MB。</p>
      <PmsFileVersionReferenceList
v-if="modelValue.fileArtifactFact" owner-context="CUT" object-type="CUTOVER_PLAN" :object-id="String(taskId)" purpose-code="FULL_PLAN"
        :reference-key="modelValue.fileArtifactFact.referenceKey" :artifact-id="modelValue.fileArtifactFact.artifactId" :version-no="modelValue.fileArtifactFact.versionNo" />
      <el-checkbox data-testid="plan-ownership-confirmed" :model-value="modelValue.ownershipConfirmed" :disabled="!editable" @update:model-value="updateOwnership">确认本人有权使用并提交该完整方案文件</el-checkbox>
    </template>
  </section>
</template>
<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file/version-reference'
import type {
  CutoverPlanRiskFact,
  CutoverPlanFileFact,
  CutoverPlanSupportArrangement,
  CutoverPlanDeviceDetails,
  CutoverPlanSectionCode,
  CutoverPlanSourceSnapshot,
  CutoverPlanStep,
  CutoverPlanStepDetails,
  StandardCutoverPlanContent,
  WireDateTime,
  WireLong
} from '@/api/pms/cutover'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'
import type { FileSelection } from '@/components/PmsFileArtifact'
import type { CutoverPlanDraftContent } from '../shared/cutoverPlanDraft'
import dayjs from 'dayjs'
import CutoverSupportArrangements from './CutoverSupportArrangements.vue'
import { focusPlanSupportValidation } from '../shared/cutoverPlanValidation'
import { surveyPlanFile } from '../shared/cutoverPlanSurvey'

// PLT 上传器的 ID 类型声明较窄；模板断言仅兼容声明，原样传递 Snowflake 字符串，不能转换为 Number。

const props = defineProps<{
  modelValue: CutoverPlanDraftContent
  sourceSnapshot: CutoverPlanSourceSnapshot | null
  taskId: string | number
  editable: boolean
  patchApproved?: boolean
  supportValidationMessage?: string
  surveyItems?: import('@/api/pms/cutover').CutoverChecklistItem[]
}>()
const surveyTopology = computed(() => surveyPlanFile(props.surveyItems || [], 'SURVEY_CURRENT_TOPOLOGY'))
const emit = defineEmits<{
  'update:modelValue': [value: CutoverPlanDraftContent]
  'patch-support': [value: StandardCutoverPlanContent['supportArrangements'][number]]
  'upload-pending': [pending: boolean]
  'file-state-pending': [pending: boolean]
}>()
const standardUploadRefs = ref<Array<InstanceType<typeof PmsFileUploader>>>([])
const supportValidationRoot = ref<HTMLElement>()
defineExpose({ focusSupportValidation: () => {
  if (supportValidationRoot.value) focusPlanSupportValidation(supportValidationRoot.value)
} })
const uploadedPlanRef = ref<InstanceType<typeof PmsFileUploader>>()
const fileFactReads = ref(0)
const standardFileReferences = ref<Partial<Record<StandardFileField, { artifactId: WireLong; referenceVersion: number } | null>>>({})
const standardFileReferencesLoading = ref(false)
watch(standardFileReferencesLoading, pending => emit('file-state-pending', pending), { immediate: true })
const standardFileReferencesError = ref('')
const recoveredStandardFiles = ref(false)
let standardFileReadSequence = 0
const pendingUploads = computed(() => fileFactReads.value > 0
  || standardUploadRefs.value.some(uploader => uploader.isBusy() || uploader.hasPendingFile())
  || !!uploadedPlanRef.value && (uploadedPlanRef.value.isBusy() || uploadedPlanRef.value.hasPendingFile()))
watch(pendingUploads, pending => emit('upload-pending', pending), { immediate: true })

const personChoices = ref<Partial<Record<CutoverPlanSupportArrangement['roleCode'], import('@/api/pms/cutover').SupportPersonChoice[]>>>({})
let personReadSequence = 0
watch([() => props.taskId, () => props.editable, () => props.patchApproved], async () => {
  const sequence = ++personReadSequence
  personChoices.value = {}
  if (!props.editable && !props.patchApproved) return
  const api = await import('@/api/pms/cutover')
  const roles = ['CUSTOMER', 'DP_FIRST_LINE', 'DP_SECOND_LINE', 'DP_RND'] as const
  try {
    const choices = await Promise.all(roles.map(async role => [role, await api.getSupportPersonChoices(props.taskId, role)] as const))
    if (sequence === personReadSequence) personChoices.value = Object.fromEntries(choices)
  } catch {
    // The request layer already shows the source error; keep failed choices empty.
    if (sequence === personReadSequence) personChoices.value = {}
  }
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
const isDateTimeColumn = (section: CutoverPlanSectionCode, field: TableStepField) => field === 'operationTime'
  || section === 'POST_CUTOVER_SUPPORT' && field === 'content'
const dateTimeValue = (value?: string) => value && /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$/.test(value)
  && dayjs(value).isValid() ? value : null
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
    { field: 'operationTime', label: '操作时间', width: 250 }
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
    { field: 'operationTime', label: '操作时间', width: 250 }
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

const update = (value: CutoverPlanDraftContent) => emit('update:modelValue', value)
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
type StandardFileField = typeof standardFileSlots[number]['field']
const shouldRestoreStandardFile = ({ reference, current }: {
  reference: Pick<FileApi.FileCurrentReferenceVO, 'artifactId' | 'versionNo' | 'referenceVersion'>
  current: CutoverPlanFileFact | null
}) => !current || (reference.referenceVersion > current.fileFactVersion.referenceVersion
  && (String(reference.artifactId) !== String(current.artifactId) || reference.versionNo >= current.versionNo))
const readStandardFileReferences = async () => {
  const sequence = ++standardFileReadSequence
  const content = props.modelValue
  standardFileReferences.value = {}
  standardFileReferencesError.value = ''
  recoveredStandardFiles.value = false
  standardFileReferencesLoading.value = false
  if (!props.editable || content.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  standardFileReferencesLoading.value = true
  const keys = standardFileSlots.map(slot => ({ field: slot.field,
    key: fileKey(content.overview[slot.field]?.referenceKey || `cutover-plan-${props.taskId}-${slot.referenceSuffix}`)
  }))
  try {
    const references = await Promise.all(keys.map(async slot =>
      [slot.field, await FileApi.getCurrentReference(slot.key)] as const))
    if (sequence !== standardFileReadSequence || props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
    const currentOverview = props.modelValue.overview
    standardFileReferences.value = Object.fromEntries(references.map(([field, reference]) => {
      const known = standardFileReferences.value[field]
      const current = currentOverview[field]
      if (reference && known && known.referenceVersion > reference.referenceVersion
        && (!current || known.referenceVersion >= current.fileFactVersion.referenceVersion)) return [field, known]
      if (reference && current && current.fileFactVersion.referenceVersion > reference.referenceVersion) {
        return [field, { artifactId: current.artifactId, referenceVersion: current.fileFactVersion.referenceVersion }]
      }
      return [field, reference]
    }))
    const recoverable = references.filter(([field, reference]) => reference?.status === 'ACTIVE'
      && shouldRestoreStandardFile({ reference, current: content.overview[field] }))
    if (recoverable.length === 0) return
    const restored = await Promise.all(recoverable.map(async ([field, reference]) =>
      [field, await resolveFileFact(reference!)] as const))
    if (sequence !== standardFileReadSequence || props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
    const overview = { ...props.modelValue.overview }
    let changed = false
    for (const [field, fact] of restored) {
      if (shouldRestoreStandardFile({ reference: { artifactId: fact.artifactId, versionNo: fact.versionNo,
        referenceVersion: fact.fileFactVersion.referenceVersion }, current: overview[field] })) {
        overview[field] = fact; changed = true
      }
    }
    if (changed) {
      recoveredStandardFiles.value = true
      update({ ...props.modelValue, overview })
    }
  } catch {
    if (sequence === standardFileReadSequence) standardFileReferencesError.value = '文件状态读取失败，请重新读取后再上传。已填写的方案内容保留。'
  } finally {
    if (sequence === standardFileReadSequence) standardFileReferencesLoading.value = false
  }
}
watch(() => {
  const content = props.modelValue
  return JSON.stringify([props.taskId, props.editable, content.editMode,
    content.editMode === 'ONLINE_TEMPLATE_STANDARD'
      ? standardFileSlots.map(slot => content.overview[slot.field]?.referenceKey || `cutover-plan-${props.taskId}-${slot.referenceSuffix}`) : []])
}, readStandardFileReferences, { immediate: true })
onBeforeUnmount(() => { standardFileReadSequence++; emit('file-state-pending', false) })

const completeUpload = async (selection: FileSelection) => {
  if (props.modelValue.editMode !== 'FULL_FILE_UPLOAD') return
  fileFactReads.value++
  try {
    const fact = await resolveFileFact(selection)
    if (props.modelValue.editMode === 'FULL_FILE_UPLOAD') update({ ...props.modelValue, fileArtifactFact: fact, ownershipConfirmed: false })
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
    if (props.modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD') {
      standardFileReferences.value[field] = { artifactId: fact.artifactId, referenceVersion: fact.fileFactVersion.referenceVersion }
      update({ ...props.modelValue, overview: { ...props.modelValue.overview, [field]: fact } })
    }
  } finally { fileFactReads.value-- }
}
const resolveFileFact = async (selection: Pick<CutoverPlanFileFact, 'artifactId' | 'versionNo' | 'referenceKey'> & { referenceVersion?: number }) => {
  const artifact = await FileApi.getArtifact(selection.artifactId, fileKey(selection.referenceKey))
  if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
  if (selection.referenceVersion !== undefined && selection.referenceVersion !== artifact.reference.referenceVersion) {
    throw new Error('材料引用在读取期间已变化，请重新读取文件状态')
  }
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
  if (props.modelValue.editMode === 'FULL_FILE_UPLOAD') {
    update({ ...props.modelValue, ownershipConfirmed: Boolean(value) })
  }
}
</script>

<style scoped>
.plan-editor { min-width: 0; }
.plan-editor :deep(.el-date-editor.el-input) { max-width: 100%; }
.support-validation { scroll-margin-block: 100px; }
.support-validation.has-validation-error { outline: 2px solid var(--el-color-danger); outline-offset: 4px; }
.validation-message { margin: 0 0 12px; color: var(--el-color-danger); }
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
