<template>
  <PmsEntitySelect :key="projectId" :model-value="modelValue" :api="loadDevices"
    :label-field="['sn', 'name']" value-field="id" query-field="sn"
    :disabled="disabled || !validProject" :placeholder="validProject ? '请选择设备' : '请先选择项目'"
    @update:model-value="emit('update:modelValue', $event)" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
import { getDeviceArchivePage, type DeviceArchivePageParam } from '@/api/pms/asset/device/archive'

const props = defineProps<{ projectId?: number | string; modelValue?: number | string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | string | undefined] }>()
const validProject = computed(() => typeof props.projectId === 'number' ? Number.isSafeInteger(props.projectId) && props.projectId > 0 : typeof props.projectId === 'string' && /^[1-9]\d{0,18}$/.test(props.projectId))
const loadDevices = (params: DeviceArchivePageParam) => validProject.value
  ? getDeviceArchivePage({ ...params, selectionProjectId: props.projectId })
  : Promise.resolve({ list: [], total: 0 })
</script>
