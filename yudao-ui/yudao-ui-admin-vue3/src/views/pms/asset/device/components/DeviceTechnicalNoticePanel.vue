<template>
  <DeviceSourceStatus :slice="slice" />
  <el-alert
    title="公告匹配以CONP原始版本、类型、系列和标记为主，无法可靠判断时应返回UNDETERMINED。"
    type="info"
    :closable="false"
  />
  <el-empty v-if="!slice.data" description="技术公告来源不可用或暂无匹配结果" />
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
