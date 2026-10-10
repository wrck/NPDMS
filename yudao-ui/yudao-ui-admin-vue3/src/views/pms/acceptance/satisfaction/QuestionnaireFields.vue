<template>
  <el-alert v-if="!definition" title="问卷内容不可用，请关闭后重新加载任务。" type="error" :closable="false" />
  <QuestionnaireTable v-else class="questionnaire-fields" :definition="definition" :answers="answers" />
</template>

<script setup lang="ts">
import QuestionnaireTable from './QuestionnaireTable.vue'
import type { QuestionnaireDefinition } from '@/api/pms/acceptance/satisfaction'
const props = defineProps<{ frozenQuestions?: string }>()
const definition = ref<QuestionnaireDefinition>()
const answers = reactive<Record<string, string | string[]>>({})
watch(() => props.frozenQuestions, (value) => {
  definition.value = undefined
  Object.keys(answers).forEach((key) => delete answers[key])
  try {
    const parsed = JSON.parse(value || '') as QuestionnaireDefinition
    if (!Array.isArray(parsed.questions) || !parsed.questions.length) return
    definition.value = parsed
    parsed.questions.forEach((question) => { answers[question.code] = question.type === 'MULTIPLE_CHOICE' ? [] : '' })
  } catch { /* Missing frozen content must never become an empty valid submission. */ }
}, { immediate: true })
const snapshot = () => {
  if (!definition.value) throw new Error('问卷内容不可用，请重新加载任务')
  for (const question of definition.value.questions) {
    const value = answers[question.code]
    const length = typeof value === 'string' ? value.trim().length : value.length
    if (question.required && !length) throw new Error(`请填写“${question.title}”`)
    if (!length) continue
    if (question.type === 'MULTIPLE_CHOICE' && (length < (question.minSelections ?? 0) || length > (question.maxSelections ?? Infinity)))
      throw new Error(`“${question.title}”的选择数量不符合要求`)
    if (question.type === 'TEXT' && (length < (question.minLength ?? 0) || length > (question.maxLength ?? Infinity)))
      throw new Error(`“${question.title}”的字数不符合要求`)
  }
  return JSON.stringify({ answers: definition.value.questions.filter((q) => answers[q.code]?.length).map((q) => ({ questionCode: q.code, value: answers[q.code] })) })
}
defineExpose({ snapshot })
</script>

<style scoped>
.questionnaire-fields { margin: 20px 0; }
</style>
