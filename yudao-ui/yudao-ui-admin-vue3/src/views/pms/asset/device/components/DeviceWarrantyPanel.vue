<template>
  <DeviceSourceStatus :slice="slice" />
  <el-button :disabled="loading" class="mb-16px" @click="refresh++">加载维保记录</el-button>
  <el-alert v-if="error" title="维保资料加载失败，请重试。" type="error" :closable="false" />
  <div v-loading="loading">
    <DeviceReadOnlyFields
      v-if="result?.current"
      title="当前维保"
      :fields="[
        { label: '维保状态', value: result.current.warrantyStatus },
        { label: '维保期限', value: warrantyRange },
        { label: '维保月数', value: result.current.warrantyMonths },
        { label: '维保合同', value: result.current.warrantyContractNo }
      ]"
    />
    <el-empty
      v-if="result && !result.records.list.length"
      :image-size="80"
      description="暂无维保记录"
    />
    <DeviceReadOnlyFields
      v-for="(item, index) in result?.records.list || []"
      :key="index"
      :title="`维保记录 ${index + 1}`"
      :fields="
        deviceWarrantyFields.map((field) => ({
          label: field.label,
          value: formatSourceDate(item[field.key], field.key)
        }))
      "
    />
    <el-pagination
      v-if="result"
      v-model:current-page="pageNo"
      :page-size="20"
      :total="result.records.total"
      layout="prev,pager,next,total"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatSourceDate } from '@/utils/formatSourceDate'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceSourceSliceVO, DeviceWarrantyResultVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
import { deviceWarrantyFields } from './device-native-fields'
const props = defineProps<{ deviceId: number; slice: DeviceSourceSliceVO }>()
const loading = ref(false)
const error = ref(false)
const pageNo = ref(1)
const refresh = ref(0)
const result = ref<DeviceWarrantyResultVO>()
const warrantyRange = computed(() =>
  result.value?.current
    ? `${result.value.current.warrantyStartDate || '--'} 至 ${result.value.current.warrantyEndDate || '--'}`
    : '--'
)
watch(
  [() => props.deviceId, pageNo, refresh],
  async ([id, page], _old, onCleanup) => {
    let current = true
    onCleanup(() => {
      current = false
    })
    loading.value = true
    error.value = false
    result.value = undefined
    try {
      const data = await DeviceApi.getWarrantyRecords(id, { pageNo: page, pageSize: 20 })
      if (current) result.value = data
    } catch {
      if (current) error.value = true
    } finally {
      if (current) loading.value = false
    }
  },
  { immediate: true }
)
</script>
