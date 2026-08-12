<template>
  <span>{{ displayName }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as EquipmentApi from '@/api/pms/asset/equipment'

defineOptions({ name: 'EquipmentTag' })

const props = defineProps({
  equipmentId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 模块级缓存
const equipmentCache = new Map<number, string>()

const displayName = ref<string>('')

const loadEquipmentName = async (id: number) => {
  if (!id) {
    displayName.value = '-'
    return
  }
  if (equipmentCache.has(id)) {
    displayName.value = equipmentCache.get(id) || '-'
    return
  }
  try {
    const res = await EquipmentApi.getEquipment(id)
    const name = res?.name || res?.serialNumber || `设备#${id}`
    equipmentCache.set(id, name)
    displayName.value = name
  } catch {
    const fallback = `设备#${id}`
    equipmentCache.set(id, fallback)
    displayName.value = fallback
  }
}

watch(
  () => props.equipmentId,
  (val) => {
    if (val) loadEquipmentName(Number(val))
    else displayName.value = '-'
  },
  { immediate: true }
)
</script>
