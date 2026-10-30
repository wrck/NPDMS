<template>
  <el-select
    :model-value="modelValue"
    filterable
    remote
    clearable
    placeholder="可留空"
    size="small"
    class="!w-170px"
    aria-label="换货产品"
    data-testid="exchange-product-select"
    :remote-method="loadOptions"
    :loading="loading"
    @focus="loadOptions('')"
    @clear="handleClear"
    @update:model-value="handleUpdate"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :value="item.id!"
      :label="`${item.productCode} ${item.productName}`"
      :disabled="item.status !== 'ACTIVE'"
    />
  </el-select>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { AssetProductOfficialApi, ProductOfficialVO } from '@/api/pms/asset/product-official'

defineProps<{ modelValue?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', value?: number): void }>()

const options = ref<ProductOfficialVO[]>([])
const loading = ref(false)
/** 关键字搜索产品信息；首次聚焦加载第一页全量；停用（非 ACTIVE）行置灰禁选 */
const loadOptions = async (keyword: string) => {
  loading.value = true
  try {
    options.value = (await AssetProductOfficialApi.page({ pageNo: 1, pageSize: 50, keyword })).list
  } finally {
    loading.value = false
  }
}
const handleUpdate = (value?: number) => emit('update:modelValue', value)
const handleClear = () => emit('update:modelValue', undefined)
onMounted(() => loadOptions(''))
</script>
