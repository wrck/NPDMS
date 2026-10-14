<template>
  <div v-if="schema.columns?.length" class="table-field">
    <div class="table-scroll">
      <el-table :data="rows" border :max-height="480" empty-text="暂无记录，请添加一行">
        <el-table-column v-for="column in schema.columns" :key="column.code" :label="column.label || column.name || column.code" min-width="180">
          <template #header>{{ column.label || column.name || column.code }}<span v-if="column.required" class="required"> *</span></template>
          <template #default="{ row, $index }">
            <CutoverSchemaInput v-if="visible(column, row)" :schema="column" :model-value="row[column.code]" :disabled="disabled" @update:model-value="updateRow($index, column.code, $event)" />
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!disabled" label="操作" width="76" fixed="right"><template #default="{ $index }"><el-button link type="danger" @click="removeRow($index)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-button v-if="!disabled" class="mt-8px" @click="emit('update:modelValue', [...rows, {}])"><Icon icon="ep:plus" />添加一行</el-button>
  </div>
  <div v-else-if="schema.fields?.length" class="schema-fields">
    <template v-for="field in schema.fields" :key="field.code">
      <div v-if="visible(field, object)" class="schema-field" :class="{ 'full-width': field.columns?.length }">
        <label>{{ field.label || field.name || field.code }}<span v-if="field.required" class="required"> *</span></label>
        <CutoverSchemaInput :schema="field" :model-value="object[field.code]" :disabled="disabled" @update:model-value="emit('update:modelValue', { ...object, [field.code]: $event })" />
      </div>
    </template>
  </div>
  <el-select v-else-if="schema.options?.length" :model-value="modelValue" :disabled="disabled" clearable placeholder="请选择" @update:model-value="emit('update:modelValue', $event)">
    <el-option v-for="option in options" :key="String(option.value)" :label="option.label" :value="option.value" />
  </el-select>
  <el-radio-group v-else-if="type === 'BOOLEAN'" :model-value="modelValue" :disabled="disabled" @update:model-value="emit('update:modelValue', $event)">
    <el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio>
  </el-radio-group>
  <el-input-number v-else-if="type === 'NUMBER'" :model-value="modelValue ?? undefined" :disabled="disabled" controls-position="right" @update:model-value="emit('update:modelValue', $event ?? null)" />
  <el-input v-else :model-value="modelValue ?? ''" :readonly="disabled" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" :maxlength="schema.maxLength" placeholder="请填写核查事实" @update:model-value="emit('update:modelValue', $event)" />
</template>

<script setup lang="ts">
export interface FieldSchema {
  code?: string
  name?: string
  label?: string
  type?: string
  required?: boolean
  maxLength?: number
  visibleWhen?: { field: string; equals: unknown }
  fields?: Array<FieldSchema & { code: string }>
  columns?: Array<FieldSchema & { code: string }>
  options?: Array<string | { label: string; value: string | number | boolean }>
}
const props = defineProps<{ schema: FieldSchema; modelValue: any; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: any] }>()
const type = computed(() => props.schema.type?.toUpperCase())
const object = computed<Record<string, any>>(() => props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue) ? props.modelValue : {})
const rows = computed<Array<Record<string, any>>>(() => Array.isArray(props.modelValue) ? props.modelValue : Array.isArray(props.modelValue?.rows) ? props.modelValue.rows : [])
const options = computed(() => (props.schema.options || []).map(o => typeof o === 'string' ? { value: o, label: o } : o))
const visible = (field: FieldSchema, values: Record<string, any>) => !field.visibleWhen || values[field.visibleWhen.field] === field.visibleWhen.equals
const updateRow = (index: number, key: string, value: any) => emit('update:modelValue', rows.value.map((row, i) => i === index ? { ...row, [key]: value } : row))
const removeRow = (index: number) => emit('update:modelValue', rows.value.filter((_, i) => i !== index))
</script>

<style scoped>
.schema-fields { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
.schema-field>label { display:block; font-size:13px; margin-bottom:8px; color:var(--el-text-color-regular); }
.schema-field.full-width { grid-column:1/-1; }
.required { color:var(--el-color-danger); }
.table-field,.table-scroll { min-width:0; max-width:100%; }
.table-scroll { overflow-x:auto; }
.el-select,.el-input-number { width:100%; }
@media (max-width:767px) { .schema-fields { grid-template-columns:1fr; } }
</style>
