<template>
  <DeviceSourceStatus :slice="slice" />
  <el-button :disabled="loading" class="mb-16px" @click="load">加载维保记录</el-button>
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
      :fields="[
        { label: '开始日期', value: item.warrantyStartDate },
        { label: '结束日期', value: item.warrantyEndDate },
        { label: '月数', value: item.warrantyMonths },
        { label: '合同号', value: item.warrantyContractNo },
        { label: '续保', value: item.extended }
      ]"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceSourceSliceVO, DeviceWarrantyResultVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
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
