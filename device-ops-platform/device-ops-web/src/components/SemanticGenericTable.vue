<script setup lang="ts">
import type { GenericTableSection } from '@/types/parser'
defineProps<{ section: GenericTableSection }>()
</script>

<template>
  <div
    class="semantic-generic-table__scroll"
    tabindex="0"
    aria-label="可横向滚动的结构表格"
  >
    <el-table
      :data="section.rows"
      size="small"
      border
    >
      <el-table-column
        v-for="column in section.columns"
        :key="column.id"
        :label="column.label || column.id"
        min-width="120"
      >
        <template #default="scope">
          {{ scope.row.values[column.id] ?? '—' }}
        </template>
      </el-table-column>
    </el-table>
  </div>
  <p
    v-if="section.unparsedLines.length"
    class="semantic-generic-table__warning"
  >
    {{ section.unparsedLines.length }} 行未能按当前列边界解析
  </p>
</template>

<style scoped>
.semantic-generic-table__scroll { max-width: 100%; overflow-x: auto; }
.semantic-generic-table__scroll :deep(.el-table) { min-width: max-content; }
.semantic-generic-table__warning { margin: 0.5rem 0 0; color: var(--el-color-warning-dark-2); font-size: 0.75rem; }
</style>
