<template>
  <section class="plan-editor">
    <template v-if="modelValue.editMode === 'ONLINE_TEMPLATE_STANDARD'">
      <el-alert title="A/B/C 标准方案：概述、计划、六类步骤、风险措施与四类保障缺一不可" type="info" :closable="false" />
      <el-form label-position="top" class="overview-form">
        <el-form-item label="项目与割接概述">
          <el-input
            data-testid="plan-project-description"
            :model-value="modelValue.overview.projectDescription"
            :disabled="!editable"
            type="textarea"
            :rows="3"
            @update:model-value="updateOverview('projectDescription', $event)"
          />
        </el-form-item>
      </el-form>

      <section class="editor-section">
        <h3>方案文件</h3>
        <div class="standard-file-grid">
          <article v-for="slot in standardFileSlots" :key="slot.field" class="standard-file-card">
            <strong>{{ slot.label }}</strong>
            <PmsFileUploader
              v-if="editable"
              :data-testid="`plan-file-${slot.field}`"
              owner-context="CUT"
              object-type="CUTOVER_PLAN"
              :object-id="String(taskId)"
              purpose-code="FULL_PLAN"
              :reference-key="`cutover-plan-${taskId}-${slot.referenceSuffix}`"
              category-code="CUTOVER_PLAN"
              @completed="completeStandardUpload(slot.field, $event)"
            />
            <p v-if="modelValue.overview[slot.field]">
              {{ modelValue.overview[slot.field]?.referenceKey }} / v{{ modelValue.overview[slot.field]?.versionNo }}
            </p>
            <small v-else>尚未选择</small>
          </article>
        </div>
      </section>

      <section class="editor-section">
        <header><h3>割接计划</h3><el-button v-if="editable" @click="addSchedule">增加计划项</el-button></header>
        <div v-for="(row, index) in modelValue.overview.scheduleTable" :key="row.sequenceNo" class="schedule-row">
          <el-date-picker
            :model-value="row.plannedAt"
            :disabled="!editable"
            type="datetime"
            value-format="x"
            @update:model-value="updateSchedule(index, 'plannedAt', $event)"
          />
          <el-input :model-value="row.content" :disabled="!editable" @update:model-value="updateSchedule(index, 'content', $event)" />
          <el-button v-if="editable" link type="danger" @click="removeSchedule(index)">删除</el-button>
        </div>
      </section>

      <section class="editor-section">
        <h3>实施步骤</h3>
        <el-form label-position="top" class="step-grid">
          <el-form-item v-for="section in standardSections" :key="section.code" :label="section.label">
            <div class="section-step-list">
              <div v-for="step in stepsFor(section.code)" :key="step.stepNo" class="section-step-row">
                <span>步骤 {{ step.stepNo }}</span>
                <el-input
                  :data-testid="`plan-step-${section.code}-${step.stepNo}`"
                  :model-value="step.content"
                  :disabled="!editable"
                  type="textarea"
                  :rows="3"
                  @update:model-value="updateStep(section.code, step.stepNo, $event)"
                />
                <el-button v-if="editable" link type="danger" @click="removeStep(section.code, step.stepNo)">删除</el-button>
              </div>
              <el-button v-if="editable" @click="addStep(section.code)">增加步骤</el-button>
            </div>
          </el-form-item>
        </el-form>
      </section>

      <section v-if="sourceSnapshot?.failedRiskFacts.length" class="editor-section">
        <h3>风险缓解措施</h3>
        <el-form label-position="top">
          <el-form-item v-for="risk in sourceSnapshot.failedRiskFacts" :key="risk.stableItemKey" :label="risk.itemName">
            <el-input
              :model-value="mitigation(risk.stableItemKey)"
              :disabled="!editable"
              type="textarea"
              @update:model-value="updateMitigation(risk, $event)"
            />
          </el-form-item>
        </el-form>
      </section>

      <section class="editor-section">
        <h3>保障安排</h3>
        <CutoverSupportArrangements
          :model-value="modelValue.supportArrangements"
          :readonly="!editable"
          :patch-approved="patchApproved"
          @update:model-value="updateSupport"
          @patch="$emit('patch-support', $event)"
        />
      </section>
    </template>

    <template v-else-if="modelValue.editMode === 'ONLINE_TEMPLATE_SIMPLE_D'">
      <el-alert title="D 级简易方案仅填写阶段操作与回退步骤" type="warning" :closable="false" />
      <el-form label-position="top" class="simple-grid">
        <el-form-item v-for="section in simpleSections" :key="section.code" :label="section.label">
          <div class="section-step-list">
            <div v-for="step in stepsFor(section.code)" :key="step.stepNo" class="section-step-row">
              <span>步骤 {{ step.stepNo }}</span>
              <el-input
                :data-testid="`plan-step-${section.code}-${step.stepNo}`"
                :model-value="step.content"
                :disabled="!editable"
                type="textarea"
                :rows="5"
                @update:model-value="updateStep(section.code, step.stepNo, $event)"
              />
              <el-button v-if="editable" link type="danger" @click="removeStep(section.code, step.stepNo)">删除</el-button>
            </div>
            <el-button v-if="editable" @click="addStep(section.code)">增加步骤</el-button>
          </div>
        </el-form-item>
      </el-form>
    </template>

    <template v-else-if="modelValue.editMode === 'FULL_FILE_UPLOAD'">
      <el-alert title="完整方案文件只保存 PLT 文件身份与版本事实，不提交 URL 或文件正文" type="info" :closable="false" />
      <PmsFileUploader
        v-if="editable"
        data-testid="plan-file-uploader"
        owner-context="CUT"
        object-type="CUTOVER_PLAN"
        :object-id="String(taskId)"
        purpose-code="FULL_PLAN"
        :reference-key="`cutover-plan-${taskId}`"
        category-code="CUTOVER_PLAN"
        @completed="completeUpload"
      />
      <el-descriptions v-if="modelValue.fileArtifactFact" :column="1" border>
        <el-descriptions-item label="Artifact ID">{{ modelValue.fileArtifactFact.artifactId }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ modelValue.fileArtifactFact.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="稳定引用">{{ modelValue.fileArtifactFact.referenceKey }}</el-descriptions-item>
      </el-descriptions>
      <el-checkbox
        data-testid="plan-ownership-confirmed"
        :model-value="modelValue.ownershipConfirmed"
        :disabled="!editable"
        @update:model-value="updateOwnership"
      >确认本人有权使用并提交该完整方案文件</el-checkbox>
    </template>
  </section>
</template>

<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file'
import type {
  CutoverPlanRiskFact,
  CutoverPlanSectionCode,
  CutoverPlanSourceSnapshot,
  StandardCutoverPlanContent,
  WireDateTime,
  WritableCutoverPlanContent
} from '@/api/pms/cutover/cutover-task'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'
import CutoverSupportArrangements from './CutoverSupportArrangements.vue'

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
}>()

const standardSections: Array<{ code: CutoverPlanSectionCode; label: string }> = [
  { code: 'PRE_OPERATION', label: '操作前准备' },
  { code: 'OPERATION', label: '阶段操作' },
  { code: 'CLOSING_COLLECTION', label: '收尾与信息采集' },
  { code: 'POST_BUSINESS_TEST', label: '割接后业务测试' },
  { code: 'ROLLBACK', label: '回退步骤' },
  { code: 'POST_CUTOVER_SUPPORT', label: '割接后保障' }
]
const simpleSections = standardSections.filter((section) => ['OPERATION', 'ROLLBACK'].includes(section.code))
const standardFileSlots = [
  { field: 'preTopologyFile', label: '割接前拓扑', referenceSuffix: 'pre-topology' },
  { field: 'postTopologyFile', label: '割接后拓扑', referenceSuffix: 'post-topology' },
  { field: 'networkConfigurationFile', label: '组网配置', referenceSuffix: 'network-configuration' }
] as const

const update = (value: WritableCutoverPlanContent) => emit('update:modelValue', value)
const updateOverview = (field: 'projectDescription', value: string) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  update({ ...props.modelValue, overview: { ...props.modelValue.overview, [field]: value } })
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
  const fact = await resolveFileFact(selection)
  update({ editMode: 'FULL_FILE_UPLOAD', fileArtifactFact: fact, ownershipConfirmed: props.modelValue.ownershipConfirmed })
}
const completeStandardUpload = async (
  field: typeof standardFileSlots[number]['field'],
  selection: FileSelection
) => {
  if (props.modelValue.editMode !== 'ONLINE_TEMPLATE_STANDARD') return
  const fact = await resolveFileFact(selection)
  update({ ...props.modelValue, overview: { ...props.modelValue.overview, [field]: fact } })
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
.editor-section { margin-top: 20px; }
.editor-section > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.schedule-row { display: grid; grid-template-columns: minmax(180px, 0.45fr) minmax(220px, 1fr) auto; gap: 10px; margin-bottom: 10px; }
.step-grid, .simple-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.standard-file-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.standard-file-card { min-width: 0; padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.standard-file-card p, .standard-file-card small { overflow-wrap: anywhere; }
.section-step-list { width: 100%; }
.section-step-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: 8px; margin-bottom: 8px; }
@media (max-width: 767px) {
  .schedule-row, .step-grid, .simple-grid, .standard-file-grid, .section-step-row { grid-template-columns: 1fr; }
}
</style>
