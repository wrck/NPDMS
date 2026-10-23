<template>
  <div class="external-source-editor">
    <el-alert v-if="!catalog" title="接口目录尚未加载，已有配置仍保留。" type="warning" :closable="false" show-icon />
    <el-alert v-else-if="!catalog.interfaces.length" title="暂无已接通的正式外部接口。接口就绪后可在此选择并配置映射。" type="info" :closable="false" show-icon />
    <label class="source-label">正式接口
      <el-select :model-value="interfaceCode" :disabled="disabled || !catalog" placeholder="选择已接通的接口" @update:model-value="update('interfaceCode', $event)">
        <el-option v-if="interfaceCode && !selected" :label="`${interfaceCode}（未接通）`" :value="interfaceCode" disabled />
        <el-option v-for="entry in catalog?.interfaces || []" :key="entry.code" :label="entry.name" :value="entry.code" />
      </el-select>
    </label>
    <el-checkbox :model-value="modelValue.manualFallback === true" :disabled="disabled" @update:model-value="update('manualFallback', $event)">加载失败时允许上传人工核验证据</el-checkbox>
    <p class="help">当前版本和目标版本公告共用参数结构，“本项查询软件版本”会按设备与公告项选择对应版本。</p>
    <h4>查询参数</h4>
    <el-table :data="parameterFields" border empty-text="选择正式接口后显示查询参数">
      <el-table-column label="接口参数" min-width="180"><template #default="{ row }">{{ row.label || row.key }}<small v-if="row.unknown" class="warning">原参数不在当前接口定义中</small></template></el-table-column>
      <el-table-column label="取值来源" min-width="240"><template #default="{ row }">
        <el-select :model-value="mapping('parameterMapping', row.key)?.sourcePath" clearable :disabled="disabled" placeholder="选择设备或任务字段" @update:model-value="setParameter(row.key, $event, row.required)">
          <el-option v-for="field in catalog?.contextFields || []" :key="field.key" :label="field.label" :value="field.key" :disabled="field.valueType !== row.valueType" />
        </el-select>
      </template></el-table-column>
      <el-table-column label="必需" width="80"><template #default="{ row }"><el-checkbox :model-value="row.required || mapping('parameterMapping', row.key)?.required === true" :disabled="disabled || row.required || !mapping('parameterMapping', row.key)" @update:model-value="setRequired('parameterMapping', row.key, $event)" /></template></el-table-column>
      <el-table-column label="操作" width="80"><template #default="{ row }"><el-button v-if="!disabled && mapping('parameterMapping', row.key)" link type="danger" @click="removeMapping('parameterMapping', row.key)">移除</el-button></template></el-table-column>
    </el-table>
    <h4>公告返回字段</h4>
    <el-table :data="returnFields" border empty-text="接口目录加载后显示返回字段">
      <el-table-column label="公告字段" min-width="150"><template #default="{ row }">{{ row.label || row.key }}<small v-if="row.unknown" class="warning">原映射不属于公告字段</small></template></el-table-column>
      <el-table-column label="接口返回字段" min-width="240"><template #default="{ row }">
        <el-select :model-value="mapping('returnMapping', row.key)?.sourcePath" clearable :disabled="disabled || !selected" placeholder="选择正式接口的返回字段" @update:model-value="setReturn(row.key, $event)">
          <el-option v-for="field in selected?.responseFields || []" :key="field.key" :label="field.label" :value="field.key" :disabled="field.valueType !== 'STRING'" />
        </el-select>
        <small v-if="!selected && mapping('returnMapping', row.key)">{{ mapping('returnMapping', row.key)?.sourcePath }}</small>
      </template></el-table-column>
      <el-table-column label="必需" width="80"><template #default="{ row }"><el-checkbox :model-value="mapping('returnMapping', row.key)?.required === true" :disabled="disabled || !mapping('returnMapping', row.key)" @update:model-value="setRequired('returnMapping', row.key, $event)" /></template></el-table-column>
      <el-table-column label="操作" width="80"><template #default="{ row }"><el-button v-if="!disabled && mapping('returnMapping', row.key)" link type="danger" @click="removeMapping('returnMapping', row.key)">移除</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { CutoverExternalCatalog, CutoverExternalField } from '@/api/pms/cutover/configuration'
type Mapping = { sourcePath: string; valueType: string; required: boolean }
type Direction = 'parameterMapping' | 'returnMapping'
const props = defineProps<{ modelValue: Record<string, unknown>; catalog: CutoverExternalCatalog | null; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()
const interfaceCode = computed(() => typeof props.modelValue.interfaceCode === 'string' ? props.modelValue.interfaceCode : '')
const selected = computed(() => props.catalog?.interfaces.find(entry => entry.code === interfaceCode.value))
const group = (direction: Direction) => {
  const raw = props.modelValue[direction]
  return raw && typeof raw === 'object' && !Array.isArray(raw) ? raw as Record<string, Mapping> : {}
}
const mapping = (direction: Direction, key: string) => group(direction)[key]
const withExisting = (fields: CutoverExternalField[], direction: Direction) => [
  ...fields.map(field => ({ ...field, unknown: false })),
  ...Object.keys(group(direction)).filter(key => !fields.some(field => field.key === key))
    .map(key => ({ key, label: key, valueType: mapping(direction, key)?.valueType || 'STRING', required: false, unknown: true }))
]
const parameterFields = computed(() => withExisting(selected.value?.parameters || [], 'parameterMapping'))
const returnFields = computed(() => withExisting(props.catalog?.bulletinFields || [], 'returnMapping'))
const update = (key: string, value: unknown) => emit('update:modelValue', { ...props.modelValue, [key]: value })
const removeMapping = (direction: Direction, key: string) => {
  const next = { ...group(direction) }; delete next[key]; update(direction, next)
}
const setMapping = (direction: Direction, key: string, value: Mapping) => update(direction, { ...group(direction), [key]: value })
const setParameter = (key: string, sourcePath: string | undefined, required: boolean) => {
  if (!sourcePath) return removeMapping('parameterMapping', key)
  const field = props.catalog?.contextFields.find(entry => entry.key === sourcePath)
  if (field) setMapping('parameterMapping', key, { sourcePath, valueType: field.valueType, required })
}
const setReturn = (key: string, sourcePath: string | undefined) => {
  if (!sourcePath) return removeMapping('returnMapping', key)
  setMapping('returnMapping', key, { sourcePath, valueType: 'STRING', required: mapping('returnMapping', key)?.required ?? true })
}
const setRequired = (direction: Direction, key: string, required: boolean | string | number) => {
  const existing = mapping(direction, key)
  if (existing) setMapping(direction, key, { ...existing, required: required === true })
}
</script>

<style scoped>
.external-source-editor { display: grid; gap: 16px; }
.source-label { display: grid; gap: 8px; }
.el-select { width: 100%; }
h4, p { margin: 0; }
.help { color: var(--el-text-color-secondary); font-size: 13px; }
.warning { display: block; color: var(--el-color-warning); }
</style>
