<template>
  <div v-loading="loading">
    <el-descriptions v-if="detail" :column="2" border>
      <el-descriptions-item label="设备">{{ detail.sn }} {{ detail.name }}</el-descriptions-item>
      <el-descriptions-item label="解析状态">
        <el-tag :type="detail.locationResolutionStatus === 'RESOLVED' ? 'success' : 'warning'">
          {{ detail.locationResolutionStatus || 'UNRESOLVED' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="当前地点" :span="2">{{
        detail.locationSnapshot || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="站点">{{
        detail.siteId ? `#${detail.siteId}` : '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="站点位置">{{
        detail.siteLocationId ? `#${detail.siteLocationId}` : '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="生效时间">{{
        detail.locationEffectiveFrom || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="来源安装记录">
        {{ detail.locationRecordId ? `#${detail.locationRecordId}` : '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="发生时快照" :span="2">
        <pre class="snapshot">{{ detail.locationSnapshot || '-' }}</pre>
      </el-descriptions-item>
    </el-descriptions>
    <el-divider content-position="left">位置变更历史</el-divider>
    <el-timeline>
      <el-timeline-item
        v-for="item in locationHistory"
        :key="item.id"
        :timestamp="String(item.createTime || '')"
      >
        <strong>{{ item.changeType }}</strong> · {{ item.changeDescription || '-' }}
      </el-timeline-item>
    </el-timeline>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
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
<style scoped>
.snapshot {
  margin: 0;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
