<template>
  <div class="overflow-auto">
    <el-alert
      v-if="errors.size"
      title="枚举映射 JSON 无效，请修正后保存。"
      type="error"
      :closable="false"
    />
    <el-table :data="model" class="integration-table" empty-text="请先选择业务适配器">
      <el-table-column label="目标字段" min-width="160"
        ><template #default="{ row }">{{
          fields.find((f) => f.name === row.target)?.label || row.target
        }}</template></el-table-column
      >
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
            v-if="row.conversion === 'ENUM'"
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
    </el-table>
    <p class="integration-note"
      >枚举使用 JSON 对象，NULL 表示来源空值。引用字段填写对应对象的源主键，不填写目标数据库 ID。</p
    >
  </div>
</template>
<script setup lang="ts">
import type { Mapping } from '@/api/pms/integration'
const model = defineModel<Mapping[]>({ required: true })
defineProps<{ columns: string[]; fields: { name: string; label: string }[] }>()
const errors = reactive(new Set<Mapping>())
const enumDrafts = reactive(new Map<Mapping, string>())
defineExpose({
  validate: () => !model.value.some((row) => row.conversion === 'ENUM' && errors.has(row))
})
const conversions = [
  { value: 'DIRECT', label: '直接映射' },
  { value: 'STRING', label: '转为文本' },
  { value: 'TRIM', label: '去首尾空格' },
  { value: 'LONG', label: '转为整数' },
  { value: 'DECIMAL', label: '转为小数' },
  { value: 'BOOLEAN', label: '转为布尔' },
  { value: 'DATETIME', label: '日期时间' },
  { value: 'ENUM', label: '枚举转换' },
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
