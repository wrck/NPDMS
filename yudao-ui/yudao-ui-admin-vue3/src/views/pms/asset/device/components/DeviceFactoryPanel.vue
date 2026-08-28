<template>
  <DeviceSourceStatus :slice="slice" />
  <el-empty v-if="!slice.data" description="暂无可展示的出厂信息" />
  <pre v-else class="slice-data">{{ slice.data }}</pre>
</template>
<script setup lang="ts">
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
defineProps<{ slice: DeviceSourceSliceVO }>()
</script>
<style scoped>
.slice-data {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
