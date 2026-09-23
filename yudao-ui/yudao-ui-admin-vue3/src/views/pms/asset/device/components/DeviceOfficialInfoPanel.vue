<template>
  <DeviceSourceStatus :slice="slice" />
  <DeviceSourceData :data="slice.data" title="官网信息" empty-text="官网信息未维护或来源不可用" />
</template>
<script setup lang="ts">
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceSourceData from './DeviceSourceData.vue'
defineProps<{ slice: DeviceSourceSliceVO }>()
</script>
