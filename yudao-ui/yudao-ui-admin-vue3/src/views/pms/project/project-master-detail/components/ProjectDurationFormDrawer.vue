<template>
  <el-drawer v-model="visible" :title="title" :size="drawerSize" destroy-on-close>
    <el-alert
      v-if="mode !== 'INITIAL'"
      title="变更先保存为草稿，提交审批后才会影响当前工期。"
      type="info"
      :closable="false"
      class="form-alert"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="计算口径" prop="calculationBasis">
        <el-radio-group v-model="form.calculationBasis" @change="resetDerivedField">
          <el-radio-button value="DATE_RANGE">起止日期</el-radio-button>
          <el-radio-button value="DURATION_FROM_START">起点 + 天数</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <div class="date-grid">
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
        <el-form-item label="变更原因" prop="reasonType">
          <el-select v-model="form.reasonType" placeholder="请选择变更原因">
            <el-option
              v-for="item in reasonOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原因说明" prop="reasonDetail">
          <el-input
            v-model="form.reasonDetail"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <section v-if="form.reasonType === 'CUSTOMER_DELAY'" class="evidence-section">
          <div class="evidence-heading">
            <div>
              <strong>客户延期依据</strong>
              <span>文件上传完成后仍需服务端校验与扫描，审批冻结具体版本。</span>
            </div>
            <el-tag v-if="draft?.customerEvidenceRequired" type="danger">必填</el-tag>
          </div>
          <el-alert
            v-if="mode === 'CREATE'"
            title="先保存工期变更草稿，随后即可在当前抽屉上传客户依据。"
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
              editable
              @loaded="evidenceSlot.loaded"
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
                Boolean(evidenceSlot.state.artifactId) &&
                evidenceSlot.state.referenceVersion === undefined
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
      <el-button type="primary" :loading="saving" @click="save">
        {{ mode === 'INITIAL' ? '保存并生效' : '保存草稿' }}
      </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import { getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import { PmsFileReferenceList, PmsFileUploader } from '@/components/PmsFileArtifact'
import { useFileSlotState } from '@/components/PmsFileArtifact/useFileSlotState'
import type { DetachedFileSlot, FileSelection } from '@/components/PmsFileArtifact/types'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import type {
  ConstructionPlanChangeVO,
  ConstructionPlanVO,
  PatchDurationChangeReqVO
} from '@/api/pms/engineering/construction-plan'
import {
  formStateFromChange,
  reconcilePatchResponseLoss,
  type DurationChangeFormState
} from './durationChangeFormState'

const props = defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ saved: [] }>()
const message = useMessage()
const narrow = useMediaQuery('(max-width: 767px)')
const drawerSize = computed(() => (narrow.value ? '100%' : '560px'))
const visible = ref(false)
const saving = ref(false)
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
const reasonOptions = computed(() => getStrDictOptions('pms_duration_change_reason_type'))

type FormModel = DurationChangeFormState

const emptyForm = (): FormModel => ({
  calculationBasis: 'DATE_RANGE',
  startDate: '',
  endDate: '',
  durationDays: undefined,
  reasonType: '',
  reasonDetail: '',
  customerEvidenceFileId: undefined,
  customerEvidenceFileVersion: undefined,
  customerEvidenceReferenceKey: undefined
})
const form = reactive<FormModel>(emptyForm())
const evidenceReferenceKey = 'customer-delay'
const evidenceListRef = ref<InstanceType<typeof PmsFileReferenceList>>()
const evidenceSlot = useFileSlotState()
const activeEvidenceReferenceKey = computed(
  () => evidenceSlot.state.referenceKey || evidenceReferenceKey
)
const rules: FormRules<FormModel> = {
  calculationBasis: [{ required: true, message: '请选择计算口径' }],
  startDate: [{ required: true, message: '请选择开始日期' }],
  endDate: [{ required: true, message: '请选择结束日期' }],
  durationDays: [{ required: true, message: '请输入自然日天数' }],
  reasonType: [{ required: true, message: '请选择变更原因' }]
}

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

const openInitial = () => {
  mode.value = 'INITIAL'
  plan.value = undefined
  draft.value = undefined
  original.value = undefined
  evidenceSlot.reset()
  assign({})
  visible.value = true
}
const openCreate = (value: ConstructionPlanVO) => {
  mode.value = 'CREATE'
  plan.value = value
  draft.value = undefined
  original.value = undefined
  evidenceSlot.reset()
  assign({ ...value.currentRevision, reasonType: '', reasonDetail: '' })
  visible.value = true
}
const openEdit = (value: ConstructionPlanVO, change: ConstructionPlanChangeVO) => {
  mode.value = 'EDIT'
  plan.value = value
  draft.value = change
  const snapshot = formStateFromChange(change)
  assign(snapshot)
  original.value = structuredClone(snapshot)
  evidenceSlot.reset(change.customerEvidenceFileId, change.customerEvidenceReferenceKey)
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

const saveEvidence = async (selection: FileSelection) => {
  if (!plan.value || !draft.value) return
  evidenceSlot.uploaded(selection)
  Object.assign(form, {
    customerEvidenceFileId: selection.artifactId,
    customerEvidenceFileVersion: selection.versionNo,
    customerEvidenceReferenceKey: selection.referenceKey
  })
  try {
    const updated = await DurationApi.patchChange(
      plan.value.planId,
      draft.value.changeId,
      {
        expectedProjectVersion: props.project.version || 0,
        customerEvidenceFileId: selection.artifactId,
        customerEvidenceFileVersion: selection.versionNo,
        customerEvidenceReferenceKey: selection.referenceKey
      },
      draft.value.version
    )
    draft.value = updated
    if (original.value)
      Object.assign(original.value, {
        customerEvidenceFileId: selection.artifactId,
        customerEvidenceFileVersion: selection.versionNo,
        customerEvidenceReferenceKey: selection.referenceKey
      })
    emit('saved')
  } catch {
    const recovered = await recoverDraftAfterPatchLoss()
    message.warning(
      recovered
        ? '文件已完成校验，已读取最新草稿；请点击“保存草稿”完成剩余变化'
        : '文件已完成校验，草稿状态读取失败，请刷新后重试'
    )
  } finally {
    await evidenceListRef.value?.refresh()
  }
}
const clearEvidence = async (result: DetachedFileSlot) => {
  if (!plan.value || !draft.value) return
  evidenceSlot.detached(result)
  Object.assign(form, {
    customerEvidenceFileId: undefined,
    customerEvidenceFileVersion: undefined,
    customerEvidenceReferenceKey: undefined
  })
  try {
    const updated = await DurationApi.patchChange(
      plan.value.planId,
      draft.value.changeId,
      {
        expectedProjectVersion: props.project.version || 0,
        customerEvidenceFileId: null,
        customerEvidenceFileVersion: null,
        customerEvidenceReferenceKey: null
      },
      draft.value.version
    )
    draft.value = updated
    if (original.value)
      Object.assign(original.value, {
        customerEvidenceFileId: undefined,
        customerEvidenceFileVersion: undefined,
        customerEvidenceReferenceKey: undefined
      })
    emit('saved')
  } catch {
    const recovered = await recoverDraftAfterPatchLoss()
    message.warning(
      recovered
        ? '文件引用已解除，已读取最新草稿；请点击“保存草稿”完成剩余变化'
        : '文件引用已解除，草稿状态读取失败，请刷新后重试'
    )
  }
}

const recoverDraftAfterPatchLoss = async () => {
  if (!plan.value || !draft.value) return false
  const local = structuredClone(toRaw(form))
  try {
    const current = await DurationApi.getChange(plan.value.planId, draft.value.changeId)
    const recovered = reconcilePatchResponseLoss(local, current)
    draft.value = recovered.current
    assign(recovered.form)
    original.value = recovered.baseline
    return true
  } catch {
    return false
  }
}

const save = async () => {
  if (!(await formRef.value?.validate())) return
  saving.value = true
  try {
    if (mode.value === 'INITIAL') {
      await DurationApi.createInitial(
        {
          projectId: props.project.id!,
          expectedProjectVersion: props.project.version || 0,
          ...durationPayload()
        },
        crypto.randomUUID()
      )
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
        crypto.randomUUID()
      )
      message.success('工期变更草稿已保存')
      if (form.reasonType === 'CUSTOMER_DELAY') {
        mode.value = 'EDIT'
        draft.value = created
        const snapshot = formStateFromChange(created)
        assign(snapshot)
        original.value = structuredClone(snapshot)
        evidenceSlot.reset(created.customerEvidenceFileId, created.customerEvidenceReferenceKey)
        emit('saved')
        return
      }
    } else {
      const patch = patchPayload()
      if (Object.keys(patch).length === 1) return message.warning('没有需要保存的变化')
      await DurationApi.patchChange(
        plan.value!.planId,
        draft.value!.changeId,
        patch,
        draft.value!.version
      )
      message.success('工期变更草稿已更新')
    }
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

defineExpose({ openInitial, openCreate, openEdit })
</script>

<style scoped lang="scss">
.form-alert {
  margin-bottom: 16px;
}

.date-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
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

@media (width <= 767px) {
  .date-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
