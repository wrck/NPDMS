<template>
  <h4>设备日志解析记录</h4>
  <el-button size="small" :loading="loading" @click="load">刷新解析记录</el-button>
  <el-alert v-if="failed" type="error" title="解析记录加载失败，请重试" :closable="false" />
  <el-table v-loading="loading" :data="rows" row-key="id" empty-text="暂无关联的设备日志解析记录">
    <el-table-column prop="serialNumber" label="设备序列号" min-width="180" />
    <el-table-column prop="conpVersion" label="软件版本" min-width="220" />
    <el-table-column prop="parserVersion" label="解析版本" width="110" />
    <el-table-column prop="logKey" label="来源日志" min-width="200" />
    <el-table-column label="版本处理" min-width="160"
      ><template #default="{ row }">{{
        labels[row.versionStatus] || row.versionStatus
      }}</template></el-table-column
    >
  </el-table>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import request from '@/config/axios'
const props = defineProps<{ deviceId: number | string }>()
interface RecordRow {
  id: string | number
  serialNumber: string
  conpVersion?: string
  parserVersion?: string
  logKey: string
  versionStatus: string
}
const rows = ref<RecordRow[]>([])
const loading = ref(false)
const failed = ref(false)
const labels: Record<string, string> = {
  UPDATED: '已更新在网版本',
  HISTORICAL: '历史结果，未回退版本',
  VERSION_NOT_IDENTIFIED: '未识别软件版本',
  NOT_UPDATED: '未更新'
}
let sequence = 0
const load = async () => {
  const current = ++sequence
  rows.value = []
  failed.value = false
  loading.value = true
  try {
    const result = await request.get<RecordRow[]>({
      url: `/pms/asset/devices/${props.deviceId}/log-parses`
    })
    if (current === sequence) rows.value = result
  } catch {
    if (current === sequence) failed.value = true
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(() => props.deviceId, load, { immediate: true })
</script>
