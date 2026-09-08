<template>
  <div class="rule-editor">
    <el-select :model-value="mode" :disabled="disabled" @update:model-value="changeMode">
      <el-option value="PREDICATE" label="事实谓词" /><el-option value="ALL" label="全部满足 ALL" /><el-option value="ANY" label="任一满足 ANY" />
    </el-select>
    <template v-if="mode === 'PREDICATE'">
      <el-select :model-value="modelValue.predicate" :disabled="disabled" placeholder="选择已注册谓词" @update:model-value="changePredicate">
        <el-option v-for="predicate in predicates" :key="predicate" :value="predicate" :label="predicate" />
      </el-select>
      <el-input v-if="modelValue.predicate?.endsWith('_NATIVE_STATUS')" model-value="DONE" disabled aria-label="要求状态" />
      <el-select v-else-if="modelValue.predicate === 'STATE'" :model-value="modelValue.parameters?.refCode" :disabled="disabled" placeholder="受控阶段完成码" @update:model-value="setCode">
        <el-option v-for="code in stateCodes" :key="code" :value="code" :label="code" />
      </el-select>
      <el-input v-else :model-value="modelValue.parameters?.refCode" :disabled="disabled" placeholder="Owner事实稳定编码 / BPM定义Key" @update:model-value="setCode" />
    </template>
    <template v-else>
      <div v-for="(child, index) in children" :key="index" class="rule-child">
        <RuleEditor :model-value="child" :disabled="disabled" @update:model-value="replaceChild(index, $event)" />
        <el-button v-if="!disabled" link type="danger" @click="removeChild(index)">移除条件</el-button>
      </div>
      <el-button v-if="!disabled" @click="addChild">新增条件</el-button>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { JsonObject } from '@/api/pms/project/project-templates/definitions'
defineOptions({ name: 'RuleEditor' })
const props = defineProps<{ modelValue: JsonObject; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: JsonObject] }>()
const predicates = ['TASK_NATIVE_STATUS', 'STAGE_NATIVE_STATUS', 'TASK', 'MILESTONE', 'DELIVERABLE', 'STATE', 'APPROVAL', 'PROCESS']
const stateCodes = ['S0_COMPLETED', 'S1_COMPLETED', 'S2_COMPLETED', 'S3_COMPLETED', 'S4_COMPLETED', 'S5_COMPLETED', 'S6_COMPLETED']
const mode = computed(() => props.modelValue.operator ?? 'PREDICATE')
const children = computed<JsonObject[]>(() => Array.isArray(props.modelValue.rules) ? props.modelValue.rules : [])
const blank = () => ({ predicate: '', parameters: { refCode: '' } })
const changeMode = (value: string) => emit('update:modelValue', value === 'PREDICATE' ? blank() : { operator: value, rules: [blank()] })
const changePredicate = (predicate: string) => emit('update:modelValue', { predicate, parameters: predicate.endsWith('_NATIVE_STATUS') ? { requiredStatus: 'DONE' } : { refCode: '' } })
const setCode = (refCode: string) => emit('update:modelValue', { ...props.modelValue, parameters: { refCode } })
const replaceChild = (index: number, child: JsonObject) => emit('update:modelValue', { ...props.modelValue, rules: props.modelValue.rules.map((row: JsonObject, i: number) => i === index ? child : row) })
const addChild = () => emit('update:modelValue', { ...props.modelValue, rules: [...(props.modelValue.rules ?? []), blank()] })
const removeChild = (index: number) => emit('update:modelValue', { ...props.modelValue, rules: props.modelValue.rules.filter((_: JsonObject, i: number) => i !== index) })
</script>
<style scoped>
.rule-editor { display: flex; gap: 8px; flex-wrap: wrap; width: 100%; }
.rule-editor > :deep(.el-select), .rule-editor > :deep(.el-input) { width: 220px; }
.rule-child { width: 100%; border-left: 2px solid var(--el-border-color); padding-left: 12px; margin: 4px 0; }
</style>
