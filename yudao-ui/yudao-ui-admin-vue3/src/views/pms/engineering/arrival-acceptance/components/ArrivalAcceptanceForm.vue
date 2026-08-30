<template>
  <Dialog v-model="visible" :title="title" width="min(960px, 94vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="项目" prop="projectId">
          <PmsEntitySelect
            v-model="form.projectId"
            :api="ProjectApi.getProjectPage"
            label-field="projectName"
            value-field="id"
            query-field="projectName"
            placeholder="选择本人可编辑项目"
            :disabled="Boolean(detail)"
            data-testid="project-id"
          />
        </el-form-item>
        <el-form-item label="业务批次码" prop="batchCode">
          <el-input
            v-model="form.batchCode"
            :disabled="Boolean(detail)"
            maxlength="64"
            data-testid="batch-code"
          />
        </el-form-item>
        <el-form-item label="物流单号" prop="logisticsNo">
          <el-input v-model="form.logisticsNo" maxlength="128" data-testid="logistics-no" />
        </el-form-item>
        <el-form-item label="签收人" prop="signerName">
          <el-input v-model="form.signerName" maxlength="128" data-testid="signer-name" />
        </el-form-item>
        <el-form-item label="到货时间" prop="arrivedAt">
          <el-date-picker
            v-model="form.arrivedAt"
            type="datetime"
            value-format="x"
            class="!w-full"
            data-testid="arrival-time"
          />
        </el-form-item>
        <el-form-item v-if="!detail" label="应到范围版本" prop="expectedDeliveryScopeVersion">
          <el-input
            v-model="form.expectedDeliveryScopeVersion"
            inputmode="numeric"
            data-testid="delivery-scope-version"
          />
        </el-form-item>
      </div>
      <ArrivalLineEditor v-if="detail" v-model="form.lines" :editable="true" />
      <el-form-item
        v-if="correction"
        label="纠正原因"
        prop="correctionReason"
        class="correction-reason"
      >
        <el-input
          v-model="form.correctionReason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          data-testid="correction-reason"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" data-testid="save-arrival" @click="submit"
        >保存</el-button
      >
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type {
  ArrivalDetail,
  ArrivalDraftLine,
  CreateArrivalRequest,
  PatchArrivalRequest,
  WireLong
} from '@/api/pms/engineering/arrival-acceptance'
import * as ProjectApi from '@/api/pms/project/projects'
import {
  pickerValueToWireDateTime,
  wireDateTimeToPickerValue
} from '../arrivalAcceptanceInteraction'
import ArrivalLineEditor from './ArrivalLineEditor.vue'

const props = withDefaults(
  defineProps<{ modelValue: boolean; detail: ArrivalDetail | null; correction?: boolean }>(),
  { correction: false }
)
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  create: [value: CreateArrivalRequest]
  patch: [value: PatchArrivalRequest]
  correct: [value: { patch: PatchArrivalRequest; reason: string }]
}>()
const title = computed(() =>
  props.correction ? '创建信息纠正后继' : props.detail ? '编辑到货草稿' : '创建到货草稿'
)
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const formRef = ref()
const saving = ref(false)
const form = reactive<{
  projectId: WireLong | ''
  batchCode: string
  logisticsNo: string
  signerName: string
  arrivedAt: number | string
  expectedDeliveryScopeVersion: WireLong | ''
  lines: ArrivalDraftLine[]
  correctionReason: string
}>({
  projectId: '',
  batchCode: '',
  logisticsNo: '',
  signerName: '',
  arrivedAt: '',
  expectedDeliveryScopeVersion: '',
  lines: [],
  correctionReason: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  batchCode: [{ required: true, message: '请输入批次码' }],
  logisticsNo: [{ required: true, message: '请输入物流单号' }],
  signerName: [{ required: true, message: '请输入签收人' }],
  arrivedAt: [{ required: true, message: '请选择到货时间' }],
  expectedDeliveryScopeVersion: [{ required: true, message: '请输入应到范围版本' }],
  correctionReason: [{ required: true, message: '请输入纠正原因' }]
}

const toDraftLine = (line: ArrivalDetail['currentLines'][number]): ArrivalDraftLine =>
  line.scopeType === 'DEVICE'
    ? {
        scopeType: 'DEVICE',
        lineId: line.id,
        expectedLineVersion: line.version,
        deviceId: line.deviceId!,
        received: line.status === 'ACCEPTED'
      }
    : {
        scopeType: 'ORDER_MODEL_QUANTITY',
        lineId: line.id,
        expectedLineVersion: line.version,
        orderLineId: line.orderLineId!,
        productCode: line.productCode,
        modelCode: line.modelCode,
        acceptedQuantity: line.acceptedQuantity || 0,
        unitCode: line.unitCode || ''
      }

watch(
  () => [props.modelValue, props.detail] as const,
  ([opened, detail]) => {
    if (!opened) return
    Object.assign(form, {
      projectId: detail?.projectId || '',
      batchCode: detail?.batchCode || '',
      logisticsNo: detail?.logisticsNo || '',
      signerName: detail?.signerName || '',
      arrivedAt: wireDateTimeToPickerValue(detail?.arrivedAt),
      expectedDeliveryScopeVersion: detail?.deliveryScopeVersion || '',
      lines: detail?.currentLines.map(toDraftLine) || [],
      correctionReason: ''
    })
  },
  { immediate: true }
)

const submit = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (props.detail) {
      const patch = {
        logisticsNo: form.logisticsNo,
        arrivedAt: pickerValueToWireDateTime(form.arrivedAt),
        signerName: form.signerName,
        lines: form.lines
      }
      if (props.correction) emit('correct', { patch, reason: form.correctionReason.trim() })
      else emit('patch', patch)
    } else {
      emit('create', {
        projectId: form.projectId,
        batchCode: form.batchCode.trim(),
        logisticsNo: form.logisticsNo.trim(),
        arrivedAt: pickerValueToWireDateTime(form.arrivedAt),
        signerName: form.signerName.trim(),
        expectedDeliveryScopeVersion: form.expectedDeliveryScopeVersion
      })
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.correction-reason {
  margin-top: 16px;
}

@media (width <= 767px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
