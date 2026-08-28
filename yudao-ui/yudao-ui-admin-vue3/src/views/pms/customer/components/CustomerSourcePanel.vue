<template>
  <el-descriptions :column="2" border>
    <el-descriptions-item label="来源">{{ sourceTypeLabel }}</el-descriptions-item>
    <el-descriptions-item label="同步状态">{{ customer.syncStatus || '-' }}</el-descriptions-item>
    <el-descriptions-item label="对账状态">{{
      customer.reconciliationPending ? '待对账' : '无需对账'
    }}</el-descriptions-item>
    <el-descriptions-item
      v-if="customer.sourceType === 'PLATFORM_TEMPORARY'"
      label="临时客户原因"
    >
      {{ customer.temporaryReason || '-' }}
    </el-descriptions-item>
    <el-descriptions-item label="联系电话">{{ customer.contactPhone || '-' }}</el-descriptions-item>
    <el-descriptions-item label="联系邮箱">{{ customer.contactEmail || '-' }}</el-descriptions-item>
  </el-descriptions>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { CustomerDetailRespVO } from '@/api/pms/customer'
const props = defineProps<{ customer: CustomerDetailRespVO }>()
const sourceTypeLabel = computed(
  () =>
    ({
      CRM_SYNC: 'CRM 同步',
      PLATFORM_CREATED: '平台创建',
      PLATFORM_TEMPORARY: '平台临时'
    })[props.customer.sourceType]
)
</script>
