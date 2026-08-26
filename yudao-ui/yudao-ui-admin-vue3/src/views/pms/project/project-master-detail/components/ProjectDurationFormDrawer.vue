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
          <el-input-number v-model="form.durationDays" :min="1" :max="36500" controls-position="right" />
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
          <el-input v-model="form.reasonDetail" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-alert
          v-if="form.reasonType === 'CUSTOMER_DELAY'"
          title="客户依据文件能力尚未接入；该原因命中材料必填规则时，服务端会失败关闭。"
          type="warning"
          :closable="false"
        />
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
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import type {
  ConstructionPlanChangeVO,
  ConstructionPlanVO,
  DurationCalculationBasis,
  PatchDurationChangeReqVO
} from '@/api/pms/engineering/construction-plan'

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
  mode.value === 'INITIAL' ? '录入项目工期' : mode.value === 'CREATE' ? '新建工期变更' : '编辑工期变更草稿'
)
const reasonOptions = computed(() => getStrDictOptions('pms_duration_change_reason_type'))

interface FormModel {
  calculationBasis: DurationCalculationBasis
  startDate: string
  endDate?: string
  durationDays?: number
  reasonType: string
  reasonDetail: string
}

const emptyForm = (): FormModel => ({
  calculationBasis: 'DATE_RANGE', startDate: '', endDate: '', durationDays: undefined,
  reasonType: '', reasonDetail: ''
})
const form = reactive<FormModel>(emptyForm())
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
  mode.value = 'INITIAL'; plan.value = undefined; draft.value = undefined; original.value = undefined
  assign({}); visible.value = true
}
const openCreate = (value: ConstructionPlanVO) => {
  mode.value = 'CREATE'; plan.value = value; draft.value = undefined; original.value = undefined
  assign({ ...value.currentRevision, reasonType: '', reasonDetail: '' }); visible.value = true
}
const openEdit = (value: ConstructionPlanVO, change: ConstructionPlanChangeVO) => {
  mode.value = 'EDIT'; plan.value = value; draft.value = change
  const snapshot: FormModel = {
    ...change.candidateRevision,
    reasonType: change.reasonType,
    reasonDetail: change.reasonDetail || ''
  }
  assign(snapshot); original.value = structuredClone(snapshot); visible.value = true
}

const patchPayload = (): PatchDurationChangeReqVO => {
  const before = original.value!
  const patch: PatchDurationChangeReqVO = { expectedProjectVersion: props.project.version || 0 }
  if (form.calculationBasis !== before.calculationBasis) patch.calculationBasis = form.calculationBasis
  if (form.startDate !== before.startDate) patch.startDate = form.startDate
  if ((form.endDate || null) !== (before.endDate || null)) patch.endDate = form.endDate || null
  if ((form.durationDays || null) !== (before.durationDays || null)) patch.durationDays = form.durationDays || null
  if (form.reasonType !== before.reasonType) patch.reasonType = form.reasonType
  if (form.reasonDetail !== before.reasonDetail) patch.reasonDetail = form.reasonDetail || null
  return patch
}

const save = async () => {
  if (!(await formRef.value?.validate())) return
  saving.value = true
  try {
    if (mode.value === 'INITIAL') {
      await DurationApi.createInitial({
        projectId: props.project.id!, expectedProjectVersion: props.project.version || 0, ...durationPayload()
      }, crypto.randomUUID())
      message.success('项目工期已生效')
    } else if (mode.value === 'CREATE') {
      await DurationApi.createChange(plan.value!.planId, {
        expectedProjectVersion: props.project.version || 0,
        ...durationPayload(), reasonType: form.reasonType, reasonDetail: form.reasonDetail || undefined
      }, plan.value!.planVersion, crypto.randomUUID())
      message.success('工期变更草稿已保存')
    } else {
      const patch = patchPayload()
      if (Object.keys(patch).length === 1) return message.warning('没有需要保存的变化')
      await DurationApi.patchChange(plan.value!.planId, draft.value!.changeId, patch, draft.value!.version)
      message.success('工期变更草稿已更新')
    }
    visible.value = false
    emit('saved')
  } finally { saving.value = false }
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
