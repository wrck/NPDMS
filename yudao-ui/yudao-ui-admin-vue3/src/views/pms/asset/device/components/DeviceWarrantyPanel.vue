<template>
  <DeviceSourceStatus :slice="slice" />
  <el-button :loading="loading" @click="load">加载维保记录</el-button>
  <el-descriptions v-if="result?.current" :column="2" border>
    <el-descriptions-item label="维保状态">{{
      result.current.warrantyStatus || '--'
    }}</el-descriptions-item>
    <el-descriptions-item label="维保期限">{{ warrantyRange }}</el-descriptions-item>
    <el-descriptions-item label="维保月数">{{
      result.current.warrantyMonths ?? '--'
    }}</el-descriptions-item>
    <el-descriptions-item label="维保合同">{{
      result.current.warrantyContractNo || '--'
    }}</el-descriptions-item>
  </el-descriptions>
  <el-table v-if="result" :data="result.records.list" size="small">
    <el-table-column prop="warrantyStartDate" label="开始日期" />
    <el-table-column prop="warrantyEndDate" label="结束日期" />
    <el-table-column prop="warrantyMonths" label="月数" />
    <el-table-column prop="warrantyContractNo" label="合同号" />
    <el-table-column label="续保"
      ><template #default="{ row }">{{ row.extended ? '是' : '否' }}</template></el-table-column
    >
  </el-table>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceSourceSliceVO, DeviceWarrantyResultVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
const props = defineProps<{ deviceId: number; slice: DeviceSourceSliceVO }>()
const loading = ref(false)
const result = ref<DeviceWarrantyResultVO>()
const warrantyRange = computed(() =>
  result.value?.current
    ? `${result.value.current.warrantyStartDate || '--'} 至 ${result.value.current.warrantyEndDate || '--'}`
    : '--'
)
const load = async () => {
  loading.value = true
  try {
    result.value = await DeviceApi.getWarrantyRecords(props.deviceId, { pageNo: 1, pageSize: 20 })
  } finally {
    loading.value = false
  }
}
</script>
