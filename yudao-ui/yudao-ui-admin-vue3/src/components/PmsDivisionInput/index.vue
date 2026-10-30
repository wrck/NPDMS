<template>
  <el-form-item v-if="!foreign" :label="label" :prop="prop">
    <el-cascader
      v-model="areaPath"
      :options="areaOptions"
      :props="{ label: 'name', value: 'id', emitPath: true }"
      filterable
      clearable
      class="!w-full"
      placeholder="请选择省、市、区县"
      :disabled="areaLoading"
      @change="handleAreaChange"
    />
  </el-form-item>
  <el-form-item v-else :label="label">
    <el-input :model-value="divisionText" disabled />
    <span class="text-12px text-[var(--el-text-color-secondary)]">境外行政区划保留原值</span>
  </el-form-item>
</template>

<script setup lang="ts">
import { getAreaTree } from '@/api/system/area'

export interface DivisionValue {
  countryCode?: string
  countryName?: string
  provinceCode?: string
  provinceName?: string
  cityCode?: string
  cityName?: string
  districtCode?: string
  districtName?: string
}

defineProps<{ label?: string; prop?: string }>()
const model = defineModel<DivisionValue>({ required: true })

interface AreaNode {
  id: number
  name: string
  children?: AreaNode[]
}
const areaOptions = ref<AreaNode[]>([])
const areaPath = ref<number[]>([])
const areaLoading = ref(false)
const foreign = computed(() => !!model.value.countryCode && model.value.countryCode !== 'CN')
const divisionText = computed(() =>
  [model.value.countryName, model.value.provinceName, model.value.cityName, model.value.districtName]
    .filter(Boolean)
    .join(' / ')
)

const loadArea = async () => {
  if (areaOptions.value.length || areaLoading.value) return
  areaLoading.value = true
  try {
    areaOptions.value = await getAreaTree()
  } finally {
    areaLoading.value = false
  }
}
watch(
  () => [model.value.provinceCode, model.value.cityCode, model.value.districtCode],
  () => {
    areaPath.value = [model.value.provinceCode, model.value.cityCode, model.value.districtCode]
      .filter(Boolean)
      .map(Number)
  },
  { immediate: true }
)
const handleAreaChange = () => {
  let nodes = areaOptions.value
  const selected = (areaPath.value || []).map((id) => {
    const node = nodes.find((item) => item.id === id)
    nodes = node?.children || []
    return node
  })
  Object.assign(model.value, {
    countryCode: 'CN',
    countryName: '中国',
    provinceCode: selected[0] ? String(selected[0].id) : undefined,
    provinceName: selected[0]?.name,
    cityCode: selected[1] ? String(selected[1].id) : undefined,
    cityName: selected[1]?.name,
    districtCode: selected[2] ? String(selected[2].id) : undefined,
    districtName: selected[2]?.name
  })
}
onMounted(loadArea)
</script>
