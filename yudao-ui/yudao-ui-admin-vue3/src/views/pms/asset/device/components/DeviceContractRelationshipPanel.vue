<template>
  <section aria-label="主SN与附加SN关系" v-loading="loading">
    <el-alert v-if="error" title="设备关系加载失败，请重试。" type="error" :closable="false">
      <el-button @click="refresh++">重新加载</el-button>
    </el-alert>
    <template v-else>
      <el-table :data="rows" row-key="id" empty-text="暂无主SN与附加SN关系">
        <el-table-column prop="sourceDeviceSn" label="主SN" min-width="190" />
        <el-table-column prop="sourceItem" label="主产品编码" min-width="150" />
        <el-table-column prop="targetDeviceSn" label="附加SN" min-width="190" />
        <el-table-column prop="targetItem" label="附加产品编码" min-width="150" />
        <el-table-column prop="contractNo" label="合同号" min-width="190" />
        <el-table-column v-for="field in dates" :key="field.key" :label="field.label" min-width="170">
          <template #default="{ row }">{{ formatSourceDate(row[field.key], field.key) ?? '--' }}</template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pageNo" :page-size="20" :total="total" layout="prev,pager,next,total" />
    </template>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { getDeviceContractRelationships, type DeviceContractRelationship } from '@/api/pms/asset/device'
import { formatSourceDate } from '@/utils/formatSourceDate'
const props = defineProps<{ deviceId: number }>()
const dates = [
  { key: 'effectiveFrom', label: '关系开始时间' },
  { key: 'effectiveTo', label: '关系结束时间' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '更新时间' }
] as const
const rows = ref<DeviceContractRelationship[]>([]), total = ref(0), pageNo = ref(1),
  refresh = ref(0), loading = ref(false), error = ref(false)
watch([() => props.deviceId, pageNo, refresh], async ([id, page], old, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  rows.value = []
  if (id !== old?.[0]) total.value = 0
  loading.value = true
  error.value = false
  try {
    const result = await getDeviceContractRelationships(id, { pageNo: page, pageSize: 20 })
    if (current) { rows.value = result.list; total.value = result.total }
  } catch { if (current) error.value = true }
  finally { if (current) loading.value = false }
}, { immediate: true })
</script>
