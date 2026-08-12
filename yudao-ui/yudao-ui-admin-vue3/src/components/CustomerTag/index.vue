<template>
  <span>{{ customerName }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as CustomerApi from '@/api/pms/project/customer'

defineOptions({ name: 'CustomerTag' })

const props = defineProps({
  customerId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 模块级缓存：避免同一客户ID重复请求
const customerCache = new Map<number, string>()

const customerName = ref<string>('')

const loadCustomerName = async (id: number) => {
  if (!id) {
    customerName.value = '-'
    return
  }
  if (customerCache.has(id)) {
    customerName.value = customerCache.get(id) || '-'
    return
  }
  try {
    const res = await CustomerApi.getCustomer(id)
    const name = res?.name || res?.shortName || `客户#${id}`
    customerCache.set(id, name)
    customerName.value = name
  } catch {
    const fallback = `客户#${id}`
    customerCache.set(id, fallback)
    customerName.value = fallback
  }
}

watch(
  () => props.customerId,
  (val) => {
    if (val) loadCustomerName(Number(val))
    else customerName.value = '-'
  },
  { immediate: true }
)
</script>
