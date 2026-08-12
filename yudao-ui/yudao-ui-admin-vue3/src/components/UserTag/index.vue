<template>
  <span>{{ displayName }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'UserTag' })

const props = defineProps({
  userId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 模块级缓存：避免同一用户ID重复请求
const userCache = new Map<number, string>()

const displayName = ref<string>('')

const loadUserName = async (id: number) => {
  if (!id) {
    displayName.value = '-'
    return
  }
  if (userCache.has(id)) {
    displayName.value = userCache.get(id) || '-'
    return
  }
  try {
    const res = await UserApi.getSimpleUser(id)
    const name = res?.nickname || `用户#${id}`
    userCache.set(id, name)
    displayName.value = name
  } catch {
    const fallback = `用户#${id}`
    userCache.set(id, fallback)
    displayName.value = fallback
  }
}

watch(
  () => props.userId,
  (val) => {
    if (val) loadUserName(Number(val))
    else displayName.value = '-'
  },
  { immediate: true }
)
</script>
