<template>
  <el-table :data="fields" class="integration-table" empty-text="此记录没有字段明细">
    <el-table-column prop="field" label="字段" min-width="140" />
    <el-table-column prop="before" label="处理前" min-width="180" />
    <el-table-column prop="after" label="来源转换值" min-width="180" />
    <el-table-column label="差异" width="90">
      <template #default="{ row }">
        <el-tag v-if="row.changed" type="warning" size="small">有变化</el-tag>
        <span v-else>一致</span>
      </template>
    </el-table-column>
  </el-table>
</template>
<script setup lang="ts">
import type { Change } from '@/api/pms/integration'
import { fieldChanges } from './changePresentation'
const props = defineProps<{ change: Change }>()
const fields = computed(() => fieldChanges(props.change))
</script>
