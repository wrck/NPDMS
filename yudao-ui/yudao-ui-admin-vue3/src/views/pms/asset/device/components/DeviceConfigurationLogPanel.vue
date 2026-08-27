<template>
  <DeviceSourceStatus :slice="slice" />
  <el-button :loading="loading" @click="load">加载配置Log</el-button>
  <el-table v-if="loaded" :data="rows" size="small">
    <el-table-column prop="configType" label="类型" />
    <el-table-column prop="sourceSystem" label="来源" />
    <el-table-column prop="collectedAt" label="采集时间" />
    <el-table-column prop="fileHash" label="文件摘要" show-overflow-tooltip />
    <el-table-column label="操作" width="100">
      <template #default="{ row }">
        <el-button
          v-if="row.downloadable"
          link
          v-hasPermi="['pms:device-configuration-log:download']"
          @click="download(row.id)"
          >下载</el-button
        >
      </template>
    </el-table-column>
  </el-table>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceConfigurationLogVO, DeviceSourceSliceVO } from '@/api/pms/asset/device'
import downloadFile from '@/utils/download'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
const props = defineProps<{ deviceId: number; slice: DeviceSourceSliceVO }>()
const loading = ref(false)
const loaded = ref(false)
const rows = ref<DeviceConfigurationLogVO[]>([])
const load = async () => {
  loading.value = true
  try {
    rows.value = await DeviceApi.getConfigurationLogs(props.deviceId)
    loaded.value = true
  } finally {
    loading.value = false
  }
}
const download = async (logId: number) => {
  const grant = await DeviceApi.createConfigurationLogDownloadUrl(props.deviceId, logId)
  const data = await DeviceApi.downloadConfigurationLog(grant.downloadPath)
  downloadFile.markdown(data, `configuration-log-${logId}.txt`)
}
</script>
