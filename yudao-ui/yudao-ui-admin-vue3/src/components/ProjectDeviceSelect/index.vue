<template>
  <el-select
    :model-value="modelValue"
    :disabled="disabled || !validProject"
    :placeholder="validProject ? '请选择设备，可输入序列号或型号搜索' : '请先选择项目'"
    clearable filterable remote
    :remote-method="search"
    :debounce="100"
    :loading="loading && !options.length"
    class="w-full"
    @update:model-value="selectDevice"
  >
    <el-option
      v-for="device in displayedOptions" :key="device.id"
      :label="[device.sn, device.productModel, device.name].filter(Boolean).join(' - ')" :value="device.id" />
    <template #footer>
      <div v-if="validProject" class="device-options-footer">
        <span v-if="errorText" role="alert">{{ errorText }}</span>
        <span v-else>已加载 {{ options.length }} / {{ total }} 台</span>
        <el-button
          v-if="errorText" text type="primary" size="small" :disabled="disabled"
          :loading="loading" data-testid="project-device-retry" @click="retry">重试</el-button>
        <el-button
          v-else-if="options.length < total" text type="primary" size="small"
          :disabled="disabled" :loading="loading" data-testid="project-device-load-more"
          @click="loadMore">加载更多</el-button>
      </div>
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getDeviceArchivePage, type DeviceArchiveVO } from '@/api/pms/asset/device/archive'

const props = defineProps<{ projectId?: number | string; modelValue?: number | string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | string | undefined] }>()
const validProject = computed(() => typeof props.projectId === 'number' ? Number.isSafeInteger(props.projectId) && props.projectId > 0 : typeof props.projectId === 'string' && /^[1-9]\d{0,18}$/.test(props.projectId))
type DeviceOption = Omit<DeviceArchiveVO, 'id'> & { id: number | string }
const options = ref<DeviceOption[]>([])
const selectedOption = ref<DeviceOption>()
const displayedOptions = computed(() => selectedOption.value &&
  String(selectedOption.value.id) === String(props.modelValue) &&
  !options.value.some(device => String(device.id) === String(props.modelValue))
  ? [selectedOption.value, ...options.value] : options.value)
const loading = ref(false)
const errorText = ref('')
const total = ref(0)
const pageNo = ref(0)
const keyword = ref('')
let searchProjectId: number | string | undefined
let sequence = 0

const loadPage = async (request: { pageNo: number; append: boolean }) => {
  const current = ++sequence
  errorText.value = ''
  if (!validProject.value) { loading.value = false; return }
  loading.value = true
  try {
    const result = await getDeviceArchivePage({
      selectionProjectId: props.projectId, pageNo: request.pageNo, pageSize: 50,
      keyword: keyword.value || undefined
    })
    if (current !== sequence) return
    const next: DeviceOption[] = result.list
    if (request.append && !next.length && options.value.length < result.total) throw new Error('INCOMPLETE_PAGE')
    options.value = request.append
      ? [...new Map([...options.value, ...next].map(device => [String(device.id), device])).values()]
      : next
    total.value = result.total
    pageNo.value = request.pageNo
    const selected = options.value.find(device => String(device.id) === String(props.modelValue))
    if (selected) selectedOption.value = selected
  } catch {
    if (current === sequence) errorText.value = '设备加载失败，请重试。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
const search = async (value: string) => {
  const nextKeyword = value.trim()
  const sameSearch = searchProjectId === props.projectId && nextKeyword === keyword.value
  if (sameSearch && (loading.value || (!nextKeyword && pageNo.value > 0 && !errorText.value))) return
  searchProjectId = props.projectId
  keyword.value = nextKeyword
  options.value = []
  total.value = 0
  pageNo.value = 0
  await loadPage({ pageNo: 1, append: false })
}
const loadMore = async () => {
  if (loading.value || props.disabled || options.value.length >= total.value) return
  await loadPage({ pageNo: pageNo.value + 1, append: true })
}
const retry = async () => {
  if (loading.value || props.disabled) return
  await loadPage({ pageNo: pageNo.value + 1, append: pageNo.value > 0 })
}
const selectDevice = (value?: number | string) => {
  selectedOption.value = displayedOptions.value.find(device => String(device.id) === String(value))
  emit('update:modelValue', value)
}
watch(() => props.projectId, () => {
  selectedOption.value = undefined
  void search('')
}, { immediate: true })
onBeforeUnmount(() => { ++sequence })
</script>

<style scoped>
.device-options-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: 12px; }
</style>
