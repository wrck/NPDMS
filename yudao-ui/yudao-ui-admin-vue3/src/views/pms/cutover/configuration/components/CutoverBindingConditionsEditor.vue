<template>
  <div class="binding-conditions-editor">
    <div v-if="!disabled" class="add-condition">
      <el-select v-model="selectedDimension" filterable clearable placeholder="选择要限制的维度" aria-label="新增条件维度">
        <el-option v-for="dimension in availableDimensions" :key="dimension.code" :label="`${dimension.name || dimension.code}（${dimension.code}）`" :value="dimension.code" />
      </el-select>
      <el-button :disabled="!selectedDimension" @click="addCondition">添加条件</el-button>
    </div>
    <p class="help">未限制的维度不参与匹配筛选；清空全部值后删除该维度条件。</p>
    <el-table :data="rows" border empty-text="尚未限制任何维度">
      <el-table-column label="维度" min-width="170">
        <template #default="{ row }">
          <strong>{{ row.dimension?.name || row.code }}</strong>
          <div class="help">{{ row.code }}</div>
          <div v-if="!row.dimension?.enabled" class="unavailable" role="status">原维度定义不存在或已停用，已有条件保留。</div>
        </template>
      </el-table-column>
      <el-table-column label="允许值" min-width="320">
        <template #default="{ row }">
          <template v-if="row.dimension?.enabled && dictType(row.dimension)">
            <el-select :model-value="validValues(row)" multiple filterable clearable :disabled="disabled" placeholder="尚未限制，请选择字典值" :aria-label="`${row.code}允许的字典值`" @update:model-value="setDictionaryValues({ row, values: $event })">
              <el-option v-for="option in dictionaryOptions(row.dimension)" :key="`${typeof option.value}:${String(option.value)}`" :value="option.value" :label="option.label" />
            </el-select>
            <div v-if="!dictionaryOptions(row.dimension).length" class="help" role="status">当前字典没有可选值。</div>
            <div v-if="unavailableValues(row).length" class="old-values">
              <span class="help">以下原值已不在字典中，保留只读：</span>
              <div v-for="entry in unavailableValues(row)" :key="entry.index" class="value-row">
                <el-tag type="warning">{{ display(entry.value) }}（{{ valueType(entry.value) }}）</el-tag>
                <el-button v-if="!disabled" link type="danger" :aria-label="`移除${row.code}原值${entry.index + 1}`" @click="removeValue({ row, index: entry.index })">移除此原值</el-button>
              </div>
            </div>
          </template>
          <template v-else>
            <div v-for="(value, index) in valuesFor(row.code)" :key="index" class="value-row">
              <template v-if="row.dimension?.enabled && isScalar(value)">
                <el-input-number v-if="typeof value === 'number'" :model-value="value" :disabled="disabled" controls-position="right" :aria-label="`${row.code}数字值${index + 1}`" @update:model-value="editValue({ row, index, value: $event })" />
                <el-select v-else-if="typeof value === 'boolean'" :model-value="value" :disabled="disabled" :aria-label="`${row.code}布尔值${index + 1}`" @update:model-value="editValue({ row, index, value: $event })">
                  <el-option label="是（true）" :value="true" /><el-option label="否（false）" :value="false" />
                </el-select>
                <el-input v-else :model-value="value" :disabled="disabled" :aria-label="`${row.code}文本值${index + 1}`" @update:model-value="editValue({ row, index, value: $event })" />
                <el-tag type="info">{{ valueType(value) }}</el-tag>
              </template>
              <el-tag v-else type="info">{{ display(value) }}（{{ valueType(value) }}）</el-tag>
              <el-button v-if="!disabled" link type="danger" :aria-label="`移除${row.code}允许值${index + 1}`" @click="removeValue({ row, index })">移除值</el-button>
            </div>
            <div v-if="!disabled && row.dimension?.enabled" class="value-row">
              <el-input-number v-if="row.dimension.dataType.toUpperCase() === 'NUMBER'" :model-value="typeof draftValues[row.code] === 'number' ? draftValues[row.code] as number : undefined" controls-position="right" :aria-label="`${row.code}新增数字值`" @update:model-value="draftValues[row.code] = $event" />
              <el-select v-else-if="row.dimension.dataType.toUpperCase() === 'BOOLEAN'" :model-value="draftValues[row.code]" placeholder="选择布尔值" :aria-label="`${row.code}新增布尔值`" @update:model-value="draftValues[row.code] = $event">
                <el-option label="是（true）" :value="true" /><el-option label="否（false）" :value="false" />
              </el-select>
              <el-input v-else :model-value="typeof draftValues[row.code] === 'string' ? draftValues[row.code] as string : ''" placeholder="输入允许值" :aria-label="`${row.code}新增文本值`" @update:model-value="draftValues[row.code] = $event" @keyup.enter="addValue(row)" />
              <el-button :disabled="draftValues[row.code] === undefined" @click="addValue(row)">添加值</el-button>
            </div>
            <span v-if="!row.values.length" class="help">尚未限制此维度。</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column v-if="!disabled" label="操作" width="110">
        <template #default="{ row }">
          <el-popconfirm title="确认删除此维度条件？删除后该维度不再参与匹配筛选。" confirm-button-text="删除" cancel-button-text="保留" @confirm="removeCondition(row.code)">
            <template #reference><el-button link type="danger" :aria-label="`删除${row.code}条件`">删除条件</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CutoverDimension } from '@/api/pms/cutover/configuration'
import { getStrDictOptions } from '@/utils/dict'

type Scalar = string | number | boolean
interface ConditionRow { code: string; dimension?: CutoverDimension; values: unknown[] }
interface DictionaryOption { value: Scalar; label: string }
const props = defineProps<{
  modelValue: Record<string, unknown>
  dimensions: CutoverDimension[]
  dictionary: Record<string, unknown>
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()
const selectedDimension = ref('')
const pendingCodes = ref<string[]>([])
const draftValues = ref<Record<string, Scalar | undefined>>({})
const isScalar = (value: unknown): value is Scalar => ['string', 'number', 'boolean'].includes(typeof value)
const valueType = (value: unknown) => typeof value === 'string' ? 'STRING' : typeof value === 'number' ? 'NUMBER' : typeof value === 'boolean' ? 'BOOLEAN' : value == null ? 'NULL' : '非标量原值'
const display = (value: unknown) => value == null ? '空值' : typeof value === 'boolean' ? (value ? '是' : '否') : String(value)
const valuesFor = (code: string): unknown[] => {
  if (!Object.prototype.hasOwnProperty.call(props.modelValue, code)) return []
  const value = props.modelValue[code]
  return Array.isArray(value) ? value : [value]
}
const rows = computed<ConditionRow[]>(() => [...new Set([...Object.keys(props.modelValue), ...pendingCodes.value])].map(code => ({
  code,
  dimension: props.dimensions.find(dimension => dimension.code === code),
  values: valuesFor(code)
})))
const availableDimensions = computed(() => props.dimensions.filter(dimension => dimension.enabled && !rows.value.some(row => row.code === dimension.code)))
const dictType = (dimension: CutoverDimension) => dimension.valueSource?.startsWith('DICT:') ? dimension.valueSource.slice(5) : ''
const dictionaryOptions = (dimension: CutoverDimension): DictionaryOption[] => {
  const type = dictType(dimension)
  if (!type) return []
  const frozen = props.dictionary[type]
  if (!Array.isArray(frozen)) return getStrDictOptions(type)
  return frozen.filter((option): option is DictionaryOption => !!option && typeof option === 'object' && isScalar(option.value) && typeof option.label === 'string')
}
const validValues = (row: ConditionRow): Scalar[] => row.values.filter((value): value is Scalar =>
  !!row.dimension && dictionaryOptions(row.dimension).some(option => option.value === value))
const unavailableValues = (row: ConditionRow) => row.values.map((value, index) => ({ value, index })).filter(entry =>
  !row.dimension || !dictionaryOptions(row.dimension).some(option => option.value === entry.value))
const writeValues = (input: { row: ConditionRow; values: unknown[] }) => {
  if (props.disabled) return
  const next = { ...props.modelValue }
  if (!input.values.length) delete next[input.row.code]
  else next[input.row.code] = !Array.isArray(props.modelValue[input.row.code]) && Object.prototype.hasOwnProperty.call(props.modelValue, input.row.code) && input.values.length === 1
    ? input.values[0] : input.values
  emit('update:modelValue', next)
}
const addCondition = () => {
  if (props.disabled || !availableDimensions.value.some(dimension => dimension.code === selectedDimension.value)) return
  pendingCodes.value.push(selectedDimension.value)
  selectedDimension.value = ''
}
const removeCondition = (code: string) => {
  if (props.disabled) return
  pendingCodes.value = pendingCodes.value.filter(value => value !== code)
  delete draftValues.value[code]
  const next = { ...props.modelValue }
  delete next[code]
  emit('update:modelValue', next)
}
const setDictionaryValues = (input: { row: ConditionRow; values: Scalar[] }) => {
  if (!input.row.dimension?.enabled) return
  const allowed = dictionaryOptions(input.row.dimension)
  writeValues({ row: input.row, values: [...input.values.filter(value => allowed.some(option => option.value === value)), ...unavailableValues(input.row).map(entry => entry.value)] })
}
const editValue = (input: { row: ConditionRow; index: number; value: Scalar | undefined }) => {
  if (input.value === undefined) return
  writeValues({ row: input.row, values: input.row.values.map((value, index) => index === input.index ? input.value : value) })
}
const removeValue = (input: { row: ConditionRow; index: number }) =>
  writeValues({ row: input.row, values: input.row.values.filter((_, index) => index !== input.index) })
const addValue = (row: ConditionRow) => {
  const value = draftValues.value[row.code]
  if (props.disabled || value === undefined || !row.dimension?.enabled) return
  if (!row.values.includes(value)) writeValues({ row, values: [...row.values, value] })
  delete draftValues.value[row.code]
}
</script>

<style scoped>
.binding-conditions-editor { width: 100%; min-width: 0; }
.add-condition, .value-row { display: flex; align-items: center; gap: 8px; }
.add-condition { margin-bottom: 12px; }
.add-condition > .el-select { max-width: 360px; }
.value-row { margin: 8px 0; }
.value-row > .el-input, .value-row > .el-select, .value-row > .el-input-number { flex: 1; min-width: 0; }
.value-row > .el-tag { flex-shrink: 0; }
.help { margin: 8px 0; color: var(--el-text-color-secondary); font-size: 13px; }
.unavailable { margin-top: 8px; color: var(--el-color-warning-dark-2); font-size: 13px; }
.old-values { margin-top: 12px; }
.el-select { width: 100%; }
@media (max-width: 767px) {
  .add-condition, .value-row { flex-wrap: wrap; }
  .add-condition > .el-select { max-width: none; }
  .value-row > .el-input, .value-row > .el-select, .value-row > .el-input-number { flex-basis: 100%; }
}
</style>
