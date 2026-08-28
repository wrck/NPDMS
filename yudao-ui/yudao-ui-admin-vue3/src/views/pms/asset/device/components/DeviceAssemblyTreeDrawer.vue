<template>
  <el-drawer v-model="visible" title="设备装配树" size="min(760px, 92vw)" @open="load">
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="parentDeviceSn" label="父设备SN" />
      <el-table-column prop="childDeviceSn" label="子设备SN" />
      <el-table-column prop="positionCode" label="位置" />
      <el-table-column prop="assemblyType" label="装配类型" />
      <el-table-column prop="effectiveFrom" label="生效时间" />
      <el-table-column prop="effectiveTo" label="失效时间" />
    </el-table>
  </el-drawer>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceAssemblyVO } from '@/api/pms/asset/device'
const visible = ref(false)
const loading = ref(false)
const deviceId = ref<number>()
const rows = ref<DeviceAssemblyVO[]>([])
const open = (id: number) => {
  deviceId.value = id
  visible.value = true
}
const load = async () => {
  if (!deviceId.value) return
  loading.value = true
  try {
    rows.value = await DeviceApi.getAssemblyTree(deviceId.value)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
