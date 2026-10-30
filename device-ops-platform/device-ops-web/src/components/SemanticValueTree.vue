<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'SemanticValueTree' })

const props = withDefaults(defineProps<{
  value: unknown
  level?: number
  path?: string
}>(), {
  level: 0,
  path: 'root'
})

const isArray = computed(() => Array.isArray(props.value))
const isObject = computed(() => Boolean(props.value)
  && typeof props.value === 'object'
  && !Array.isArray(props.value))
const isDepthLimited = computed(() => props.level >= 4 && (isArray.value || isObject.value))

function scalarText(value: unknown): string {
  if (value == null) return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}

function itemSummary(value: unknown, index: number): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return `#${index + 1}`
  const record = value as Record<string, unknown>
  const label = typeof record.command === 'string' && record.command.trim()
    ? record.command
    : typeof record.header === 'string' && record.header.trim()
      ? record.header
      : `#${index + 1}`
  const start = typeof record.startLine === 'number' ? record.startLine : undefined
  const end = typeof record.endLine === 'number' ? record.endLine : undefined
  if (start == null) return label
  return `${label} · 行 ${start}${end != null && end !== start ? `–${end}` : ''}`
}
</script>

<template>
  <pre v-if="isDepthLimited" class="semantic-value-tree__raw">{{ JSON.stringify(value, null, 2) }}</pre>
  <div v-else-if="isArray" class="semantic-value-tree__array">
    <span class="semantic-value-tree__count">{{ (value as unknown[]).length }} 项</span>
    <el-collapse v-if="(value as unknown[]).length">
      <el-collapse-item
        v-for="(item, index) in value as unknown[]"
        :key="`${path}-${index}`"
        :name="`${path}-${index}`"
        :title="itemSummary(item, index)"
      >
        <SemanticValueTree :value="item" :level="level + 1" :path="`${path}.${index}`" />
      </el-collapse-item>
    </el-collapse>
  </div>
  <el-descriptions v-else-if="isObject" :column="1" size="small" border>
    <el-descriptions-item
      v-for="(item, field) in value as Record<string, unknown>"
      :key="String(field)"
      :label="String(field)"
    >
      <SemanticValueTree :value="item" :level="level + 1" :path="`${path}.${String(field)}`" />
    </el-descriptions-item>
  </el-descriptions>
  <span v-else class="semantic-value-tree__scalar">{{ scalarText(value) }}</span>
</template>

<style scoped>
.semantic-value-tree__array { display: grid; gap: 0.375rem; min-width: 0; }
.semantic-value-tree__count { color: var(--slate); font-size: 0.75rem; }
.semantic-value-tree__scalar { overflow-wrap: anywhere; white-space: pre-wrap; }
.semantic-value-tree__raw { max-height: 18rem; overflow: auto; margin: 0; font-size: 0.75rem; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
