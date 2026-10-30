<template>
  <div class="schema-node">
    <div class="node-properties">
      <label v-if="has('code') || !has('key')">编码（code）
        <el-input :model-value="text(modelValue.code)" :disabled="disabled" aria-label="字段编码" @update:model-value="set('code', $event)" />
      </label>
      <label v-if="has('key')">编码（key）
        <el-input :model-value="text(modelValue.key)" :disabled="disabled" aria-label="字段原有键编码" @update:model-value="set('key', $event)" />
      </label>
      <label>名称
        <el-input :model-value="text(modelValue.name)" :disabled="disabled" aria-label="字段名称" @update:model-value="set('name', $event)" />
      </label>
      <label>标签
        <el-input :model-value="text(modelValue.label)" :disabled="disabled" aria-label="字段显示标签" @update:model-value="set('label', $event)" />
      </label>
      <label>数据类型
        <el-select :model-value="text(modelValue.type)" :disabled="disabled" filterable allow-create default-first-option clearable aria-label="字段数据类型" @update:model-value="set('type', $event)">
          <el-option v-for="type in dataTypes" :key="type" :value="type" :label="type" />
        </el-select>
      </label>
      <label>必填
        <el-switch :model-value="modelValue.required === true" :disabled="disabled" aria-label="字段必填" @update:model-value="set('required', $event)" />
      </label>
      <label>字符上限
        <el-input-number :model-value="typeof modelValue.maxLength === 'number' ? modelValue.maxLength : undefined" :disabled="disabled" :min="0" :precision="0" controls-position="right" aria-label="字段字符上限" @update:model-value="set('maxLength', $event)" />
      </label>
    </div>

    <div v-if="modelValue.source != null" class="source" aria-label="只读来源证据">
      <strong>来源（只读）</strong>
      <span v-if="typeof modelValue.source === 'string'">{{ modelValue.source }}</span>
      <dl v-else-if="isObject(modelValue.source)">
        <template v-for="(value, key) in modelValue.source" :key="key">
          <dt>{{ sourceLabels[key] || key }}</dt><dd>{{ displayValue(value) }}</dd>
        </template>
      </dl>
    </div>

    <div class="condition">
      <div class="section-actions">
        <strong>显示条件</strong>
        <el-button v-if="!disabled && !condition" size="small" @click="set('visibleWhen', { field: '', equals: '' })">添加显示条件</el-button>
        <el-popconfirm v-if="!disabled && condition" title="确认删除此显示条件？" confirm-button-text="删除" cancel-button-text="保留" @confirm="set('visibleWhen', undefined)">
          <template #reference><el-button size="small" type="danger" plain>删除显示条件</el-button></template>
        </el-popconfirm>
      </div>
      <div v-if="condition" class="condition-properties">
        <label>关联字段
          <el-input :model-value="text(condition.field)" :disabled="disabled" placeholder="字段编码或路径" aria-label="显示条件关联字段" @update:model-value="setCondition({ field: $event })" />
        </label>
        <label>等于
          <ScalarInput :model-value="condition.equals" :disabled="disabled" label="显示条件比较值" @update:model-value="setCondition({ equals: $event })" />
        </label>
      </div>
      <span v-else class="help">无条件，始终显示。</span>
    </div>

    <el-collapse>
      <el-collapse-item v-for="collection in collections" :key="collection.key" :name="collection.key" :title="`${collection.label}（${children(collection.key).length}）`">
        <el-button v-if="!disabled" size="small" class="add-action" @click="addChild(collection.key)">添加{{ collection.singular }}</el-button>
        <el-empty v-if="!children(collection.key).length" :image-size="40" :description="`暂无${collection.label}`" />
        <el-collapse>
          <el-collapse-item v-for="(child, index) in children(collection.key)" :key="index" :name="index" :title="`${index + 1}. ${text(child.label) || text(child.name) || text(child.code) || text(child.key) || '未命名'}`">
            <div class="child-toolbar" v-if="!disabled">
              <el-popconfirm :title="`确认删除此${collection.singular}及其子结构？`" confirm-button-text="删除" cancel-button-text="保留" @confirm="removeChild({ key: collection.key, index })">
                <template #reference><el-button size="small" type="danger" plain>删除此{{ collection.singular }}</el-button></template>
              </el-popconfirm>
            </div>
            <CutoverSchemaNodeEditor :model-value="child" :disabled="disabled" @update:model-value="updateChild({ key: collection.key, index, value: $event })" />
          </el-collapse-item>
        </el-collapse>
      </el-collapse-item>
      <el-collapse-item name="options" :title="`选项（${options.length}）`">
        <div class="section-actions add-action" v-if="!disabled">
          <el-button size="small" @click="set('options', [...options, ''])">添加值选项</el-button>
          <el-button size="small" @click="set('options', [...options, { label: '', value: '' }])">添加标签和值选项</el-button>
        </div>
        <el-table :data="options.map((option, index) => ({ option, index }))" border empty-text="暂无选项" :max-height="360">
          <el-table-column label="标签" min-width="140">
            <template #default="{ row }">
              <el-input v-if="isObject(row.option)" :model-value="text(row.option.label)" :disabled="disabled" :aria-label="`选项${row.index + 1}标签`" @update:model-value="updateOption({ index: row.index, label: $event })" />
              <span v-else class="help">使用选项值</span>
            </template>
          </el-table-column>
          <el-table-column label="值与值类型" min-width="240">
            <template #default="{ row }">
              <ScalarInput :model-value="isObject(row.option) ? row.option.value : row.option" :disabled="disabled" :label="`选项${row.index + 1}值`" @update:model-value="updateOption({ index: row.index, value: $event })" />
            </template>
          </el-table-column>
          <el-table-column v-if="!disabled" label="操作" width="80">
            <template #default="{ row }">
              <el-popconfirm title="确认删除此选项？" confirm-button-text="删除" cancel-button-text="保留" @confirm="set('options', options.filter((_, index) => index !== row.index))">
                <template #reference><el-button link type="danger" :aria-label="`删除选项${row.index + 1}`">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h } from 'vue'
import { ElInput, ElInputNumber, ElSelect, ElOption, ElMessage, ElMessageBox } from 'element-plus'

type Schema = Record<string, unknown>
type Collection = 'fields' | 'columns'
const props = defineProps<{ modelValue: Schema; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Schema] }>()
const isObject = (value: unknown): value is Schema => !!value && typeof value === 'object' && !Array.isArray(value)
const text = (value: unknown) => typeof value === 'string' ? value : ''
const displayValue = (value: unknown) => value == null ? '空值' : typeof value === 'boolean' ? (value ? '是' : '否') : String(value)
const has = (key: string) => Object.prototype.hasOwnProperty.call(props.modelValue, key)
const dataTypes = ['TEXT', 'NUMBER', 'BOOLEAN']
const sourceLabels: Record<string, string> = { file: '文件', sheet: '工作表', row: '行号', originalName: '原始名称', part: '拆分序号', split: '已拆分' }
const collections: Array<{ key: Collection; label: string; singular: string }> = [
  { key: 'fields', label: '嵌套字段', singular: '字段' }, { key: 'columns', label: '表格列', singular: '列' }
]
const condition = computed(() => isObject(props.modelValue.visibleWhen) ? props.modelValue.visibleWhen : null)
const options = computed<unknown[]>(() => Array.isArray(props.modelValue.options) ? props.modelValue.options : [])
const children = (key: Collection): Schema[] => Array.isArray(props.modelValue[key]) ? props.modelValue[key] as Schema[] : []
const set = (key: string, value: unknown) => {
  if (props.disabled) return
  const next = { ...props.modelValue }
  if (value === undefined) delete next[key]
  else next[key] = value
  emit('update:modelValue', next)
}
const setCondition = (patch: Schema) => set('visibleWhen', { ...condition.value, ...patch })
const addChild = (key: Collection) => set(key, [...children(key), { code: '', label: '', type: 'TEXT' }])
const updateChild = (input: { key: Collection; index: number; value: Schema }) =>
  set(input.key, children(input.key).map((child, index) => index === input.index ? input.value : child))
const removeChild = (input: { key: Collection; index: number }) =>
  set(input.key, children(input.key).filter((_, index) => index !== input.index))
const updateOption = (input: { index: number; label?: string; value?: unknown }) => {
  const next = options.value.map((option, index) => {
    if (index !== input.index) return option
    if (isObject(option)) return { ...option, ...('label' in input ? { label: input.label } : { value: input.value }) }
    return input.value
  })
  set('options', next)
}

// 值类型切换必须由用户确认，不使用字段类型推断或改写已有选项。
const scalarType = (value: unknown) => value == null ? 'NULL' : typeof value === 'number' ? 'NUMBER' : typeof value === 'boolean' ? 'BOOLEAN' : 'TEXT'
const ScalarInput = defineComponent({
  props: { modelValue: { required: true }, disabled: Boolean, label: { type: String, required: true } },
  emits: ['update:modelValue'],
  setup(inputProps, { emit: scalarEmit }) {
    const update = (value: unknown) => { if (!inputProps.disabled) scalarEmit('update:modelValue', value) }
    const changeType = async (type: string) => {
      if (inputProps.disabled || type === scalarType(inputProps.modelValue)) return
      const old = inputProps.modelValue
      let next: unknown
      if (type === 'TEXT') next = old == null ? '' : String(old)
      else if (type === 'NULL') next = null
      else if (type === 'NUMBER') {
        if (old == null || old === '') next = 0
        else if (typeof old === 'boolean') next = old ? 1 : 0
        else if (Number.isFinite(Number(old))) next = Number(old)
        else { ElMessage.warning('当前值不能转换为数字，请先修改值。'); return }
      } else if (old == null || old === '') next = false
      else if (old === true || old === 'true' || old === 1) next = true
      else if (old === false || old === 'false' || old === 0) next = false
      else { ElMessage.warning('布尔值仅支持 true、false、1 或 0，请先修改值。'); return }
      try {
        await ElMessageBox.confirm(`将${inputProps.label}从「${displayValue(old)}」转换为${type}「${displayValue(next)}」？`, '确认值类型转换', { confirmButtonText: '转换', cancelButtonText: '保留' })
        update(next)
      } catch { /* 取消转换时保留原值和类型。 */ }
    }
    return () => {
      const type = scalarType(inputProps.modelValue)
      const common = { disabled: inputProps.disabled, 'aria-label': inputProps.label }
      const control = type === 'NUMBER'
        ? h(ElInputNumber, { ...common, modelValue: inputProps.modelValue as number, controlsPosition: 'right', 'onUpdate:modelValue': (value: number | undefined) => update(value ?? null) })
        : type === 'BOOLEAN'
          ? h(ElSelect, { ...common, modelValue: inputProps.modelValue as boolean, 'onUpdate:modelValue': update }, () => [h(ElOption, { label: '是（true）', value: true }), h(ElOption, { label: '否（false）', value: false })])
          : type === 'NULL' ? h('span', { class: 'help' }, '空值（null）')
            : h(ElInput, { ...common, modelValue: text(inputProps.modelValue), 'onUpdate:modelValue': update })
      return h('div', { class: 'scalar-input' }, [
        h(ElSelect, { disabled: inputProps.disabled, modelValue: type, 'aria-label': `${inputProps.label}类型`, 'onUpdate:modelValue': changeType }, () => ['TEXT', 'NUMBER', 'BOOLEAN', 'NULL'].map(value => h(ElOption, { value, label: value }))),
        control
      ])
    }
  }
})
</script>

<style scoped>
.schema-node { min-width: 0; }
.node-properties, .condition-properties { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
label { display: flex; flex-direction: column; gap: 4px; color: var(--el-text-color-regular); font-size: 13px; }
.source, .condition { margin: 16px 0; }
.source { padding: 12px; background: var(--el-fill-color-light); border-radius: var(--el-border-radius-base); overflow-wrap: anywhere; }
.source > span { margin-left: 12px; }
.source dl { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; margin: 8px 0 0; }
.source dd { margin: 0; }
.section-actions, .child-toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.child-toolbar { justify-content: flex-end; }
.add-action { margin-bottom: 12px; }
.help { color: var(--el-text-color-secondary); font-size: 13px; }
:deep(.scalar-input) { display: grid; grid-template-columns: 112px minmax(0, 1fr); align-items: center; gap: 8px; }
:deep(.el-select), :deep(.el-input-number) { width: 100%; }
:deep(.el-collapse-item__content) { padding: 12px; }
@media (max-width: 767px) {
  .node-properties, .condition-properties { grid-template-columns: 1fr; }
  :deep(.scalar-input) { grid-template-columns: 1fr; }
}
</style>
