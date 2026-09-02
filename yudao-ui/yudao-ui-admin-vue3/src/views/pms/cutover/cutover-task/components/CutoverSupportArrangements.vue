<template>
  <section class="support-grid" aria-label="割接保障安排">
    <article v-for="(row, index) in rows" :key="row.roleCode" class="support-card">
      <header>
        <strong>{{ roleLabels[row.roleCode] }}</strong>
        <el-tag v-if="row.arrangementId" size="small">已保存</el-tag>
      </header>
      <el-form label-position="top">
        <el-form-item label="姓名">
          <el-input
            :data-testid="`support-name-${row.roleCode}`"
            :model-value="row.personName"
            :disabled="contactReadonly"
            @update:model-value="update(index, 'personName', $event)"
          />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input
            :data-testid="`support-phone-${row.roleCode}`"
            :model-value="row.phone"
            :disabled="contactReadonly"
            @update:model-value="update(index, 'phone', $event)"
          />
        </el-form-item>
        <el-form-item label="职责说明">
          <el-input
            :model-value="row.dutyDescription"
            :disabled="readonly || patchApproved"
            type="textarea"
            @update:model-value="update(index, 'dutyDescription', $event)"
          />
        </el-form-item>
        <el-form-item label="到场时间">
          <el-date-picker
            :data-testid="`support-arrival-${row.roleCode}`"
            :model-value="row.arrivalTime"
            :disabled="contactReadonly"
            type="datetime"
            value-format="x"
            @update:model-value="updateArrival(index, $event)"
          />
        </el-form-item>
      </el-form>
      <el-button
        v-if="patchApproved && row.arrangementId"
        :data-testid="`patch-support-${row.roleCode}`"
        type="primary"
        @click="$emit('patch', row)"
      >更新批准联系人</el-button>
    </article>
  </section>
</template>

<script setup lang="ts">
import type { CutoverPlanSupportArrangement, WireDateTime } from '@/api/pms/cutover/cutover-task'

const props = withDefaults(defineProps<{
  modelValue: CutoverPlanSupportArrangement[]
  readonly?: boolean
  patchApproved?: boolean
}>(), { readonly: false, patchApproved: false })
const emit = defineEmits<{
  'update:modelValue': [value: CutoverPlanSupportArrangement[]]
  patch: [value: CutoverPlanSupportArrangement]
}>()

const rows = computed(() => props.modelValue)
const contactReadonly = computed(() => props.readonly && !props.patchApproved)
const roleLabels: Record<CutoverPlanSupportArrangement['roleCode'], string> = {
  CUSTOMER: '客户方',
  DP_FIRST_LINE: '数通一线',
  DP_SECOND_LINE: '数通二线',
  DP_RND: '数通研发'
}

const update = (index: number, field: 'personName' | 'phone' | 'dutyDescription', value: string) => {
  const next = props.modelValue.map((row, rowIndex) => rowIndex === index ? { ...row, [field]: value } : row)
  emit('update:modelValue', next)
}
const updateArrival = (index: number, value: string | number) => {
  const epoch = typeof value === 'number' ? value : Number(value)
  if (!Number.isSafeInteger(epoch) || epoch <= 0) return
  const next = props.modelValue.map((row, rowIndex) => rowIndex === index
    ? { ...row, arrivalTime: epoch as WireDateTime }
    : row)
  emit('update:modelValue', next)
}
</script>

<style scoped>
.support-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.support-card { min-width: 0; padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; }
.support-card header { display: flex; justify-content: space-between; margin-bottom: 12px; }
@media (max-width: 767px) { .support-grid { grid-template-columns: 1fr; } }
</style>
