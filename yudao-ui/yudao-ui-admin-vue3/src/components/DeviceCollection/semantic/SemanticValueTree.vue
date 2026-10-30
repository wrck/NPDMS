<script setup lang="ts">
import { computed, ref } from 'vue'

defineOptions({ name: 'SemanticValueTree' })

const props = withDefaults(
  defineProps<{
    value: unknown
    level?: number
    path?: string
  }>(),
  {
    level: 0,
    path: 'root'
  }
)

const isArray = computed(() => Array.isArray(props.value))
const isObject = computed(
  () => Boolean(props.value) && typeof props.value === 'object' && !Array.isArray(props.value)
)
const isDepthLimited = computed(() => props.level >= 4 && (isArray.value || isObject.value))
const openItems = ref<number[]>([])
function toggleItem(index: number, event: Event) {
  const opened = (event.target as HTMLDetailsElement).open
  openItems.value = opened
    ? [...new Set([...openItems.value, index])]
    : openItems.value.filter((item) => item !== index)
}

function scalarText(value: unknown): string {
  if (value == null) return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}

function itemSummary(value: unknown, index: number): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return `#${index + 1}`
  const record = value as Record<string, unknown>
  const label =
    typeof record.command === 'string' && record.command.trim()
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
  <pre v-if="isDepthLimited" class="semantic-value-tree__raw">{{
    JSON.stringify(value, null, 2)
  }}</pre>
  <div v-else-if="isArray" class="semantic-value-tree__array">
    <span class="semantic-value-tree__count">{{ (value as unknown[]).length }} 项</span>
    <div v-if="(value as unknown[]).length">
      <details
        v-for="(item, index) in value as unknown[]"
        :key="`${path}-${index}`"
        @toggle="toggleItem(index, $event)"
      >
        <summary>{{ itemSummary(item, index) }}</summary>
        <SemanticValueTree
          v-if="openItems.includes(index)"
          :value="item"
          :level="level + 1"
          :path="`${path}.${index}`"
        />
      </details>
    </div>
  </div>
  <dl v-else-if="isObject" class="semantic-value-tree__object">
    <div v-for="(item, field) in value as Record<string, unknown>" :key="String(field)">
      <dt>{{ String(field) }}</dt>
      <dd
        ><SemanticValueTree :value="item" :level="level + 1" :path="`${path}.${String(field)}`"
      /></dd>
    </div>
  </dl>
  <span v-else class="semantic-value-tree__scalar">{{ scalarText(value) }}</span>
</template>

<style scoped>
.semantic-value-tree__object {
  margin: 0;
  border: 1px solid var(--el-border-color);
}

.semantic-value-tree__object > div {
  display: grid;
  grid-template-columns: minmax(100px, 28%) minmax(0, 1fr);
  border-bottom: 1px solid var(--el-border-color);
}

.semantic-value-tree__object > div:last-child {
  border-bottom: 0;
}

.semantic-value-tree__object dt,
.semantic-value-tree__object dd {
  padding: 6px 10px;
  margin: 0;
  overflow-wrap: anywhere;
}

.semantic-value-tree__object dt {
  background: var(--el-fill-color-light);
}

.semantic-value-tree__array {
  display: grid;
  gap: 0.375rem;
  min-width: 0;
}

.semantic-value-tree__count {
  font-size: 0.75rem;
  color: var(--slate);
}

.semantic-value-tree__scalar {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.semantic-value-tree__raw {
  max-height: 18rem;
  margin: 0;
  overflow: auto;
  font-size: 0.75rem;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
