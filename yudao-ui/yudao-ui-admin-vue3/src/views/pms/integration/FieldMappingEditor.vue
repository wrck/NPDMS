<template>
  <div class="overflow-auto">
    <el-alert
      v-if="errors.size"
      title="枚举映射 JSON 无效，请修正后保存。"
      type="error"
      :closable="false"
    />
    <el-table :data="model" class="integration-table" empty-text="请先选择业务适配器">
      <el-table-column label="目标字段" min-width="180"
        ><template #default="{ row }">
          <el-select v-model="row.target" filterable :aria-label="row.target + '目标字段'">
            <el-option
              v-for="field in fields"
              :key="field.name"
              :value="field.name"
              :label="`${field.required ? '* ' : ''}${field.label} (${field.name})`"
            />
          </el-select> </template
      ></el-table-column>
      <el-table-column label="来源字段" min-width="160"
        ><template #default="{ row }">
          <el-select
            v-model="row.source"
            filterable
            allow-create
            default-first-option
            :disabled="row.conversion === 'CONSTANT'"
            :aria-label="row.target + '来源字段'"
          >
            <el-option v-for="column in columns" :key="column" :value="column" :label="column" />
          </el-select> </template
      ></el-table-column>
      <el-table-column label="转换" min-width="160"
        ><template #default="{ row }">
          <el-select v-model="row.conversion" :aria-label="row.target + '转换方式'"
            ><el-option v-for="c in conversions" :key="c.value" :value="c.value" :label="c.label"
          /></el-select> </template
      ></el-table-column>
      <el-table-column label="常量 / 默认值 / 枚举映射" min-width="230"
        ><template #default="{ row }">
          <el-input
            v-if="['ENUM', 'LOOKUP'].includes(row.conversion)"
            :model-value="enumDrafts.get(row) ?? JSON.stringify(row.values || {})"
            :aria-label="row.target + '枚举映射'"
            @update:model-value="setEnum(row, $event)"
            placeholder="输入枚举映射 JSON"
          />
          <el-input
            v-else-if="row.conversion === 'CONSTANT'"
            :model-value="String(row.constant ?? '')"
            :aria-label="row.target + '常量'"
            @update:model-value="row.constant = $event"
          />
          <el-input
            v-else
            :model-value="String(row.defaultValue ?? '')"
            :aria-label="row.target + '默认值'"
            @update:model-value="row.defaultValue = $event === '' ? null : $event"
            placeholder="来源为 NULL 时使用；可留空"
          /> </template
      ></el-table-column>
      <el-table-column label="操作" width="80" fixed="right"
        ><template #default="{ $index }">
          <el-button link type="danger" :aria-label="'删除' + $index + '行映射'" @click="removeRow($index)"
            >删除</el-button
          > </template
      ></el-table-column>
    </el-table>
    <div class="mt-8px">
      <el-button :disabled="!canAdd" @click="addRow"
        ><Icon icon="ep:plus" class="mr-5px" />新增映射</el-button
      >
      <span v-if="!canAdd" class="integration-note">全部目标字段均已映射</span>
    </div>
    <p class="integration-note"
      >枚举使用 JSON 对象，NULL 表示来源空值。引用字段填写对应对象的源主键，不填写目标数据库 ID。</p
    >
  </div>
</template>
<script setup lang="ts">
import type { Mapping } from '@/api/pms/integration'
const model = defineModel<Mapping[]>({ required: true })
const props = defineProps<{
  columns: string[]
  fields: { name: string; label: string; type?: string; required?: boolean }[]
}>()
const errors = reactive(new Set<Mapping>())
const enumDrafts = reactive(new Map<Mapping, string>())
defineExpose({
  validate: () => !model.value.some((row) => ['ENUM', 'LOOKUP'].includes(row.conversion) && errors.has(row))
})
// 读取来源字段后，按同名自动回填尚未指定来源的映射行；不覆盖已手工选择的来源。
watch(
  () => props.columns,
  (cols) => {
    if (!cols?.length) return
    for (const row of model.value) {
      if (!row.source && row.conversion !== 'CONSTANT' && cols.includes(row.target)) {
        row.source = row.target
      }
    }
  },
  { immediate: true }
)
const canAdd = computed(
  () => !!props.fields.length && props.fields.some((f) => !model.value.some((m) => m.target === f.name))
)
const addRow = () => {
  const field = props.fields.find((f) => !model.value.some((m) => m.target === f.name))
  if (!field) return
  model.value.push({
    target: field.name,
    source: props.columns.includes(field.name) ? field.name : '',
    conversion: field.type === 'REFERENCE' ? 'REFERENCE' : 'DIRECT'
  })
}
const removeRow = (index: number) => {
  const [row] = model.value.splice(index, 1)
  if (row) {
    errors.delete(row)
    enumDrafts.delete(row)
  }
}
const conversions = [
  { value: 'DIRECT', label: '直接映射' },
  { value: 'STRING', label: '转为文本' },
  { value: 'TRIM', label: '去首尾空格' },
  { value: 'LONG', label: '转为整数' },
  { value: 'DECIMAL', label: '转为小数' },
  { value: 'BOOLEAN', label: '转为布尔' },
  { value: 'DATETIME', label: '日期时间' },
  { value: 'ENUM', label: '枚举转换' },
  { value: 'LOOKUP', label: '显式映射（未匹配留空）' },
  { value: 'CONSTANT', label: '固定常量' },
  { value: 'REFERENCE', label: '来源关系引用' }
]
const setEnum = (row: Mapping, value: string) => {
  enumDrafts.set(row, value)
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error()
    row.values = parsed
    errors.delete(row)
  } catch {
    errors.add(row)
  }
}
</script>
