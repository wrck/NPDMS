<template>
  <section aria-label="割接保障人员安排">
    <el-table :data="rows" empty-text="暂无保障安排">
      <el-table-column label="角色" width="180"><template #default="{ row, $index }">
        <el-select :data-testid="'support-role-' + $index" :model-value="row.roleCode" :disabled="readonly || patchApproved" aria-label="保障角色" @update:model-value="updateRole($index, $event)">
          <el-option v-for="(label, code) in roleLabels" :key="code" :label="label" :value="code" />
        </el-select>
      </template></el-table-column>
      <el-table-column label="姓名" min-width="195"><template #default="{ row, $index }">
        <el-select v-if="row.roleCode === 'CUSTOMER' && !contactReadonly" :data-testid="'support-customer-' + $index" :model-value="row.customerContactReference ? String(row.customerContactReference.contactId) : row.personName || undefined" filterable allow-create clearable placeholder="选择项目联系人或输入姓名" @update:model-value="selectCustomer($index, $event)">
          <el-option v-for="contact in contacts" :key="String(contact.contactId)" :label="contact.name" :value="String(contact.contactId)" />
        </el-select>
        <el-input v-else :data-testid="'support-name-' + $index" :model-value="row.personName" :disabled="contactReadonly" @update:model-value="update($index, 'personName', $event)" />
      </template></el-table-column>
      <el-table-column label="任务描述" min-width="240"><template #default="{ row, $index }"><el-input :model-value="row.dutyDescription" :disabled="readonly || patchApproved" @update:model-value="update($index, 'dutyDescription', $event)" /></template></el-table-column>
      <el-table-column label="联系电话" min-width="180"><template #default="{ row, $index }"><el-input :data-testid="'support-phone-' + $index" :model-value="row.phone" :disabled="contactReadonly || !!row.customerContactReference" @update:model-value="update($index, 'phone', $event)" /></template></el-table-column>
      <el-table-column label="到位时间" min-width="230"><template #default="{ row, $index }"><el-date-picker :data-testid="'support-arrival-' + $index" :model-value="row.arrivalTime || null" :disabled="contactReadonly" type="datetime" value-format="x" @update:model-value="updateArrival($index, $event)" /></template></el-table-column>
      <el-table-column v-if="!readonly && !patchApproved" label="操作" width="80" fixed="right"><template #default="{ $index }"><el-button :data-testid="'remove-support-' + $index" link type="danger" @click="remove($index)">删除</el-button></template></el-table-column>
      <el-table-column v-if="patchApproved" label="操作" width="120" fixed="right"><template #default="{ row }"><el-button v-if="row.arrangementId" :data-testid="'patch-support-' + row.roleCode" link type="primary" @click="$emit('patch', row)">更新联系人</el-button></template></el-table-column>
    </el-table>
    <el-button v-if="!readonly && !patchApproved" data-testid="add-support" class="add-support" plain @click="add">新增人员</el-button>
  </section>
</template>
<script setup lang="ts">
import type { CutoverPlanSupportArrangement, WireDateTime, ProjectContactChoice } from '@/api/pms/cutover'

const props = withDefaults(defineProps<{
  modelValue: CutoverPlanSupportArrangement[]
  readonly?: boolean
  patchApproved?: boolean
  contacts: ProjectContactChoice[]
}>(), { readonly: false, patchApproved: false })
const emit = defineEmits<{
  'update:modelValue': [value: CutoverPlanSupportArrangement[]]
  patch: [value: CutoverPlanSupportArrangement]
}>()

const selectCustomer = (index: number, id: string | undefined) => {
  const contact = props.contacts.find(c => String(c.contactId) === id)
  emit('update:modelValue', props.modelValue.map((row, rowIndex) => rowIndex === index ? {
    ...row, customerContactReference: contact ? { contactId: contact.contactId, contactVersion: contact.contactVersion } : null,
    personName: contact?.name || id?.trim() || '', phone: contact?.mobile || contact?.phone || ''
  } : row))
}
const rows = computed(() => props.modelValue)
const contactReadonly = computed(() => props.readonly && !props.patchApproved)
const roleLabels: Record<CutoverPlanSupportArrangement['roleCode'], string> = {
  CUSTOMER: '客户',
  DP_FIRST_LINE: '迪普一线工程师',
  DP_SECOND_LINE: '迪普二线工程师',
  DP_RND: '迪普研发'
}

const add = () => emit('update:modelValue', [...props.modelValue, {
  arrangementId: null, roleCode: 'CUSTOMER', personName: '', dutyDescription: '', phone: '', arrivalTime: 0,
  customerContactReference: null
}])
const remove = (index: number) => emit('update:modelValue', props.modelValue.filter((_, rowIndex) => rowIndex !== index))
const updateRole = (index: number, roleCode: CutoverPlanSupportArrangement['roleCode']) => {
  emit('update:modelValue', props.modelValue.map((row, rowIndex) => {
    if (rowIndex !== index || row.roleCode === roleCode) return row
    const customerChanged = row.roleCode === 'CUSTOMER' || roleCode === 'CUSTOMER'
    return { ...row, roleCode, customerContactReference: null,
      ...(customerChanged ? { personName: '', phone: '' } : {}) }
  }))
}

const update = (index: number, field: 'personName' | 'phone' | 'dutyDescription', value: string) => {
  const next = props.modelValue.map((row, rowIndex) => rowIndex === index ? { ...row, [field]: value } : row)
  emit('update:modelValue', next)
}
const updateArrival = (index: number, value: string | number | null) => {
  const epoch = value == null || value === '' ? 0 : Number(value)
  if (!Number.isSafeInteger(epoch) || epoch < 0) return
  const next = props.modelValue.map((row, rowIndex) => rowIndex === index
    ? { ...row, arrivalTime: epoch as WireDateTime }
    : row)
  emit('update:modelValue', next)
}
</script>

<style scoped>
.el-input, .el-select, .el-date-editor { width: 100%; }
.add-support { margin-top: 12px; }
</style>
