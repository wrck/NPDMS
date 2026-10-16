<template>
  <el-tag size="small" type="info" effect="plain" aria-label="当前执行计划">
    <template v-if="plan">当前执行：{{ executionLabel }} · 计划 v{{ plan.revisionNo }}</template>
    <template v-else>{{ error ? '当前执行计划读取失败' : '当前执行计划加载中…' }}</template>
  </el-tag>
  <el-button v-if="error" link type="primary" @click="load">重试计划信息</el-button>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getProjectPlan, type PlanDefinition } from '@/api/pms/project/projects/projectPlan'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'

const props = defineProps<{ projectId: number; refreshToken?: number }>()
const plan = ref<PlanDefinition>()
const error = ref(false)
// 配置维度取自生效计划，不使用可独立变化的项目属性或未生效草稿。
const executionLabel = computed(() => {
  const match = plan.value?.designer.match
  const dimensions: [string | undefined, DICT_TYPE][] = [
    [match?.projectCategory, DICT_TYPE.PMS_PROJECT_CATEGORY],
    [match?.signingMethod, DICT_TYPE.PMS_SIGNING_METHOD],
    [match?.implementationMethod, DICT_TYPE.PMS_IMPLEMENTATION_METHOD],
    [match?.majorProjectLevel, DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL]
  ]
  return dimensions.map(([value, dict]) => value ? getDictLabel(dict, value) || value : '')
    .filter(Boolean).join('－') || '项目独立配置'
})
let requestId = 0
const load = async () => {
  const current = ++requestId
  plan.value = undefined
  error.value = false
  try {
    const state = await getProjectPlan(props.projectId)
    if (current === requestId) plan.value = state.effective
  } catch {
    if (current === requestId) error.value = true
  }
}
watch(() => [props.projectId, props.refreshToken], load, { immediate: true })
</script>
