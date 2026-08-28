<template>
  <DeviceSourceStatus :slice="slice" />
  <el-empty v-if="!slice.data" description="官网信息未维护或来源不可用" />
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
