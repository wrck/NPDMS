<template>
  <section class="device-section">
    <div class="section-heading">{{ title }}</div>
    <el-form label-position="top" disabled>
      <div class="form-grid">
        <el-form-item
          v-for="field in fields"
          :key="field.label"
          :label="field.label"
          :class="{ 'field-wide': field.multiline }"
        >
          <el-input
            :model-value="displayValue(field.value)"
            :type="field.multiline ? 'textarea' : 'text'"
            :rows="3"
          />
        </el-form-item>
      </div>
    </el-form>
    <slot></slot>
  </section>
</template>
<script setup lang="ts">
defineProps<{
  title: string
  fields: Array<{ label: string; value: unknown; multiline?: boolean }>
}>()
const displayValue = (value: unknown): string => {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}
</script>
<style scoped>
.device-section {
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.section-heading {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 14px;
}

.field-wide {
  grid-column: 1 / -1;
}

@media (width <= 600px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
