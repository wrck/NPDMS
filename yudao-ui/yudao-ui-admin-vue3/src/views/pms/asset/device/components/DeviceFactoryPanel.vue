<template>
  <DeviceSourceStatus :slice="slice" />
  <DeviceSourceData :data="slice.data" title="出厂信息" empty-text="暂无可展示的出厂信息" />
</template>
<script setup lang="ts">
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceSourceData from './DeviceSourceData.vue'
defineProps<{ slice: DeviceSourceSliceVO }>()
</script>
