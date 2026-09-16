<!-- src/components/ConnectorDesigner/JsonTree.vue -->
<script setup lang="ts">
/**
 * 通用 JSON 树形展示组件（可折叠）。
 *
 * <p>用于测试控制台响应 Body 的可视化展示。递归渲染对象/数组/基本类型，
 * 支持点击 key 或对象头部折叠/展开。</p>
 */
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    data: unknown
    /** 当前层级（用于缩进） */
    level?: number
    /** 节点 key（根节点不显示） */
    name?: string
    /** 默认是否展开（仅对 object/array 生效） */
    defaultExpanded?: boolean
  }>(),
  {
    level: 0,
    defaultExpanded: true
  }
)

const expanded = ref(props.defaultExpanded)

function toggle() {
  expanded.value = !expanded.value
}

const isObject = computed(() => props.data !== null && typeof props.data === 'object')
const isArray = computed(() => Array.isArray(props.data))
const isPlainObject = computed(() => isObject.value && !isArray.value)

const entries = computed<[string, unknown][]>(() => {
  if (!isObject.value) return []
  if (isArray.value) {
    return (props.data as unknown[]).map((v, i) => [String(i), v] as [string, unknown])
  }
  return Object.entries(props.data as Record<string, unknown>)
})

const size = computed(() => entries.value.length)

const valuePreview = computed(() => {
  if (isObject.value) {
    return isArray.value ? `Array(${size.value})` : `{${size.value} keys}`
  }
  if (typeof props.data === 'string') return `"${props.data}"`
  if (props.data === null) return 'null'
  if (props.data === undefined) return 'undefined'
  return String(props.data)
})

const valueType = computed(() => {
  if (isArray.value) return 'array'
  if (isPlainObject.value) return 'object'
  if (props.data === null) return 'null'
  return typeof props.data
})

const valueClass = computed(() => {
  switch (valueType.value) {
    case 'string':
      return 'jt-string'
    case 'number':
    case 'bigint':
      return 'jt-number'
    case 'boolean':
      return 'jt-boolean'
    case 'null':
      return 'jt-null'
    default:
      return 'jt-other'
  }
})
</script>

<template>
  <div class="json-tree" :class="{ 'is-root': level === 0 }">
    <span v-if="name !== undefined" class="jt-key" @click="isObject && toggle()">
      <span v-if="isObject" class="jt-arrow" :class="{ collapsed: !expanded }">▶</span>
      <span v-else class="jt-arrow-placeholder"></span>
      "{{ name }}":
    </span>

    <template v-if="!isObject">
      <span :class="valueClass">{{ valuePreview }}</span>
    </template>
    <template v-else>
      <span v-if="size === 0" class="jt-empty">{{ isArray ? '[]' : '{}' }}</span>
      <template v-else>
        <span class="jt-toggle" @click="toggle">
          <span class="jt-arrow" :class="{ collapsed: !expanded }">▶</span>
          <span v-if="!expanded" class="jt-collapsed-preview">{{ valuePreview }}</span>
        </span>
        <div v-if="expanded" class="jt-children">
          <div v-for="[k, v] in entries" :key="k" class="jt-child">
            <JsonTree :data="v" :name="k" :level="level + 1" :default-expanded="true" />
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<script lang="ts">
export default { name: 'JsonTree' }
</script>

<style scoped lang="scss">
.json-tree {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
  &.is-root {
    padding: 4px 0;
  }
}
.jt-key {
  color: #871094;
  cursor: pointer;
  user-select: none;
}
.jt-arrow {
  display: inline-block;
  width: 14px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  transition: transform 0.15s;
  &.collapsed {
    transform: rotate(0deg);
  }
  &:not(.collapsed) {
    transform: rotate(90deg);
  }
}
.jt-arrow-placeholder {
  display: inline-block;
  width: 14px;
}
.jt-toggle {
  cursor: pointer;
  user-select: none;
  color: var(--el-text-color-regular);
}
.jt-collapsed-preview {
  color: var(--el-text-color-secondary);
  margin-left: 4px;
  font-style: italic;
}
.jt-children {
  padding-left: 22px;
  border-left: 1px dashed var(--el-border-color-lighter);
  margin-left: 6px;
}
.jt-child {
  padding-left: 4px;
}
.jt-empty {
  color: var(--el-text-color-placeholder);
}
.jt-string {
  color: #0b7500;
}
.jt-number {
  color: #1a01cc;
}
.jt-boolean {
  color: #c98a00;
}
.jt-null {
  color: #808080;
  font-style: italic;
}
.jt-other {
  color: var(--el-text-color-primary);
}
</style>
