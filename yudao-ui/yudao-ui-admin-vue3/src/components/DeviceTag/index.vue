<template>
  <span>{{ displayName }}</span>
</template>

<script lang="ts">
const deviceNameRequests = new Map<string, Promise<string>>()
</script>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import { getCurrentUserId, getTenantId, getVisitTenantId } from '@/utils/auth'

defineOptions({ name: 'DeviceTag' })

const props = defineProps({
  deviceId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 当前组件已显示的名称；不同组件只共享同一登录上下文内尚未结束的请求。
const deviceCache = new Map<number | string, string>()

const readDeviceName = (id: number | string): Promise<string> => {
  const key = JSON.stringify([getTenantId(), getVisitTenantId(), getCurrentUserId(), String(id)])
  const pending = deviceNameRequests.get(key)
  if (pending) return pending
  const request = DeviceArchiveApi.getDeviceArchiveRecord(id)
    .then(res => res?.name || res?.sn || `设备#${id}`)
    .finally(() => { deviceNameRequests.delete(key) })
  deviceNameRequests.set(key, request)
  return request
}

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
    const name = await readDeviceName(id)
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
