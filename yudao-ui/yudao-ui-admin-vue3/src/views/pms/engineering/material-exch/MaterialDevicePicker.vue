<template>
  <div class="material-picker">
    <el-alert v-if="!validProject" title="请先选择项目" type="info" :closable="false" />
    <div class="filters">
      <el-input v-model="query.sn" aria-label="筛选序列号" placeholder="序列号" clearable @keyup.enter="search" />
      <el-input v-model="query.name" aria-label="筛选设备名称" placeholder="设备名称" clearable @keyup.enter="search" />
      <el-input v-model="query.productModel" aria-label="筛选产品型号" placeholder="产品型号" clearable @keyup.enter="search" />
      <el-input v-model="query.contractNo" aria-label="筛选合同号" placeholder="合同号" clearable @keyup.enter="search" />
      <el-button :disabled="!validProject" @click="search">筛选</el-button>
      <el-button :disabled="!validProject" @click="reset">重置</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table ref="table" data-testid="device-candidates" v-loading="loading" :data="devices" row-key="id" border max-height="280"
      @select="selectRow" @select-all="selectPage">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="sn" label="序列号" min-width="150" />
      <el-table-column prop="name" label="设备名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="productModel" label="产品型号" min-width="120" />
      <el-table-column prop="contractNo" label="合同号" min-width="140" />
    </el-table>
    <Pagination data-testid="device-pagination" v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="load" />
    <div class="selection-summary">已选择 {{ modelValue.length }} 台设备（翻页和筛选保留选择）</div>
    <el-table v-if="modelValue.length" :data="modelValue" row-key="equipmentId" border max-height="220" size="small">
      <el-table-column prop="sn" label="已选序列号" min-width="150" />
      <el-table-column prop="name" label="设备名称" min-width="140" />
      <el-table-column prop="productModel" label="产品型号" min-width="120" />
      <el-table-column label="操作" width="70">
        <template #default="{ row }"><el-button link type="danger" @click="remove(row.equipmentId)">移除</el-button></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { getDeviceArchivePage, type DeviceArchiveVO } from '@/api/pms/asset/device/archive'
import type { MaterialExchangeSerialVO } from '@/api/pms/engineering/material-exch'

const props = defineProps<{ projectId?: number; modelValue: MaterialExchangeSerialVO[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: MaterialExchangeSerialVO[]] }>()
const validProject = computed(() => Number.isSafeInteger(props.projectId) && Number(props.projectId) > 0)
const query = reactive({ pageNo: 1, pageSize: 20, sn: '', name: '', productModel: '', contractNo: '' })
const table = ref()
const loading = ref(false)
const error = ref('')
const devices = ref<DeviceArchiveVO[]>([])
const total = ref(0)
let requestVersion = 0
const restoreChecks = async () => {
  await nextTick()
  const ids = new Set(props.modelValue.map(row => row.equipmentId))
  for (const row of devices.value) table.value?.toggleRowSelection(row, ids.has(row.id!))
}
const load = async () => {
  const version = ++requestVersion
  devices.value = []
  error.value = ''
  loading.value = false
  if (!validProject.value) { total.value = 0; return }
  loading.value = true
  try {
    const result = await getDeviceArchivePage({ ...query, selectionProjectId: props.projectId })
    if (version !== requestVersion) return
    devices.value = result.list
    total.value = result.total
    await restoreChecks()
  } catch {
    if (version === requestVersion) error.value = '设备加载失败，请重试筛选'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
const search = () => { query.pageNo = 1; return load() }
const reset = () => { Object.assign(query, { sn: '', name: '', productModel: '', contractNo: '' }); return search() }
const snapshot = (row: DeviceArchiveVO): MaterialExchangeSerialVO => ({
  equipmentId: row.id!, sn: row.sn, name: row.name, productCode: row.productCode,
  productModel: row.productModel, contractNo: row.contractNo
})
const selectRow = (selection: DeviceArchiveVO[], row: DeviceArchiveVO) => {
  const remaining = props.modelValue.filter(item => item.equipmentId !== row.id)
  emit('update:modelValue', selection.some(item => item.id === row.id) ? [...remaining, snapshot(row)] : remaining)
}
const selectPage = (selection: DeviceArchiveVO[]) => {
  const pageIds = new Set(devices.value.map(row => row.id))
  emit('update:modelValue', [...props.modelValue.filter(row => !pageIds.has(row.equipmentId)), ...selection.map(snapshot)])
}
const remove = (id: number) => emit('update:modelValue', props.modelValue.filter(row => row.equipmentId !== id))
watch(() => props.modelValue, restoreChecks, { deep: true })
watch(() => props.projectId, () => { query.pageNo = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { requestVersion++ })
</script>

<style scoped>
.material-picker { width: 100%; }
.filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.filters .el-input { flex: 1 1 150px; }
.selection-summary { clear: both; padding: 12px 0; color: var(--el-text-color-secondary); }
</style>
