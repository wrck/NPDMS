<template>
  <el-form label-width="150px" :disabled="disabled">
    <template v-if="kind === 'STAGE' || kind === 'TASK'">
      <el-form-item label="名称"><el-input v-model="model.payload.name" /></el-form-item>
      <template v-if="kind === 'STAGE'">
        <el-form-item label="阶段编码"><el-select v-model="model.payload.stageCode" placeholder="显式选择，不补齐其他阶段"><el-option v-for="code in stageCodes" :key="code" :value="code" :label="code" /></el-select></el-form-item>
        <el-form-item v-for="flag in flags" :key="flag.key" :label="flag.label"><el-radio-group v-model="model.payload[flag.key]"><el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio></el-radio-group></el-form-item>
      </template>
      <el-form-item v-for="slot in executionSlots" :key="slot.key" :label="slot.label">
        <DefinitionSelect :model-value="reference(slot.key)" :kind="slot.kind" :disabled="disabled" @update:model-value="setReference(slot.key, $event)" />
      </el-form-item>
    </template>
    <template v-else-if="kind === 'WORK_BINDING'">
      <el-form-item label="绑定类型"><el-select :model-value="model.payload.bindingType" @update:model-value="changeBinding"><el-option v-for="type in bindingTypes" :key="type" :value="type" :label="type" /></el-select></el-form-item>
      <el-form-item label="实例解析策略"><el-select v-model="model.payload.instanceResolutionStrategy"><el-option v-for="strategy in strategies" :key="strategy" :value="strategy" :label="strategy" /></el-select></el-form-item>
      <el-alert title="解析策略只声明Owner入口；视图查询、预检和挂载不创建实体，也不代表业务完成。" type="info" :closable="false" class="mb-12px" />
      <template v-if="!native">
        <el-form-item label="注册业务视图"><BusinessViewSelect v-model="model.payload.businessViewRevisionId" :disabled="disabled" @selected="selectView" /></el-form-item>
        <el-form-item label="目标Owner Context"><el-input v-model="model.payload.targetContextCode" placeholder="须与注册契约兼容，不改变Owner" /></el-form-item>
        <el-form-item label="目标对象类型"><el-input v-model="model.payload.targetObjectType" /></el-form-item>
        <el-form-item label="目标稳定键"><el-input v-model="model.payload.targetObjectKey" placeholder="受控稳定上下文键，不是脚本或URL" /></el-form-item>
        <el-form-item label="上下文映射">
          <div class="w-full">
            <div v-for="(row, index) in mappings" :key="index" class="form-row">
              <el-input v-model="row.key" placeholder="目标上下文键" @change="syncMapping" /><el-input v-model="row.value" placeholder="来源上下文键" @change="syncMapping" />
              <el-button v-if="!disabled" link type="danger" @click="mappings.splice(index, 1); syncMapping()">移除</el-button>
            </div>
            <el-button v-if="!disabled" @click="mappings.push({ key: '', value: '' })">新增映射</el-button>
            <div v-if="mappingError" role="alert" class="text-danger">{{ mappingError }}</div>
          </div>
        </el-form-item>
      </template>
    </template>
    <template v-else-if="kind === 'COMPLETION_RULE'">
      <el-form-item label="规则组合"><RuleDecisionDesigner v-model="model.payload" :disabled="disabled" /></el-form-item>
    </template>
    <template v-else-if="kind === 'PERMISSION_POLICY'">
      <el-alert title="只声明所需操作编码，不授予权限。服务端仍重验租户、项目树、Owner对象、字段与当前状态。" type="info" :closable="false" class="mb-12px" />
      <el-form-item label="所需操作编码"><el-select v-model="model.payload.requiredActions" multiple filterable allow-create default-first-option placeholder="输入既有Owner操作编码并确认" /></el-form-item>
    </template>
    <template v-else-if="kind === 'DELIVERABLE'">
      <el-form-item label="归属范围"><el-radio-group v-model="model.payload.scope"><el-radio value="STAGE">阶段</el-radio><el-radio value="TASK">任务</el-radio></el-radio-group></el-form-item>
      <el-form-item label="交付件类型"><el-input v-model="model.payload.deliverableType" placeholder="Owner定义的交付件类型" /></el-form-item>
      <el-form-item label="必须提交"><el-radio-group v-model="model.payload.required"><el-radio :value="true">必传</el-radio><el-radio :value="false">选传</el-radio></el-radio-group></el-form-item>
      <el-form-item label="最少数量"><el-input-number v-model="model.payload.minimumQuantity" :min="model.payload.required ? 1 : 0" :precision="0" /><span class="ml-8px">必传不可用零数量绕过</span></el-form-item>
      <el-form-item label="允许来源"><el-select v-model="model.payload.allowedSources" multiple filterable allow-create default-first-option placeholder="输入已定义来源编码并确认" /></el-form-item>
      <el-form-item label="产出类型"><el-input v-model="model.payload.outputType" placeholder="文件或业务产出类型（Owner契约）" /></el-form-item>
      <el-form-item label="确认 / 审批要求"><RuleDecisionDesigner v-model="model.payload.confirmationRule" :disabled="disabled" /></el-form-item>
      <el-form-item label="完成规则修订"><DefinitionSelect :model-value="reference('completionRule')" kind="COMPLETION_RULE" :disabled="disabled" @update:model-value="setReference('completionRule', $event)" /></el-form-item>
    </template>
    <template v-else-if="kind === 'GATE'">
      <el-form-item label="门禁类型"><el-radio-group v-model="model.payload.gateType"><el-radio value="ENTRY">准入</el-radio><el-radio value="EXIT">准出</el-radio></el-radio-group></el-form-item>
      <el-form-item label="Owner事实引用">
        <div class="w-full">
          <div v-for="(row, index) in model.payload.references" :key="index" class="form-row">
            <el-select v-model="row.refType"><el-option v-for="type in gateTypes" :key="type" :value="type" :label="type" /></el-select>
            <el-select v-if="row.refType === 'STATE'" v-model="row.refCode"><el-option v-for="code in stageCodes" :key="code" :value="`${code}_COMPLETED`" :label="`${code}_COMPLETED`" /></el-select>
            <el-input v-else v-model="row.refCode" placeholder="任务/里程碑/交付件编码或BPM Key" />
            <el-button v-if="!disabled" link type="danger" @click="model.payload.references.splice(index, 1)">移除</el-button>
          </div>
          <el-button v-if="!disabled" @click="model.payload.references.push({ refType: 'TASK', refCode: '' })">新增事实引用</el-button>
        </div>
      </el-form-item>
    </template>
    <template v-else-if="kind === 'MILESTONE'">
      <el-form-item label="名称"><el-input v-model="model.payload.name" /></el-form-item>
      <el-form-item label="达成标准"><el-input v-model="model.payload.criteria" type="textarea" /></el-form-item>
    </template>
  </el-form>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DefinitionKind, DefinitionSave } from '@/api/pms/project/project-templates/definitions'
import type { BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'
import DefinitionSelect from './DefinitionSelect.vue'
import BusinessViewSelect from './BusinessViewSelect.vue'
import RuleDecisionDesigner from './RuleDecisionDesigner.vue'
const props = defineProps<{ model: DefinitionSave; disabled?: boolean }>()
const kind = computed(() => props.model.definitionKind)
const stageCodes = ['S0', 'S1', 'S2', 'S3', 'S4', 'S5', 'S6']
const flags = [{ key: 'start', label: '开始阶段' }, { key: 'terminal', label: '正常收口' }]
const executionSlots: { key: string; label: string; kind: DefinitionKind }[] = [
  { key: 'workBinding', label: '主工作绑定', kind: 'WORK_BINDING' },
  { key: 'permissionPolicy', label: '权限策略', kind: 'PERMISSION_POLICY' },
  { key: 'completionRule', label: '完成规则', kind: 'COMPLETION_RULE' }
]
const bindingTypes = ['STAGE_NATIVE', 'TASK_NATIVE', 'BUSINESS_OBJECT', 'BUSINESS_COMPONENT', 'DYNAMIC_FORM', 'APPROVAL', 'COMPOSITE']
const strategies = ['REFERENCE_EXISTING', 'CREATE_ON_ENTER', 'CREATE_ON_FIRST_ACTION', 'READ_ONLY_AGGREGATE']
const gateTypes = ['TASK', 'MILESTONE', 'DELIVERABLE', 'STATE', 'APPROVAL', 'PROCESS']
const native = computed(() => props.model.payload.bindingType?.endsWith('_NATIVE'))
const reference = (key: string) => props.model.references.find((row) => row.referenceKey === key)?.targetRevisionId
const setReference = (key: string, value?: number) => {
  props.model.references = props.model.references.filter((row) => row.referenceKey !== key)
  if (value) props.model.references.push({ referenceKey: key, targetRevisionId: value })
  if (kind.value === 'STAGE' || kind.value === 'TASK') props.model.payload[key] = key
}
const changeBinding = (bindingType: string) => {
  props.model.payload.bindingType = bindingType
  if (bindingType.endsWith('_NATIVE')) {
    for (const key of ['businessViewRevisionId', 'targetContextCode', 'targetObjectType', 'targetObjectKey']) delete props.model.payload[key]
    props.model.payload.contextMapping = {}
    mappings.value = []
  }
}
const selectView = (view?: BusinessViewRegistrationVO) => {
  if (!view) return
  props.model.payload.targetContextCode = view.ownerContext
  props.model.payload.targetObjectType = view.entityType
}
const mappings = ref<{ key: string; value: string }[]>([])
const mappingError = ref('')
watch(() => props.model, () => {
  mappings.value = Object.entries(props.model.payload.contextMapping ?? {}).map(([key, value]) => ({ key, value: String(value) }))
  mappingError.value = ''
}, { immediate: true })
const syncMapping = () => {
  const keys = mappings.value.map((row) => row.key)
  mappingError.value = keys.some((key) => !key) || new Set(keys).size !== keys.length ? '映射键不能为空或重复；请修正后保存。' : ''
  props.model.payload.contextMapping = Object.fromEntries(mappings.value.map((row) => [row.key, row.value]))
}
defineExpose({ validate: () => {
  if (kind.value !== 'WORK_BINDING') return true
  syncMapping()
  return !mappingError.value
} })
</script>
<style scoped>
.form-row { display: flex; gap: 8px; margin-bottom: 8px; }
.form-row > :deep(.el-input), .form-row > :deep(.el-select) { min-width: 120px; flex: 1; }
</style>