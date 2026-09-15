<template>
  <el-collapse v-model="open" class="rule-simulation" @change="prepare">
    <el-collapse-item title="模拟输入试算 · 不推进业务状态" name="trial">
      <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
      <el-form label-position="top" v-loading="busy">
        <el-form-item v-for="input in result?.inputs ?? []" :key="input.key" :label="inputLabel(input)">
          <el-select
            v-if="input.valueType === 'BOOLEAN'"
            v-model="values[input.key]"
            clearable
            placeholder="未知 / 是 / 否"
          >
            <el-option :value="true" label="是" /><el-option :value="false" label="否" />
          </el-select>
          <el-input
            v-else
            :type="input.valueType === 'CHILD_PROJECT_STATUSES' ? 'textarea' : 'text'"
            :model-value="typeof values[input.key] === 'string' ? String(values[input.key]) : undefined"
            :placeholder="input.valueType === 'CHILD_PROJECT_STATUSES' ? '每行一个：正常关闭 / 异常关闭 / 未关闭 / 未知；无子项目填写“无”' : input.valueType === 'DATETIME' ? '例如 2026-09-15T09:00:00+08:00' : '输入模拟值；未提供的值保持未知'"
            @update:model-value="values[input.key] = $event"
          />
        </el-form-item>
        <el-button type="primary" :loading="busy" @click="run">试算</el-button>
      </el-form>
      <template v-if="result">
        <el-alert
          :title="summary"
          :type="result.evaluation.reasonCode ? 'warning' : 'info'"
          :closable="false"
          class="mt-12px"
        />
        <el-table
          v-if="result.evaluation.kind === 'DECISION'"
          :data="result.evaluation.values"
          border
        >
          <el-table-column
            v-for="column in outputColumns"
            :key="column"
            :prop="column"
            :label="column"
          />
        </el-table>
        <el-table :data="result.evaluation.conditions" border class="mt-12px">
          <el-table-column prop="path" label="条件位置" /><el-table-column
            prop="component"
            label="执行组件"
          />
          <el-table-column prop="outcome" label="结果" /><el-table-column
            prop="reasonCode"
            label="诊断"
          />
        </el-table>
        <el-table
          v-if="result.evaluation.diagnostics?.length"
          :data="result.evaluation.diagnostics"
          border
          class="mt-12px"
        >
          <el-table-column prop="path" label="规则位置" /><el-table-column
            prop="inputKey"
            label="输入字段"
          />
          <el-table-column prop="component" label="组件" /><el-table-column
            prop="code"
            label="原因"
          />
        </el-table>
      </template>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { computed, inject, reactive, ref, watch } from 'vue'
import type { JsonValue } from '@/api/pms/project/project-templates'
import {
  simulateRule,
  type RuleSimulation,
  type VersionRule
} from '@/api/pms/project/project-templates/rules'
import { relativeTimeOptionsKey } from './relativeTimeModel'
const props = defineProps<{ rules: VersionRule[]; ruleKey: string }>()
const timeOptions = inject(relativeTimeOptionsKey, computed(() => undefined))
const inputLabel = (input: RuleSimulation['inputs'][number]) => {
  if (input.key === 'clock.activation') return '模拟本节点本轮激活时间（含时区）'
  if (!input.key.startsWith('clock.completed:')) return input.label
  const sourceKey = input.key.slice('clock.completed:'.length)
  const source = timeOptions.value?.sources.find(node => node.key === sourceKey)
  return `${source?.label ?? sourceKey} · 本轮完成时间（含时区）`
}
const open = ref<string[]>([])
const busy = ref(false)
const failure = ref('')
const result = ref<RuleSimulation>()
const values = reactive<Record<string, string | boolean | undefined>>({})
let generation = 0
watch(
  () => [props.ruleKey, props.rules],
  () => {
    generation++
    result.value = undefined
    failure.value = ''
    busy.value = false
    for (const key of Object.keys(values)) delete values[key]
  },
  { deep: true }
)
const summary = computed(() => {
  const value = result.value?.evaluation
  if (!value) return ''
  if (value.kind === 'DECISION')
    return value.status === 'AVAILABLE'
      ? '策略求值完成，输出保留原始类型。'
      : `结果未知：${value.reasonCode ?? '缺少输入'}`
  return (
    { MATCHED: '条件满足', NOT_MATCHED: '条件不满足', UNKNOWN: '条件未知' }[value.outcome] +
    (value.reasonCode ? `：${value.reasonCode}` : '')
  )
})
const outputColumns = computed(() =>
  result.value?.evaluation.kind === 'DECISION'
    ? [...new Set(result.value.evaluation.values.flatMap((row) => Object.keys(row)))]
    : []
)
const run = async () => {
  const requestGeneration = ++generation
  busy.value = true
  failure.value = ''
  try {
    const facts: Record<string, JsonValue> = {}
    for (const [key, value] of Object.entries(values)) {
      if (value === undefined || value === '') continue
      if (result.value?.inputs.find(input => input.key === key)?.valueType === 'CHILD_PROJECT_STATUSES') {
        const labels: Record<string, string> = { 正常关闭: 'NORMAL_CLOSED', 异常关闭: 'EXCEPTION_CLOSED', 未关闭: 'ACTIVE', 未知: 'UNKNOWN' }
        facts[key] = String(value).trim() === '无' ? []
          : String(value).split('\n').map(item => labels[item.trim()] ?? item.trim())
      } else facts[key] = value
    }
    const response = await simulateRule(props.rules, props.ruleKey, facts)
    if (requestGeneration === generation) result.value = response
  } catch (error) {
    if (requestGeneration === generation)
      failure.value = error instanceof Error ? error.message : '规则试算失败，请检查配置。'
  } finally {
    if (requestGeneration === generation) busy.value = false
  }
}
const prepare = () => {
  if (open.value.includes('trial') && !result.value) void run()
}
</script>
