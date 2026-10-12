<template>
  <section aria-label="设备发货事实" v-loading="loading">
    <el-alert v-if="error" title="发货信息加载失败，请重试。" type="error" :closable="false">
      <el-button @click="refresh++">重新加载</el-button>
    </el-alert>
    <template v-else>
      <el-table :data="rows" row-key="id" empty-text="暂无发货记录">
        <el-table-column label="DPPMS主产品目录名称及型号" min-width="260">
          <template #default="{ row }">
            <div v-for="(label, index) in row.dppmsCatalogLabels" :key="index">
              {{ label.productName ?? '--' }} / {{ label.productModel ?? '--' }}
            </div>
            <span v-if="!row.dppmsCatalogLabels?.length">--</span>
          </template>
        </el-table-column>
        <el-table-column label="DPPMS副产品目录名称及型号" min-width="260">
          <template #default="{ row }">
            <div v-for="(label, index) in row.secondaryDppmsCatalogLabels" :key="index">
              {{ label.productName ?? '--' }} / {{ label.productModel ?? '--' }}
            </div>
            <span v-if="!row.secondaryDppmsCatalogLabels?.length">--</span>
          </template>
        </el-table-column>
        <el-table-column
          v-for="field in shipmentFields"
          :key="field.key"
          :label="field.label"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{
            formatSourceDate(row[field.key], field.key) ?? '--'
          }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNo"
        :page-size="20"
        :total="total"
        layout="prev,pager,next,total"
      />
    </template>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { formatSourceDate } from '@/utils/formatSourceDate'
import { getDeviceShipments, type DeviceShipmentVO } from '@/api/pms/asset/device'
import { deviceShipmentFields } from './device-native-fields'
const shipmentFields = deviceShipmentFields
const props = defineProps<{ deviceId: number }>()
const rows = ref<DeviceShipmentVO[]>([]),
  total = ref(0),
  pageNo = ref(1),
  refresh = ref(0),
  loading = ref(false),
  error = ref(false)
watch(
  [() => props.deviceId, pageNo, refresh],
  async ([id, page], _old, onCleanup) => {
    let current = true
    onCleanup(() => {
      current = false
    })
    rows.value = []
    if (id !== _old?.[0]) total.value = 0
    loading.value = true
    error.value = false
    try {
      const result = await getDeviceShipments(id, { pageNo: page, pageSize: 20 })
      if (current) {
        rows.value = result.list
        total.value = result.total
      }
    } catch {
      if (current) error.value = true
    } finally {
      if (current) loading.value = false
    }
  },
  { immediate: true }
)
</script>
