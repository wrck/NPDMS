<template>
  <el-drawer v-model="visible" :title="title" :size="drawerSize" destroy-on-close>
    <el-alert
      v-if="mode !== 'INITIAL'"
      title="变更先保存为草稿，提交审批后才会影响当前工期。"
      type="info"
      :closable="false"
      class="form-alert"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" :disabled="!canWrite">
      <el-alert v-if="project.projectEndDate" type="info" :closable="false" class="form-alert"
        title="可按工勘登记的项目结束日期倒排天数，也可切换为起止日期直接录入。自然日包含首尾两天；此处不会回写项目结束日期。" />
      <template v-if="project.projectEndDate">
        <el-form-item label="录入方式">
          <el-radio-group v-model="entryMode">
            <el-radio-button value="BACKWARD">按工勘日期倒排天数</el-radio-button>
            <el-radio-button value="DATE_RANGE">起止日期</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <div class="date-grid">
          <el-form-item label="工勘结束日期">
            <el-input :model-value="project.projectEndDate" readonly />
          </el-form-item>
          <template v-if="entryMode === 'BACKWARD'">
            <el-form-item label="自然日天数" prop="durationDays">
              <el-input-number v-model="form.durationDays" :min="1" :max="36500" controls-position="right" />
            </el-form-item>
            <el-form-item label="倒排开始日期" prop="startDate">
              <el-input :model-value="form.startDate" readonly placeholder="填写天数后自动倒排" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="自然日天数">
              <el-input :model-value="computedDaysText" readonly />
            </el-form-item>
            <el-alert
              v-if="form.endDate && form.endDate !== project.projectEndDate"
              title="结束日期与工勘要求的项目结束日期不同；请确认客户已认可新的结束时间。"
              type="warning"
              :closable="false"
              class="form-alert"
            />
          </template>
        </div>
      </template>
      <div v-else class="date-grid">
        <el-form-item label="计算口径" prop="calculationBasis">
          <el-radio-group v-model="form.calculationBasis" @change="resetDerivedField">
            <el-radio-button value="DATE_RANGE">起止日期</el-radio-button>
            <el-radio-button value="DURATION_FROM_START">起点 + 天数</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item v-if="form.calculationBasis === 'DATE_RANGE'" label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item v-else label="自然日天数" prop="durationDays">
          <el-input-number
            v-model="form.durationDays"
            :min="1"
            :max="36500"
            controls-position="right"
          />
        </el-form-item>
      </div>
      <template v-if="mode !== 'INITIAL'">
        <el-form-item label="变更原因" prop="reasonDetail">
          <el-input
            v-model="form.reasonDetail"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="请填写变更原因"
          />
        </el-form-item>
        <section class="evidence-section">
          <div class="evidence-heading">
            <div>
              <strong>附件</strong>
              <span>可上传变更依据等支持文件（可选）；上传后点击“保存草稿”随表单一并保存。</span>
            </div>
          </div>
          <el-alert
            v-if="mode === 'CREATE'"
            title="先保存工期变更草稿，随后即可在当前抽屉上传附件。"
            type="info"
            :closable="false"
          />
          <template v-else-if="mode === 'EDIT' && draft">
            <PmsFileReferenceList
              ref="evidenceListRef"
              owner-context="SOL"
              object-type="CONSTRUCTION_PLAN_CHANGE"
              :object-id="String(draft.changeId)"
              purpose-code="CUSTOMER_DELAY_EVIDENCE"
              :reference-key="activeEvidenceReferenceKey"
              :artifact-id="form.customerEvidenceFileId"
              :version-no="form.customerEvidenceFileVersion"
              :editable="canWrite"
              @loaded="onEvidenceLoaded"
              @detached="clearEvidence"
            />
            <PmsFileUploader
              owner-context="SOL"
              object-type="CONSTRUCTION_PLAN_CHANGE"
              :object-id="String(draft.changeId)"
              purpose-code="CUSTOMER_DELAY_EVIDENCE"
              :reference-key="activeEvidenceReferenceKey"
              category-code="CUSTOMER_DELAY_EVIDENCE"
              :artifact-id="evidenceSlot.state.artifactId"
              :expected-reference-version="evidenceSlot.state.referenceVersion"
              :disabled="
                !canWrite ||
                (Boolean(evidenceSlot.state.artifactId) &&
                  evidenceSlot.state.referenceVersion === undefined)
              "
              class="evidence-uploader"
              @completed="saveEvidence"
            />
          </template>
        </section>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canWrite" @click="save">
        {{ mode === 'INITIAL' ? '保存并生效' : '保存草稿' }}
      </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { generateUUID } from '@/utils'
import { computed, onBeforeUnmount, reactive, ref, toRaw, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import { backwardDuration } from './backwardDuration'
import { initialDurationHint } from './durationEntry'
import { PmsFileReferenceList, PmsFileUploader } from '@/components/PmsFileArtifact'
import { useFileSlotState } from '@/components/PmsFileArtifact/useFileSlotState'
import type { DetachedFileSlot, FileSelection } from '@/components/PmsFileArtifact/types'
import type { FileArtifactVO } from '@/api/pms/platform/file'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import type {
  ConstructionPlanChangeVO,
  ConstructionPlanVO,
  PatchDurationChangeReqVO
} from '@/api/pms/engineering/construction-plan'
import {
  formStateFromChange,
  type DurationChangeFormState
} from './durationChangeFormState'

const props = defineProps<{ project: ProjectMasterVO; readonly?: boolean }>()
const emit = defineEmits<{ saved: []; 'dirty-change': [value: boolean] }>()
const canWrite = computed(
  () => !props.readonly && Number.isSafeInteger(props.project.id) && props.project.id! > 0
)
let contextVersion = 0
const message = useMessage()
const narrow = useMediaQuery('(max-width: 767px)')
const drawerSize = computed(() => (narrow.value ? '100%' : '560px'))
const visible = ref(false)
const saving = ref(false)
// 附件已选/已上传但尚未随“保存草稿”落库：置脏提醒外层守卫，避免关抽屉后丢失指向
const stagedEvidence = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'INITIAL' | 'CREATE' | 'EDIT'>('INITIAL')
const plan = ref<ConstructionPlanVO>()
const draft = ref<ConstructionPlanChangeVO>()
const original = ref<FormModel>()
const title = computed(() =>
  mode.value === 'INITIAL'
    ? '录入项目工期'
    : mode.value === 'CREATE'
      ? '新建工期变更'
      : '编辑工期变更草稿'
)
type FormModel = DurationChangeFormState
const emptyForm = (): FormModel => ({
  calculationBasis: 'DATE_RANGE',
  startDate: '',
  endDate: '',
  durationDays: undefined,
  // 原因改自由文本填写，原因码固定落"其它"，不再选择原因
  reasonType: 'OTHER',
  reasonDetail: '',
  customerEvidenceFileId: undefined,
  customerEvidenceFileVersion: undefined,
  customerEvidenceReferenceKey: undefined
})
const form = reactive<FormModel>(emptyForm())
// 有工勘结束日期时的录入方式：BACKWARD=按工勘日期倒排天数（默认，兼容既有流程），
// DATE_RANGE=直接录入起止日期；两者提交时统一为 DATE_RANGE 口径。
type SurveyEntryMode = 'BACKWARD' | 'DATE_RANGE'
const entryMode = ref<SurveyEntryMode>('BACKWARD')
const computedDays = computed(() => {
  if (!form.startDate || !form.endDate || !dayjs(form.startDate).isValid() || !dayjs(form.endDate).isValid())
    return undefined
  const days = dayjs(form.endDate).diff(dayjs(form.startDate), 'day') + 1
  return days > 0 ? days : undefined
})
const computedDaysText = computed(() =>
  computedDays.value ? `${computedDays.value} 天（按起止日期自动计算）` : '由起止日期自动计算'
)
watch(
  () => [visible.value, props.project.projectEndDate, form.durationDays] as const,
  () => {
    if (!visible.value || !props.project.projectEndDate) return
    if (entryMode.value !== 'BACKWARD') return
    // Reuse the existing date-range revision and approval workflow after calculating its interval.
    form.calculationBasis = 'DATE_RANGE'
    form.endDate = props.project.projectEndDate
    form.startDate = backwardDuration(props.project.projectEndDate, form.durationDays)
  },
  { flush: 'sync' }
)
watch(
  () => [form.startDate, form.endDate] as const,
  () => {
    if (!visible.value || !props.project.projectEndDate) return
    if (entryMode.value !== 'DATE_RANGE') return
    form.durationDays = computedDays.value
  },
  { flush: 'sync' }
)
watch(entryMode, (mode) => {
  if (!visible.value || !props.project.projectEndDate) return
  form.calculationBasis = 'DATE_RANGE'
  if (mode === 'BACKWARD') {
    form.endDate = props.project.projectEndDate
    if (!form.durationDays) form.durationDays = computedDays.value
    form.startDate = backwardDuration(props.project.projectEndDate, form.durationDays)
  } else if (!form.endDate) {
    form.endDate = props.project.projectEndDate
  }
})
const evidenceReferenceKey = 'customer-delay'
const evidenceListRef = ref<InstanceType<typeof PmsFileReferenceList>>()
const evidenceSlot = useFileSlotState()
const activeEvidenceReferenceKey = computed(
  () => evidenceSlot.state.referenceKey || evidenceReferenceKey
)
const rules = computed<FormRules<FormModel>>(() => {
  const base: FormRules<FormModel> = {
    reasonDetail: [{ required: true, message: '请填写变更原因' }]
  }
  if (props.project.projectEndDate) {
    if (entryMode.value === 'BACKWARD') {
      base.durationDays = [{ required: true, message: '请输入自然日天数' }]
      return base
    }
    base.startDate = [{ required: true, message: '请选择开始日期' }]
    base.endDate = [
      { required: true, message: '请选择结束日期' },
      {
        validator: (_rule, value: string, callback) =>
          !value || !form.startDate || value >= form.startDate
            ? callback()
            : callback(new Error('结束日期不得早于开始日期')),
        trigger: 'change'
      }
    ]
    return base
  }
  return {
    ...base,
    calculationBasis: [{ required: true, message: '请选择计算口径' }],
    startDate: [{ required: true, message: '请选择开始日期' }],
    endDate: [{ required: true, message: '请选择结束日期' }],
    durationDays: [{ required: true, message: '请输入自然日天数' }]
  }
})

const assign = (value: Partial<FormModel>) => Object.assign(form, emptyForm(), value)
const resetDerivedField = () => {
  if (form.calculationBasis === 'DATE_RANGE') form.durationDays = undefined
  else form.endDate = undefined
}
const durationPayload = () => ({
  calculationBasis: form.calculationBasis,
  startDate: form.startDate,
  ...(form.calculationBasis === 'DATE_RANGE'
    ? { endDate: form.endDate }
    : { durationDays: form.durationDays })
})

// 草稿/生效值与“按工勘日期倒排”完全一致时沿用倒排视图，否则保留为起止日期视图。
const pickSurveyEntryMode = (state: Pick<FormModel, 'startDate' | 'endDate' | 'durationDays'>): SurveyEntryMode => {
  const surveyEnd = props.project.projectEndDate
  if (!surveyEnd) return 'BACKWARD'
  return state.endDate === surveyEnd && state.startDate === backwardDuration(surveyEnd, state.durationDays)
    ? 'BACKWARD'
    : 'DATE_RANGE'
}

const openInitial = () => {
  if (!canWrite.value || saving.value) return
  const hint = initialDurationHint(props.project)
  if (hint) return message.warning(hint)
  mode.value = 'INITIAL'
  plan.value = undefined
  draft.value = undefined
  original.value = undefined
  evidenceSlot.reset()
  stagedEvidence.value = false
  entryMode.value = 'BACKWARD'
  assign({})
  visible.value = true
}
const openCreate = (value: ConstructionPlanVO) => {
  if (!canWrite.value || saving.value || value.projectId !== props.project.id) return
  mode.value = 'CREATE'
  plan.value = value
  draft.value = undefined
  original.value = undefined
  evidenceSlot.reset()
  stagedEvidence.value = false
  const snapshot = { ...value.currentRevision, reasonType: 'OTHER', reasonDetail: '' }
  if (props.project.projectEndDate) snapshot.calculationBasis = 'DATE_RANGE'
  entryMode.value = pickSurveyEntryMode(snapshot)
  assign(snapshot)
  visible.value = true
}
const openEdit = (value: ConstructionPlanVO, change: ConstructionPlanChangeVO) => {
  if (!canWrite.value || saving.value || value.projectId !== props.project.id) return
  mode.value = 'EDIT'
  plan.value = value
  draft.value = change
  const snapshot = formStateFromChange(change)
  if (props.project.projectEndDate) snapshot.calculationBasis = 'DATE_RANGE'
  entryMode.value = pickSurveyEntryMode(snapshot)
  assign(snapshot)
  original.value = structuredClone(snapshot)
  evidenceSlot.reset(change.customerEvidenceFileId, change.customerEvidenceReferenceKey)
  stagedEvidence.value = false
  visible.value = true
}

const patchPayload = (): PatchDurationChangeReqVO => {
  const before = original.value!
  const patch: PatchDurationChangeReqVO = { expectedProjectVersion: props.project.version || 0 }
  if (form.calculationBasis !== before.calculationBasis)
    patch.calculationBasis = form.calculationBasis
  if (form.startDate !== before.startDate) patch.startDate = form.startDate
  if ((form.endDate || null) !== (before.endDate || null)) patch.endDate = form.endDate || null
  if ((form.durationDays || null) !== (before.durationDays || null))
    patch.durationDays = form.durationDays || null
  if (form.reasonType !== before.reasonType) patch.reasonType = form.reasonType
  if (form.reasonDetail !== before.reasonDetail) patch.reasonDetail = form.reasonDetail || null
  if (form.customerEvidenceFileId !== before.customerEvidenceFileId) {
    patch.customerEvidenceFileId = form.customerEvidenceFileId || null
  }
  if (form.customerEvidenceFileVersion !== before.customerEvidenceFileVersion) {
    patch.customerEvidenceFileVersion = form.customerEvidenceFileVersion || null
  }
  if (form.customerEvidenceReferenceKey !== before.customerEvidenceReferenceKey) {
    patch.customerEvidenceReferenceKey = form.customerEvidenceReferenceKey || null
  }
  return patch
}

const onEvidenceLoaded = (artifact: FileArtifactVO) => {
  evidenceSlot.loaded(artifact)
  // 草稿尚未指向附件但引用上已有生效版本（如上传后未保存即关闭）：采纳为待保存状态
  if (!form.customerEvidenceFileId && artifact.artifactId && artifact.reference?.versionNo) {
    form.customerEvidenceFileId = artifact.artifactId
    form.customerEvidenceFileVersion = artifact.reference.versionNo
    form.customerEvidenceReferenceKey = artifact.reference.referenceKey
    if (original.value)
      Object.assign(original.value, {
        customerEvidenceFileId: undefined,
        customerEvidenceFileVersion: undefined,
        customerEvidenceReferenceKey: undefined
      })
    stagedEvidence.value = true
  }
}

// 附件不单独保存：上传/解除只更新表单状态并置脏，随“保存草稿”一并提交
const saveEvidence = async (selection: FileSelection) => {
  if (!canWrite.value || !plan.value || !draft.value) return
  evidenceSlot.uploaded(selection)
  Object.assign(form, {
    customerEvidenceFileId: selection.artifactId,
    customerEvidenceFileVersion: selection.versionNo,
    customerEvidenceReferenceKey: selection.referenceKey
  })
  stagedEvidence.value = true
  await evidenceListRef.value?.refresh()
}
const clearEvidence = async (result: DetachedFileSlot) => {
  if (!canWrite.value || !plan.value || !draft.value) return
  evidenceSlot.detached(result)
  Object.assign(form, {
    customerEvidenceFileId: undefined,
    customerEvidenceFileVersion: undefined,
    customerEvidenceReferenceKey: undefined
  })
  stagedEvidence.value = true
}

const save = async () => {
  if (!canWrite.value || saving.value) return
  if (mode.value === 'INITIAL' && initialDurationHint(props.project)) {
    message.warning(initialDurationHint(props.project))
    return
  }
  const version = contextVersion
  if (!(await formRef.value?.validate())) return
  if (!canWrite.value || version !== contextVersion) return
  saving.value = true
  try {
    if (mode.value === 'INITIAL') {
      await DurationApi.createInitial(
        {
          projectId: props.project.id!,
          expectedProjectVersion: props.project.version || 0,
          ...durationPayload()
        },
        generateUUID()
      )
      if (version !== contextVersion) return
      message.success('项目工期已生效')
    } else if (mode.value === 'CREATE') {
      const created = await DurationApi.createChange(
        plan.value!.planId,
        {
          expectedProjectVersion: props.project.version || 0,
          ...durationPayload(),
          reasonType: form.reasonType,
          reasonDetail: form.reasonDetail || undefined
        },
        plan.value!.planVersion,
        generateUUID()
      )
      if (version !== contextVersion) return
      message.success('工期变更草稿已保存')
      // 附件（可选）在草稿创建后才可上传，统一停留在编辑态
      mode.value = 'EDIT'
      draft.value = created
      const snapshot = formStateFromChange(created)
      assign(snapshot)
      original.value = structuredClone(snapshot)
      evidenceSlot.reset(created.customerEvidenceFileId, created.customerEvidenceReferenceKey)
      stagedEvidence.value = false
      emit('saved')
      return
    } else {
      const patch = patchPayload()
      if (Object.keys(patch).length === 1) return message.warning('没有需要保存的变化')
      const updated = await DurationApi.patchChange(
        plan.value!.planId,
        draft.value!.changeId,
        patch,
        draft.value!.version
      )
      if (version !== contextVersion) return
      draft.value = updated
      original.value = structuredClone(toRaw(form))
      stagedEvidence.value = false
      message.success('工期变更草稿已更新')
    }
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

watch(
  () => visible.value || saving.value || stagedEvidence.value,
  (value) => emit('dirty-change', value),
  { immediate: true }
)
watch(
  () => props.project.id,
  () => {
    contextVersion++
    visible.value = false
    plan.value = undefined
    draft.value = undefined
    original.value = undefined
    evidenceSlot.reset()
    stagedEvidence.value = false
  },
  { flush: 'sync' }
)
watch(
  () => props.readonly,
  () => {
    contextVersion++
  },
  { flush: 'sync' }
)
onBeforeUnmount(() => {
  contextVersion++
})
defineExpose({
  openInitial,
  openCreate,
  openEdit,
  isDirty: () => visible.value || saving.value || stagedEvidence.value,
  discardChanges: () => {
    if (saving.value) return false
    contextVersion++
    visible.value = false
    stagedEvidence.value = false
    return true
  }
})
</script>

<style scoped lang="scss">
.form-alert {
  margin-bottom: 16px;
}

.date-grid {
  display: block;
}

.evidence-section {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.evidence-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.evidence-heading span {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.evidence-uploader {
  margin-top: 12px;
}

:deep(.el-date-editor),
:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
}

</style>
