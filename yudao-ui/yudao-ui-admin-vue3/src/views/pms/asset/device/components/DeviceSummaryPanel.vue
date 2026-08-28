<template>
  <el-descriptions :column="columns" border>
    <el-descriptions-item label="设备序列号">{{ summary.sn }}</el-descriptions-item>
    <el-descriptions-item label="产品">{{ productText }}</el-descriptions-item>
    <el-descriptions-item label="当前项目">{{ summary.projectId || '--' }}</el-descriptions-item>
    <el-descriptions-item label="当前客户">{{ summary.customerId || '--' }}</el-descriptions-item>
    <el-descriptions-item label="当前位置">--</el-descriptions-item>
    <el-descriptions-item label="待核对">{{ reconciliationText }}</el-descriptions-item>
    <el-descriptions-item label="项目归属版本">{{
      summary.projectAssignmentVersion
    }}</el-descriptions-item>
    <el-descriptions-item label="客户归属版本">{{
      summary.customerAssignmentVersion
    }}</el-descriptions-item>
  </el-descriptions>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useWindowSize } from '@vueuse/core'
import type { DeviceSummaryVO } from '@/api/pms/asset/device'
const props = defineProps<{ summary: DeviceSummaryVO }>()
const { width } = useWindowSize()
const columns = computed(() => (width.value < 768 ? 1 : width.value < 1200 ? 2 : 4))
const productText = computed(
  () =>
    [props.summary.productCode, props.summary.productModel, props.summary.productName]
      .filter(Boolean)
      .join(' / ') || '--'
)
const reconciliationText = computed(() =>
  props.summary.projectId && props.summary.customerId ? '以服务端核对状态为准' : '无待核对事实'
)
</script>
