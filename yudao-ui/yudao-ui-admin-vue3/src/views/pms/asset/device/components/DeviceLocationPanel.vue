<template>
  <div v-loading="loading">
    <DeviceReadOnlyFields
      v-if="detail"
      title="当前位置"
      :fields="[
        { label: '设备', value: `${detail.sn} ${detail.name}` },
        { label: '解析状态', value: detail.locationResolutionStatus || 'UNRESOLVED' },
        { label: '站点', value: detail.siteId },
        { label: '站点位置', value: detail.siteLocationId },
        { label: '生效时间', value: detail.locationEffectiveFrom },
        { label: '来源安装记录', value: detail.locationRecordId },
        { label: '发生时位置快照', value: detail.locationSnapshot, multiline: true }
      ]"
    />
    <el-empty
      v-if="!loading && !locationHistory.length"
      :image-size="80"
      description="暂无位置变更历史"
    />
    <DeviceReadOnlyFields
      v-for="(item, index) in locationHistory"
      :key="item.id"
      :title="`位置变更记录 ${index + 1}`"
      :fields="[
        { label: '变更时间', value: item.createTime },
        { label: '变更类型', value: item.changeType },
        { label: '变更说明', value: item.changeDescription, multiline: true }
      ]"
    />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { DeviceArchiveVO, DeviceArchiveVersionVO } from '@/api/pms/asset/device/archive'
const props = defineProps<{ deviceId: number }>()
const detail = ref<DeviceArchiveVO>()
const locationHistory = ref<DeviceArchiveVersionVO[]>([])
const loading = ref(false)
let requestVersion = 0
watch(
  () => props.deviceId,
  async (id) => {
    const version = ++requestVersion
    detail.value = undefined
    locationHistory.value = []
    loading.value = true
    try {
      const [current, versions] = await Promise.all([
        DeviceArchiveApi.getDeviceArchiveRecord(id),
        DeviceArchiveApi.getDeviceArchiveVersions(id)
      ])
      if (version !== requestVersion) return
      detail.value = current
      locationHistory.value = (versions || []).filter(
        (item) => item.changeType === 'LOCATION_EFFECTIVE'
      )
    } finally {
      if (version === requestVersion) loading.value = false
    }
  },
  { immediate: true }
)
</script>
