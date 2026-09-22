<template>
  <DeviceSourceStatus :slice="slice" />
  <el-alert
    title="公告匹配以CONP原始版本、类型、系列和标记为主，无法可靠判断时应返回UNDETERMINED。"
    type="info"
    :closable="false"
    class="mb-16px"
  />
  <DeviceSourceData
    :data="slice.data"
    title="技术公告"
    empty-text="技术公告来源不可用或暂无匹配结果"
  />
</template>
<script setup lang="ts">
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceSourceData from './DeviceSourceData.vue'
defineProps<{ slice: DeviceSourceSliceVO }>()
</script>
