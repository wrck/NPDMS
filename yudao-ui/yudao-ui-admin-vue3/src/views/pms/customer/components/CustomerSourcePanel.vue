<template>
  <el-descriptions :column="2" border
    ><el-descriptions-item label="来源">{{ customer.sourceType }}</el-descriptions-item
    ><el-descriptions-item label="同步状态">{{ customer.syncStatus || '-' }}</el-descriptions-item
    ><el-descriptions-item label="联系电话">{{ customer.contactPhone || '-' }}</el-descriptions-item
    ><el-descriptions-item label="联系邮箱">{{
      customer.contactEmail || '-'
    }}</el-descriptions-item></el-descriptions
  >
</template>
<script setup lang="ts">
import type { CustomerDetailRespVO } from '@/api/pms/customer'
defineProps<{ customer: CustomerDetailRespVO }>()
</script>
