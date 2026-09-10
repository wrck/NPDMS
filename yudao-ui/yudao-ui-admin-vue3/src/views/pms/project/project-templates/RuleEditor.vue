<template>
  <div class="rule-editor">
    <el-select :model-value="mode" :disabled="disabled" @update:model-value="changeMode">
      <el-option value="PREDICATE" label="事实谓词" /><el-option
        value="ALL"
        label="全部满足 ALL"
      /><el-option value="ANY" label="任一满足 ANY" />
    </el-select>
    <template v-if="mode === 'PREDICATE'">
      <el-select
        :model-value="modelValue.predicate"
        :disabled="disabled"
        placeholder="选择已注册谓词"
        @update:model-value="changePredicate"
      >
        <el-option
          v-for="predicate in predicates"
          :key="predicate"
          :value="predicate"
          :label="`${predicateLabels[predicate] ?? predicate}（${predicate}）`"
        />
      </el-select>
      <el-input
        v-if="modelValue.predicate?.endsWith('_NATIVE_STATUS')"
        model-value="DONE"
        disabled
        aria-label="要求状态"
      />
      <el-select
        v-else-if="modelValue.predicate === 'STATE'"
        :model-value="modelValue.parameters?.refCode"
        :disabled="disabled"
        placeholder="受控阶段完成码"
        @update:model-value="setCode"
      >
        <el-option v-for="code in stateCodes" :key="code" :value="code" :label="code" />
      </el-select>
      <template v-else-if="modelValue.predicate === 'BUSINESS_FACT'">
        <el-select
          :model-value="modelValue.parameters?.factCode"
          :disabled="disabled"
          filterable
          placeholder="选择已注册完成事实"
          @update:model-value="setBusinessCode"
        >
          <el-option
            v-for="fact in factCatalog"
            :key="`${fact.ownerContext}/${fact.objectType}/${fact.factCode}`"
            :value="fact.factCode"
            :label="`${fact.label}（${fact.ownerContext}/${fact.objectType}）`"
          />
        </el-select>
        <span v-if="!factCatalog.length" class="fact-catalog-hint"
          >完成事实目录不可用或为空；仅可选择已部署Owner注册的事实</span
        >
        <el-select
          :model-value="modelValue.parameters?.quantifier"
          :disabled="disabled"
          @update:model-value="setQuantifier"
        >
          <el-option value="ALL" label="全部关联记录满足" />
          <el-option value="ANY" label="至少一条关联记录满足" />
        </el-select>
        <span>无关联记录或结果未知时不能完成</span>
      </template>
      <el-input
        v-else
        :model-value="modelValue.parameters?.refCode"
        :disabled="disabled"
        placeholder="Owner事实稳定编码 / BPM定义Key"
        @update:model-value="setCode"
      />
    </template>
    <template v-else>
      <div v-for="(child, index) in children" :key="index" class="rule-child">
        <RuleEditor
          :model-value="child"
          :disabled="disabled"
          @update:model-value="replaceChild(index, $event)"
        />
        <el-button v-if="!disabled" link type="danger" @click="removeChild(index)"
          >移除条件</el-button
        >
      </div>
      <el-button v-if="!disabled" @click="addChild">新增条件</el-button>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as TemplateApi from '@/api/pms/project/project-templates'
import type { CompletionFactCatalogVO } from '@/api/pms/project/project-templates'
import type { JsonObject } from '@/api/pms/project/project-templates/definitions'
defineOptions({ name: 'RuleEditor' })
const props = defineProps<{ modelValue: JsonObject; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: JsonObject] }>()
const predicateLabels: Record<string, string> = {
  BUSINESS_FACT: '业务完成事实',
  TASK_NATIVE_STATUS: '任务自身状态与必填信息',
  STAGE_NATIVE_STATUS: '阶段自身状态',
  TASK: '关联任务结果',
  MILESTONE: '里程碑达成',
  DELIVERABLE: '必要交付物满足情况',
  STATE: '业务阶段完成',
  APPROVAL: '实际审批结果',
  PROCESS: '实际流程结果'
}
const factCatalog = ref<CompletionFactCatalogVO[]>([])
onMounted(async () => {
  try {
    factCatalog.value = await TemplateApi.getCompletionFactCatalog()
  } catch {
    factCatalog.value = []
  }
})
const predicates = [
  'BUSINESS_FACT',
  'TASK_NATIVE_STATUS',
  'STAGE_NATIVE_STATUS',
  'TASK',
  'MILESTONE',
  'DELIVERABLE',
  'STATE',
  'APPROVAL',
  'PROCESS'
]
const stateCodes = [
  'S0_COMPLETED',
  'S1_COMPLETED',
  'S2_COMPLETED',
  'S3_COMPLETED',
  'S4_COMPLETED',
  'S5_COMPLETED',
  'S6_COMPLETED'
]
const mode = computed(() => props.modelValue.operator ?? 'PREDICATE')
const children = computed<JsonObject[]>(() =>
  Array.isArray(props.modelValue.rules) ? props.modelValue.rules : []
)
const blank = () => ({ predicate: '', parameters: { refCode: '' } })
const changeMode = (value: string) =>
  emit('update:modelValue', value === 'PREDICATE' ? blank() : { operator: value, rules: [blank()] })
const changePredicate = (predicate: string) =>
  emit('update:modelValue', {
    predicate,
    parameters:
      predicate === 'BUSINESS_FACT'
        ? { factCode: '', quantifier: 'ALL' }
        : predicate.endsWith('_NATIVE_STATUS')
          ? { requiredStatus: 'DONE' }
          : { refCode: '' }
  })
const setBusinessCode = (factCode: string) =>
  emit('update:modelValue', {
    ...props.modelValue,
    parameters: { ...props.modelValue.parameters, factCode }
  })
const setQuantifier = (quantifier: string) =>
  emit('update:modelValue', {
    ...props.modelValue,
    parameters: { ...props.modelValue.parameters, quantifier }
  })
const setCode = (refCode: string) =>
  emit('update:modelValue', { ...props.modelValue, parameters: { refCode } })
const replaceChild = (index: number, child: JsonObject) =>
  emit('update:modelValue', {
    ...props.modelValue,
    rules: props.modelValue.rules.map((row: JsonObject, i: number) => (i === index ? child : row))
  })
const addChild = () =>
  emit('update:modelValue', {
    ...props.modelValue,
    rules: [...(props.modelValue.rules ?? []), blank()]
  })
const removeChild = (index: number) =>
  emit('update:modelValue', {
    ...props.modelValue,
    rules: props.modelValue.rules.filter((_: JsonObject, i: number) => i !== index)
  })
</script>
<style scoped>
.rule-editor {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;
}
.rule-editor > :deep(.el-select),
.rule-editor > :deep(.el-input) {
  width: 220px;
}
.rule-child {
  width: 100%;
  border-left: 2px solid var(--el-border-color);
  padding-left: 12px;
  margin: 4px 0;
}
.fact-catalog-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
