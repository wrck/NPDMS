<template>
  <span>{{ projectName }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as ProjectApi from '@/api/pms/project/project'

defineOptions({ name: 'ProjectTag' })

const props = defineProps({
  projectId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 模块级缓存：避免同一项目ID重复请求
const projectCache = new Map<number, string>()

const projectName = ref<string>('')

const loadProjectName = async (id: number) => {
  if (!id) {
    projectName.value = '-'
    return
  }
  // 命中缓存直接返回
  if (projectCache.has(id)) {
    projectName.value = projectCache.get(id) || '-'
    return
  }
  try {
    const res = await ProjectApi.getProject(id)
    const name = res?.name || `项目#${id}`
    projectCache.set(id, name)
    projectName.value = name
  } catch {
    const fallback = `项目#${id}`
    projectCache.set(id, fallback)
    projectName.value = fallback
  }
}

watch(
  () => props.projectId,
  (val) => {
    if (val) loadProjectName(Number(val))
    else projectName.value = '-'
  },
  { immediate: true }
)
</script>
