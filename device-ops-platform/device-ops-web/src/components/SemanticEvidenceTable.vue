<script setup lang="ts">
import type { SemanticEvidenceRow } from '@/utils/semantic-result'
import { displaySemanticValue } from '@/utils/semantic-result'

defineProps<{ rows: SemanticEvidenceRow[] }>()
</script>

<template>
  <el-empty v-if="!rows.length" :image-size="40" description="暂无字段证据" />
  <el-table v-else :data="rows" size="small" max-height="360" table-layout="fixed">
    <el-table-column prop="semanticKey" label="语义字段" min-width="170" show-overflow-tooltip />
    <el-table-column label="值" min-width="180" show-overflow-tooltip>
      <template #default="{ row }">{{ displaySemanticValue(row.value) }}</template>
    </el-table-column>
    <el-table-column prop="commandText" label="来源命令" min-width="120" show-overflow-tooltip />
    <el-table-column label="层级" width="76">
      <template #default="{ row }">{{ row.nestingDepth ? `嵌套 ${row.nestingDepth}` : '直接' }}</template>
    </el-table-column>
    <el-table-column prop="nestedCommandText" label="子命令" min-width="120" show-overflow-tooltip>
      <template #default="{ row }">{{ row.nestedCommandText || '—' }}</template>
    </el-table-column>
    <el-table-column label="源行" width="84">
      <template #default="{ row }">{{ row.lineStart ?? '—' }}<template v-if="row.lineEnd && row.lineEnd !== row.lineStart">–{{ row.lineEnd }}</template></template>
    </el-table-column>
    <el-table-column prop="ruleId" label="规则" min-width="130" show-overflow-tooltip />
    <el-table-column label="置信度" width="78">
      <template #default="{ row }">{{ typeof row.confidence === 'number' ? `${Math.round(row.confidence * 100)}%` : '—' }}</template>
    </el-table-column>
  </el-table>
</template>
