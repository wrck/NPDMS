<template>
  <section aria-label="工勘工期要求">
    <el-date-picker
      :model-value="modelValue"
      :disabled="readonly || disabled"
      :clearable="false"
      type="date"
      value-format="YYYY-MM-DD"
      placeholder="填写项目要求结束日期"
      @update:model-value="changeDate"
    />
    <p>工勘填写工期要求，保存到项目结束日期；项目工期以该日期为截止点倒排。</p>
    <p v-if="error">{{ error }}</p>
    <p v-else>项目已保存结束日期：{{ projectEndDate || '尚未登记' }}</p>
    <el-button :loading="loading" @click="load">刷新项目版本</el-button>
    <p v-if="!error && modelValue && !projectEndDate">本次保存将把本工勘的工期要求登记到项目。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as ProjectApi from '@/api/pms/project/projects'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
const props = defineProps<{
  modelValue?: string
  getSurvey?: () => SiteSurveyVO
  readonly?: boolean
  disabled?: boolean
  updateProjectVersion?: (version: number, needsInitialSync: boolean) => void
  markDateChanged?: () => void
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const survey = computed(() => props.getSurvey?.())
const projectEndDate = ref(''),
  error = ref(''),
  loading = ref(false)
let sequence = 0
const load = async () => {
  const current = ++sequence
  projectEndDate.value = ''
  error.value = ''
  if (!survey.value?.projectId) return
  loading.value = true
  try {
    const project = await ProjectApi.getProject(survey.value.projectId)
    if (current !== sequence) return
    projectEndDate.value = project.projectEndDate || ''
    if (project.version == null) throw new Error('项目版本不可用')
    props.updateProjectVersion?.(project.version, !!props.modelValue && !project.projectEndDate)
  } catch {
    if (current === sequence) error.value = '项目版本读取失败，无法同步结束日期，请刷新后再保存。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
const changeDate = (value: string | null) => {
  if (props.readonly || props.disabled || !value) return
  props.markDateChanged?.()
  emit('update:modelValue', value)
}
watch(() => survey.value?.projectId, load, { immediate: true })
</script>
<style scoped>
section {
  width: 100%;
}
p {
  color: var(--el-text-color-secondary);
}
section :deep(.el-date-editor) {
  max-width: 100%;
}
</style>
