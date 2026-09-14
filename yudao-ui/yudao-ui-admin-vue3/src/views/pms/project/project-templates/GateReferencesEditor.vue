<template>
  <section aria-label="门禁引用配置">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p class="hint">多个引用默认全部满足。流程版本在此选择并随计划冻结，保存草稿不会发起流程。</p>
    <div v-for="reference in gate.references" :key="rowKey(reference)" class="gate-reference">
      <div class="reference-heading">
        <el-select :model-value="reference.refType" aria-label="门禁引用类型" :disabled="!editable"
          @update:model-value="changeType(reference, $event)">
          <el-option v-for="type in types" :key="type.value" :value="type.value" :label="type.label" />
        </el-select>
        <el-button link type="danger" :disabled="!editable" @click="remove(reference)">移除引用</el-button>
      </div>
      <ApprovalDefinitionSelect v-if="isProcess(reference)" :key="`${rowKey(reference)}:${reference.refType}`"
        :binding="processBinding(reference)" :readonly="!editable" :binding-permission="bindingPermission"
        @choose="chooseProcess(reference, $event)" />
      <el-input v-else :model-value="reference.refCode" aria-label="门禁引用编码" placeholder="引用编码"
        :disabled="!editable" @update:model-value="setCode(reference, $event)" />
    </div>
    <el-button :disabled="!editable" @click="add">添加门禁引用</el-button>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import type { DesignerGateNode, GateRef, WorkBindingSpec } from '@/api/pms/project/project-templates'
import ApprovalDefinitionSelect, { type ApprovalDefinitionChoice } from './ApprovalDefinitionSelect.vue'

const props = defineProps<{ gate: DesignerGateNode; readonly?: boolean; consumers?: string[];
  bindingPermission?: 'pms:project-template:update' | 'pms:project-plan:manage' }>()
const types = [
  { value: 'TASK', label: '任务' }, { value: 'MILESTONE', label: '里程碑' },
  { value: 'DELIVERABLE', label: '交付件' }, { value: 'STATE', label: '阶段状态' },
  { value: 'APPROVAL', label: '审批结果' }, { value: 'PROCESS', label: '流程结果' }
]
const authorized = () => !props.readonly && hasPermission([props.bindingPermission ?? 'pms:project-template:update'])
const editable = computed(authorized)
const error = ref('')
const keys = new WeakMap<GateRef, number>()
let nextKey = 0, disposed = false
const rowKey = (reference: GateRef) => {
  if (!keys.has(reference)) keys.set(reference, ++nextKey)
  return keys.get(reference)!
}
const isProcess = (reference: GateRef) => ['APPROVAL', 'PROCESS'].includes(reference.refType)
const processBinding = (reference: GateRef): WorkBindingSpec => ({ type: 'APPROVAL',
  approvalDefinitionKey: reference.refCode, parameters: { processDefinitionId: reference.refVersion ?? '' } })
const active = (reference: GateRef) => !disposed && authorized() && props.gate.references.includes(reference)
const add = () => { if (!disposed && authorized()) props.gate.references.push({ refType: 'TASK', refCode: '' }) }
const remove = (reference: GateRef) => {
  if (active(reference)) props.gate.references.splice(props.gate.references.indexOf(reference), 1)
}
const changeType = (reference: GateRef, type: string) => {
  if (!active(reference) || reference.refType === type || !types.some(item => item.value === type)) return
  reference.refType = type; reference.refCode = ''; delete reference.refVersion; error.value = ''
}
const setCode = (reference: GateRef, code: string) => {
  if (active(reference) && !isProcess(reference)) reference.refCode = code
}
const chooseProcess = async (reference: GateRef, definition: ApprovalDefinitionChoice) => {
  if (!active(reference) || !isProcess(reference)) return
  const gate = props.gate, type = reference.refType
  const duplicate = () => gate.references.some(other => other !== reference && other.refType === type && other.refCode === definition.key)
  if (duplicate()) { error.value = '本门禁已经引用该流程；请修改原引用，不能重复添加。'; return }
  try {
    const affected = props.consumers?.length ? `直接引用此门禁的任务：${props.consumers.join('、')}。` : ''
    await ElMessageBox.confirm(`门禁“${gate.name}”将引用 ${definition.name} 第${definition.version}版。${affected}保存草稿不会发起流程，也不会覆盖在用版本。`,
      '配置门禁流程', { type: 'warning', confirmButtonText: '确认配置', cancelButtonText: '取消' })
  } catch { return }
  if (props.gate !== gate || !active(reference) || reference.refType !== type) return
  if (duplicate()) { error.value = '本门禁已经引用该流程，当前选择未应用。'; return }
  reference.refCode = definition.key; reference.refVersion = definition.id; error.value = ''
}
onBeforeUnmount(() => { disposed = true })
</script>

<style scoped>
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.gate-reference { margin: 12px 0; padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); }
.reference-heading { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 8px; }
.reference-heading :deep(.el-select) { flex: 1; min-width: 120px; }
</style>
