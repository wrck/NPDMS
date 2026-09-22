<template>
  <DeviceSourceStatus :slice="slice" />
  <el-button :disabled="loading" class="mb-16px" @click="load">加载配置Log</el-button>
  <div v-loading="loading">
    <el-empty v-if="loaded && !rows.length" :image-size="80" description="暂无配置日志" />
    <DeviceReadOnlyFields
      v-for="(row, index) in rows"
      :key="row.id"
      :title="`配置日志 ${index + 1}`"
      :fields="[
        { label: '类型', value: row.configType },
        { label: '来源', value: row.sourceSystem },
        { label: '采集时间', value: row.collectedAt },
        { label: '文件摘要', value: row.fileHash, multiline: true }
      ]"
    >
      <el-button
        v-if="row.downloadable"
        link
        v-hasPermi="['pms:device-configuration-log:download']"
        @click="download(row.id)"
        >下载</el-button
      >
    </DeviceReadOnlyFields>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceConfigurationLogVO, DeviceSourceSliceVO } from '@/api/pms/asset/device'
import downloadFile from '@/utils/download'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
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
