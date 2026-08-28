<template>
  <el-empty v-if="!history.length" description="暂无变更历史" />
  <el-timeline v-else>
    <el-timeline-item v-for="item in history" :key="item.operationId" :timestamp="item.occurredAt">
      <strong>{{ item.fieldName }}</strong>
      <span class="ml-8px text-gray-500">Owner：{{ item.fieldOwner }}</span>
      <div>{{ item.beforeValueDigest || '-' }} → {{ item.afterValueDigest || '-' }}</div>
    </el-timeline-item>
  </el-timeline>
</template>

<script setup lang="ts">
import type { CustomerHistoryVO } from '@/api/pms/customer'

defineProps<{ history: CustomerHistoryVO[] }>()
</script>
