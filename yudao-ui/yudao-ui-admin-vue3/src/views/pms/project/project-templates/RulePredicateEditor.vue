<template>
  <div class="predicate-editor">
    <el-select
      :model-value="predicate"
      :disabled="disabled"
      aria-label="条件类型"
      @update:model-value="selectPredicate"
    >
      <el-option v-for="(label, value) in labels" :key="value" :value="value" :label="label" />
    </el-select>
    <template v-if="predicate === 'FIELD'">
      <el-select
        :model-value="parameters.fieldCode"
        :disabled="disabled"
        filterable
        placeholder="选择开放字段"
        @update:model-value="selectField"
      >
        <el-option
          v-for="field in fields"
          :key="field.code"
          :value="field.code"
          :label="field.label"
        />
      </el-select>
      <el-select
        :model-value="parameters.operator"
        :disabled="disabled"
        aria-label="比较方式"
        @update:model-value="set('operator', $event)"
      >
        <el-option
          v-for="item in operators"
          :key="item.value"
          :value="item.value"
          :label="item.label"
        />
      </el-select>
      <template v-if="!['null', 'notNull'].includes(String(parameters.operator))">
        <el-select
          v-if="parameters.valueType === 'BOOLEAN'"
          :model-value="parameters.value"
          :disabled="disabled"
          aria-label="布尔值"
          @update:model-value="set('value', $event)"
        >
          <el-option :value="true" label="是" /><el-option :value="false" label="否" />
        </el-select>
        <el-date-picker
          v-else-if="parameters.valueType === 'DATE' && !listValue"
          :model-value="typeof parameters.value === 'string' ? parameters.value : undefined"
          :disabled="disabled"
          value-format="YYYY-MM-DD"
          type="date"
          placeholder="选择日期"
          @update:model-value="set('value', $event)"
        />
        <el-input
          v-else
          :model-value="valueText"
          :disabled="disabled"
          :placeholder="listValue ? '多个值用换行分隔' : '比较值'"
          :type="listValue ? 'textarea' : 'text'"
          @update:model-value="setValue"
        />
      </template>
    </template>
    <el-select
      v-else-if="predicate === 'CONSTANT'"
      :model-value="parameters.value"
      :disabled="disabled"
      aria-label="固定条件结果"
      @update:model-value="set('value', $event)"
    >
      <el-option :value="true" label="始终满足" /><el-option :value="false" label="始终不满足" />
    </el-select>
    <template v-else-if="predicate === 'BUSINESS_FACT'">
      <el-select
        v-if="sources.length || parameters.sourceNodeKey"
        :model-value="parameters.sourceNodeKey ?? '$current'"
        :disabled="disabled"
        filterable
        aria-label="业务结果来源节点"
        @update:model-value="selectSource"
      >
        <el-option value="$current" label="本节点（完成／退出）" />
        <el-option v-for="source in sources" :key="source.key" :value="source.key" :label="source.label" />
        <el-option v-if="missingSource" :value="String(parameters.sourceNodeKey)" label="来源节点已移除，请重新选择" disabled />
      </el-select>
      <el-select
        :model-value="parameters.factCode"
        :disabled="disabled"
        filterable
        aria-label="原模块业务事实"
        placeholder="原模块完成事实"
        @update:model-value="set('factCode', $event)"
      >
        <el-option
          v-for="fact in sourceFacts"
          :key="`${fact.ownerContext}/${fact.objectType}/${fact.factCode}`"
          :value="fact.factCode"
          :label="`${fact.label} · ${fact.ownerContext}`"
        />
      </el-select>
      <el-select
        :model-value="parameters.quantifier"
        :disabled="disabled"
        aria-label="关联记录范围"
        @update:model-value="set('quantifier', $event)"
      >
        <el-option value="ALL" label="全部关联记录" /><el-option value="ANY" label="任一关联记录" />
      </el-select>
    </template>
    <span v-else-if="predicate.endsWith('_NATIVE_STATUS')" class="condition-hint"
      >以本轮真实办理结果为依据</span
    >
    <DecisionTableConditionEditor
      v-else-if="predicate === 'DECISION'"
      :parameters="parameters"
      :rules="rules"
      :disabled="disabled"
      @change="emit('change', 'DECISION', $event)"
    />
    <el-input
      v-else
      :model-value="typeof parameters.refCode === 'string' ? parameters.refCode : undefined"
      :disabled="disabled"
      placeholder="引用编码（阶段完成引用：阶段编码_COMPLETED）"
      @update:model-value="set('refCode', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import type {
  JsonObject,
  JsonValue,
  CompletionFactCatalogVO
} from '@/api/pms/project/project-templates'
import type { RuleField, VersionRule } from '@/api/pms/project/project-templates/rules'
import DecisionTableConditionEditor from './DecisionTableConditionEditor.vue'
import { newDecisionTable } from './decisionTableModel'
import { ruleBusinessSourcesKey } from './ruleBusinessSources'
const props = defineProps<{
  predicate: string
  parameters: JsonObject
  fields: RuleField[]
  facts: CompletionFactCatalogVO[]
  rules?: VersionRule[]
  disabled?: boolean
}>()
const emit = defineEmits<{ change: [predicate: string, parameters: JsonObject] }>()
const sources = inject(ruleBusinessSourcesKey, computed(() => []))
const selectedSource = computed(() => sources.value.find((item) => item.key === props.parameters.sourceNodeKey))
const missingSource = computed(() => !!props.parameters.sourceNodeKey && !selectedSource.value)
const sourceFacts = computed(() => props.parameters.sourceNodeKey
  ? props.facts.filter((fact) => fact.ownerContext === selectedSource.value?.ownerContext && fact.objectType === selectedSource.value?.objectType)
  : props.facts)
const selectSource = (key: string) => {
  const parameters = { ...props.parameters }
  if (key !== '$current') parameters.sourceNodeKey = key
  else delete parameters.sourceNodeKey
  const source = sources.value.find((item) => item.key === key)
  if (source && !props.facts.some((fact) => fact.factCode === parameters.factCode
    && fact.ownerContext === source.ownerContext && fact.objectType === source.objectType))
    parameters.factCode = ''
  emit('change', 'BUSINESS_FACT', parameters)
}
const labels = {
  FIELD: '字段判断',
  TASK: '任务完成',
  STATE: '阶段完成',
  BUSINESS_FACT: '业务结果',
  APPROVAL: '审批结果',
  PROCESS: '流程结果',
  MILESTONE: '里程碑',
  DELIVERABLE: '交付件',
  TASK_NATIVE_STATUS: '任务自身办理',
  STAGE_NATIVE_STATUS: '阶段自身办理',
  CONSTANT: '固定条件',
  DECISION: '决策表输出'
}
const allOperators = [
  { value: '=', label: '等于' },
  { value: '!=', label: '不等于' },
  { value: '>', label: '大于' },
  { value: '>=', label: '大于等于' },
  { value: '<', label: '小于' },
  { value: '<=', label: '小于等于' },
  { value: 'contains', label: '包含文本' },
  { value: 'beginsWith', label: '开头为' },
  { value: 'endsWith', label: '结尾为' },
  { value: 'in', label: '属于集合' },
  { value: 'notIn', label: '不属于集合' },
  { value: 'between', label: '介于' },
  { value: 'null', label: '为空' },
  { value: 'notNull', label: '非空' }
]
const operators = computed(() =>
  allOperators.filter((item) => {
    if (['contains', 'beginsWith', 'endsWith'].includes(item.value))
      return props.parameters.valueType === 'TEXT'
    return (
      props.parameters.valueType !== 'BOOLEAN' ||
      ['=', '!=', 'null', 'notNull'].includes(item.value)
    )
  })
)
const listValue = computed(() =>
  ['in', 'notIn', 'between'].includes(String(props.parameters.operator))
)
const valueText = computed(() =>
  Array.isArray(props.parameters.value)
    ? props.parameters.value.join('\n')
    : String(props.parameters.value ?? '')
)
const set = (name: string, value: JsonValue | undefined) =>
  emit('change', props.predicate, { ...props.parameters, [name]: value })
const setValue = (value: string) =>
  set('value', listValue.value ? value.split('\n').filter((item) => item !== '') : value)
const selectField = (code: string) => {
  const field = props.fields.find((item) => item.code === code)
  if (field)
    emit('change', 'FIELD', {
      fieldCode: code,
      valueType: field.valueType,
      operator: '=',
      value: field.valueType === 'BOOLEAN' ? false : ''
    })
}
const selectPredicate = (value: string) => {
  if (value === 'DECISION') {
    emit('change', 'DECISION', {
      table: newDecisionTable() as unknown as JsonObject,
      fieldCode: 'result',
      valueType: 'BOOLEAN',
      operator: '=',
      value: true,
      quantifier: 'ANY'
    })
    return
  }
  const parameters: JsonObject =
    value === 'FIELD'
      ? { fieldCode: '', valueType: 'TEXT', operator: '=', value: '' }
      : value === 'CONSTANT'
        ? { value: false }
        : value === 'BUSINESS_FACT'
          ? { factCode: '', quantifier: 'ALL' }
          : value.endsWith('_NATIVE_STATUS')
            ? { requiredStatus: 'DONE' }
            : { refCode: '' }
  emit('change', value, parameters)
}
</script>

<style scoped>
.predicate-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.predicate-editor > :deep(.el-select),
.predicate-editor > :deep(.el-input) {
  width: 180px;
}
.condition-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
