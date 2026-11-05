<template>
  <section aria-label="割接保障人员安排">
    <p class="cut-help">客户、迪普一线工程师必填；二线、研发可按需添加。</p>
    <el-table :data="rows" empty-text="暂无保障安排">
      <el-table-column label="角色" width="180"><template #default="{ row, $index }">
        <el-select :data-testid="'support-role-' + $index" :model-value="row.roleCode" :disabled="readonly || patchApproved" aria-label="保障角色" @update:model-value="updateRole($index, $event)">
          <el-option v-for="(label, code) in roleLabels" :key="code" :label="label" :value="code" />
        </el-select>
      </template></el-table-column>
      <el-table-column label="姓名" min-width="195"><template #default="{ row, $index }">
        <UserSelect
          v-if="!contactReadonly && row.roleCode !== 'CUSTOMER'"
          :data-testid="'support-system-' + $index"
          :model-value="row.personReference ? referenceKey(row.personReference) : row.personName || undefined" :current-label="supportCurrentLabel(row)"
          :scoped-source="supportSource(row, $index)" option-label="nickname-account" placeholder="按姓名、用户名或邮箱搜索"
          @update:model-value="selectPerson($index, $event == null ? undefined : String($event))" />
        <el-select
v-else-if="!contactReadonly" :data-testid="(row.roleCode === 'CUSTOMER' ? 'support-customer-' : 'support-system-') + $index"
          :model-value="row.personReference ? referenceKey(row.personReference) : row.personName || undefined"
          filterable :allow-create="row.roleCode === 'CUSTOMER'" clearable
          :placeholder="row.roleCode === 'CUSTOMER' ? '选择项目成员或输入姓名' : '选择对应角色的系统用户'"
          @update:model-value="selectPerson($index, $event)">
          <el-option
v-if="row.personReference && !(personChoices[row.roleCode] || []).some(person => referenceKey(person.reference) === referenceKey(row.personReference!))"
            :label="personChoices[row.roleCode] ? `${row.personName}（原选择，请重新选择）` : row.personName" :value="referenceKey(row.personReference)" disabled />
          <el-option v-for="person in personChoices[row.roleCode] || []" :key="referenceKey(person.reference)" :label="person.name" :value="referenceKey(person.reference)" />
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
import UserSelect from '@/views/system/user/components/UserSelect.vue'
import { systemUserSelection } from '@/api/system/user/selection'

import type { CutoverPlanSupportArrangement, WireDateTime, SupportPersonChoice, SupportPersonReference } from '@/api/pms/cutover'

const props = withDefaults(defineProps<{
  modelValue: CutoverPlanSupportArrangement[]
  readonly?: boolean
  patchApproved?: boolean
  personChoices?: Partial<Record<CutoverPlanSupportArrangement['roleCode'], SupportPersonChoice[]>>
}>(), { readonly: false, patchApproved: false, personChoices: () => ({}) })
const emit = defineEmits<{
  'update:modelValue': [value: CutoverPlanSupportArrangement[]]
  patch: [value: CutoverPlanSupportArrangement]
}>()

const referenceKey = (reference: SupportPersonReference) => `${reference.sourceCode}:${reference.id}:${reference.version}`
const selectPerson = (index: number, value: string | undefined) => {
  const row = props.modelValue[index]
  const person = props.personChoices[row.roleCode]?.find(person => referenceKey(person.reference) === value)
  if (row.roleCode !== 'CUSTOMER' && value && !person) return
  emit('update:modelValue', props.modelValue.map((current, rowIndex) => rowIndex === index ? {
    ...current, customerContactReference: null, personReference: person?.reference || null,
    personName: person?.name || (row.roleCode === 'CUSTOMER' ? value?.trim() || '' : ''), phone: person?.phone || ''
  } : current))
}
const supportSource = (row: CutoverPlanSupportArrangement, index: number) => {
  const choices = props.personChoices[row.roleCode] || []
  return systemUserSelection.fromCandidates(
    `cutover-support:${index}:${row.roleCode}:${choices.map(person => referenceKey(person.reference)).join(',')}`,
    choices.map(person => ({ id: referenceKey(person.reference), nickname: person.name,
      username: person.username || undefined, email: person.email || undefined }))
  )
}
const supportCurrentLabel = (row: CutoverPlanSupportArrangement) => {
  const choices = props.personChoices[row.roleCode]
  return row.personReference && choices && !choices.some(person => referenceKey(person.reference) === referenceKey(row.personReference!))
    ? `${row.personName}（原选择，请重新选择）` : row.personName
}
const rows = computed(() => props.modelValue)
const contactReadonly = computed(() => props.readonly && !props.patchApproved)
watch(() => [props.modelValue, props.personChoices, props.readonly, props.patchApproved], () => {
  if (props.readonly || props.patchApproved) return
  let changed = false
  const next = props.modelValue.map(row => {
    const reference = row.personReference
    if (!reference) return row
    const current = props.personChoices[row.roleCode]?.find(person =>
      person.reference.sourceCode === reference.sourceCode && String(person.reference.id) === String(reference.id)
      && person.name === row.personName)
    if (!current || String(current.reference.version) === String(reference.version)) return row
    changed = true
    return { ...row, personReference: { ...current.reference } }
  })
  if (changed) emit('update:modelValue', next)
}, { immediate: true, deep: true })
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
    return { ...row, roleCode, customerContactReference: null, personReference: null, personName: '', phone: '' }
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
