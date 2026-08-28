<template>
  <el-drawer v-model="visible" title="项目归属历史" size="min(760px, 92vw)" @open="load">
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="projectId" label="项目ID" />
      <el-table-column prop="relationshipType" label="关系类型" />
      <el-table-column prop="effectiveFrom" label="生效时间" />
      <el-table-column prop="effectiveTo" label="失效时间" />
      <el-table-column prop="assignmentVersion" label="版本" />
      <el-table-column prop="reason" label="原因" show-overflow-tooltip />
    </el-table>
  </el-drawer>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceProjectRelationshipVO } from '@/api/pms/asset/device'
const visible = ref(false)
const loading = ref(false)
const deviceId = ref<number>()
const rows = ref<DeviceProjectRelationshipVO[]>([])
const open = (id: number) => {
  deviceId.value = id
  visible.value = true
}
const load = async () => {
  if (!deviceId.value) return
  loading.value = true
  try {
    rows.value = (
      await DeviceApi.getAssignmentHistory(deviceId.value, { pageNo: 1, pageSize: 50 })
    ).list
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
