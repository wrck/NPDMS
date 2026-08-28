<template>
  <DeviceSourceStatus :slice="slice" />
  <el-descriptions :column="2" border>
    <el-descriptions-item label="CONP原始版本">{{
      version.conpVersion || '--'
    }}</el-descriptions-item>
    <el-descriptions-item label="CONP类型">{{ version.conpType || '--' }}</el-descriptions-item>
    <el-descriptions-item label="CONP系列">{{ version.conpSeries || '--' }}</el-descriptions-item>
    <el-descriptions-item label="CONP标记">{{ version.conpMark || '--' }}</el-descriptions-item>
    <el-descriptions-item label="BOOT">{{ version.bootVersion || '--' }}</el-descriptions-item>
    <el-descriptions-item label="CPLD">{{ version.cpldVersion || '--' }}</el-descriptions-item>
    <el-descriptions-item label="PCB">{{ version.pcbVersion || '--' }}</el-descriptions-item>
    <el-descriptions-item label="定制版本">{{
      version.customized ? '是' : '否'
    }}</el-descriptions-item>
  </el-descriptions>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
const props = defineProps<{ slice: DeviceSourceSliceVO }>()
const version = computed(() => (props.slice.data || {}) as Record<string, unknown>)
</script>
