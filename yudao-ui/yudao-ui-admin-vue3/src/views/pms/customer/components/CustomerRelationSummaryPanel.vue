<template>
  <el-alert v-if="!slice.available" type="warning" :closable="false" title="Owner 摘要暂不可用" />
  <div v-else>
    <div class="mb-8px text-13px text-gray-500">数据截止时间：{{ slice.dataAsOf || '-' }}</div>
    <el-table :data="slice.items">
      <el-table-column :prop="kind === 'project' ? 'projectCode' : 'deviceCode'" label="编码" />
      <el-table-column :prop="kind === 'project' ? 'projectName' : 'deviceName'" label="名称" />
      <el-table-column prop="status" label="状态" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { CustomerRelationSummarySlice } from '@/api/pms/customer'

defineProps<{ slice: CustomerRelationSummarySlice; kind: 'project' | 'device' }>()
</script>
