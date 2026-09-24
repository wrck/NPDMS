<template>
  <span>{{ displayName }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'

defineOptions({ name: 'DeviceTag' })

const props = defineProps({
  deviceId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 模块级缓存
const deviceCache = new Map<number | string, string>()

const displayName = ref<string>('')

const loadDeviceName = async (id: number | string) => {
  if (!id) {
    displayName.value = '-'
    return
  }
  if (deviceCache.has(id)) {
    displayName.value = deviceCache.get(id) || '-'
    return
  }
  try {
    const res = await DeviceArchiveApi.getDeviceArchiveRecord(id)
    const name = res?.name || res?.sn || `设备#${id}`
    deviceCache.set(id, name)
    if (String(props.deviceId) !== String(id)) return
    displayName.value = name
  } catch {
    const fallback = `设备#${id}`
    deviceCache.set(id, fallback)
    if (String(props.deviceId) !== String(id)) return
    displayName.value = fallback
  }
}

watch(
  () => props.deviceId,
  (val) => {
    if (val) loadDeviceName(val)
    else displayName.value = '-'
  },
  { immediate: true }
)
</script>
