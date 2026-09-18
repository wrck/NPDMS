<template>
  <span>{{ displayName }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'

defineOptions({ name: 'EquipmentTag' })

const props = defineProps({
  equipmentId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 模块级缓存
const equipmentCache = new Map<number | string, string>()

const displayName = ref<string>('')

const loadEquipmentName = async (id: number | string) => {
  if (!id) {
    displayName.value = '-'
    return
  }
  if (equipmentCache.has(id)) {
    displayName.value = equipmentCache.get(id) || '-'
    return
  }
  try {
    const res = await DeviceArchiveApi.getDeviceArchiveRecord(id)
    const name = res?.name || res?.sn || `设备#${id}`
    equipmentCache.set(id, name)
    if (String(props.equipmentId) !== String(id)) return
    displayName.value = name
  } catch {
    const fallback = `设备#${id}`
    equipmentCache.set(id, fallback)
    if (String(props.equipmentId) !== String(id)) return
    displayName.value = fallback
  }
}

watch(
  () => props.equipmentId,
  (val) => {
    if (val) loadEquipmentName(val)
    else displayName.value = '-'
  },
  { immediate: true }
)
</script>
