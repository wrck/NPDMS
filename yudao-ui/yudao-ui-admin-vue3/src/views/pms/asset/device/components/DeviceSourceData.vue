<template>
  <el-empty v-if="data == null" :image-size="80" :description="emptyText" />
  <template v-else>
    <el-empty v-if="!records.length" :image-size="80" :description="emptyText" />
    <DeviceReadOnlyFields
      v-for="(record, index) in records"
      :key="index"
      :title="records.length > 1 ? `${title} ${index + 1}` : title"
      :fields="fieldsOf(record)"
    />
  </template>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
const props = defineProps<{ data: unknown; title: string; emptyText: string }>()
const records = computed(() => (Array.isArray(props.data) ? props.data : [props.data]))
const fieldsOf = (record: unknown) => {
  const entries =
    record !== null && typeof record === 'object' && !Array.isArray(record)
      ? Object.entries(record)
      : [['内容', record] as const]
  return entries.map(([label, value]) => ({
    label,
    value,
    multiline:
      (value !== null && typeof value === 'object') ||
      (typeof value === 'string' && (value.includes('\n') || value.length > 100))
  }))
}
</script>
