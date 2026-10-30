<template>
  <div class="business-table" :class="{ 'business-table--fit': fitPage }">
    <el-table :data="modelValue || []" border size="small">
      <el-table-column v-for="column in props.columns" :key="column.field" :label="column.label" :min-width="fitPage ? 1 : column.width">
        <template #default="{ row, $index }">
          <span v-if="disabled">{{ row[column.field] || '—' }}</span>
          <el-input v-else :model-value="row[column.field]" :aria-label="`${column.label} 第${$index + 1}行`" @update:model-value="updateCell({ index: $index, field: column.field, value: $event })" />
        </template>
      </el-table-column>
      <el-table-column v-if="!disabled" label="操作" width="70" fixed="right">
        <template #default="{ $index }"><el-button link type="danger" @click="removeRow($index)">删除</el-button></template>
      </el-table-column>
    </el-table>
    <el-button v-if="!disabled" class="mt-8px" @click="addRow">添加行</el-button>
  </div>
</template>
<script setup lang="ts">
type BusinessRow = Record<string, unknown>
const props = defineProps<{ modelValue?: BusinessRow[]; disabled?: boolean; fitPage?: boolean; columns: { field: string; label: string; width?: number }[] }>()
const emit = defineEmits<{ 'update:modelValue': [rows: BusinessRow[]] }>()
const updateCell = ({ index, field, value }: { index: number; field: string; value: string }) => {
  if (props.disabled) return
  emit('update:modelValue', (props.modelValue || []).map((row, i) => i === index ? { ...row, [field]: value } : row))
}
const addRow = () => {
  if (!props.disabled) emit('update:modelValue', [...(props.modelValue || []), Object.fromEntries(props.columns.map(column => [column.field, '']))])
}
const removeRow = (index: number) => {
  if (!props.disabled) emit('update:modelValue', (props.modelValue || []).filter((_, i) => i !== index))
}
</script>
<style scoped>
.business-table--fit { width: 100%; max-width: 100%; min-width: 0; }
.business-table--fit :deep(.cell) { padding: 4px; overflow-wrap: anywhere; white-space: normal; }
.business-table--fit :deep(.el-input) { width: 100%; min-width: 0; }
.business-table--fit :deep(.el-input__wrapper) { min-width: 0; padding: 1px 4px; }
</style>
