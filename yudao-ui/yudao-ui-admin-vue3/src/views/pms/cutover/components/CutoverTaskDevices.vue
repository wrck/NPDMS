<template>
  <section v-loading="loading" class="cut-card" aria-labelledby="cutover-devices-title" data-testid="cutover-task-devices">
    <header class="cut-card__header"><h3 id="cutover-devices-title"><Icon icon="ep:monitor" />设备序列号表</h3><small>创建后设备范围只读</small></header>
    <div class="cut-card__body">
      <el-table :data="rows" row-key="deviceId" empty-text="暂无设备">
        <el-table-column type="index" label="序号" width="65" />
        <el-table-column prop="serialNumber" label="序列号" min-width="220" />
        <el-table-column prop="productCode" label="产品编码" min-width="180" />
        <el-table-column prop="productModel" label="产品型号" min-width="180" />
      </el-table>
      <p v-if="errorText" role="status" class="cut-help">{{ errorText }}</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import { getDevice, type DeviceDetailVO } from '@/api/pms/asset/device'
import type { CutoverTaskDetail } from '@/api/pms/cutover'
import { checkPermi } from '@/utils/permission'

const props = defineProps<{ detail: CutoverTaskDetail }>()
const products = ref<Record<string, DeviceDetailVO['summary']>>({})
const loading = ref(false)
const errorText = ref('')
const rows = computed(() => props.detail.devices.map(device => ({
  deviceId: device.deviceId,
  serialNumber: device.serialNumber,
  productCode: products.value[String(device.deviceId)]?.productCode || '—',
  productModel: products.value[String(device.deviceId)]?.productModel || '—'
})))
let sequence = 0
watch(() => props.detail.task.id, async () => {
  const request = ++sequence
  products.value = {}
  errorText.value = ''
  loading.value = false
  if (!checkPermi(['pms:device:query'])) return
  loading.value = true
  const devices = props.detail.devices
  try {
    const results = await Promise.allSettled(devices.map(device => getDevice(device.deviceId)))
    if (request !== sequence) return
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') products.value[String(devices[index].deviceId)] = result.value.summary
    })
    if (results.some(result => result.status === 'rejected')) errorText.value = '部分产品信息暂时无法读取，可刷新后重试。'
  } finally { if (request === sequence) loading.value = false }
}, { immediate: true })
onBeforeUnmount(() => { ++sequence })
</script>
