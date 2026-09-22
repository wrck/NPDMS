<template>
  <el-alert v-if="!definition" title="问卷内容不可用，请关闭后重新加载任务。" type="error" :closable="false" />
  <div v-else class="questionnaire-fields">
    <el-form-item v-for="(question, index) in definition.questions" :key="question.code"
      :label="`${index + 1}. ${question.title}`" :required="question.required">
      <el-radio-group v-if="question.type === 'SINGLE_CHOICE' || question.type === 'RATING'"
        :model-value="single(question.code)" @update:model-value="answers[question.code] = String($event)">
        <el-radio v-for="option in question.options" :key="option.code" :value="option.code">{{ option.label }}</el-radio>
      </el-radio-group>
      <el-checkbox-group v-else-if="question.type === 'MULTIPLE_CHOICE'"
        :model-value="multiple(question.code)" @update:model-value="answers[question.code] = $event.map(String)">
        <el-checkbox v-for="option in question.options" :key="option.code" :value="option.code">{{ option.label }}</el-checkbox>
      </el-checkbox-group>
      <el-input v-else :model-value="single(question.code)" @update:model-value="answers[question.code] = $event" type="textarea" :rows="3" :maxlength="question.maxLength" show-word-limit />
      <span v-if="question.type === 'MULTIPLE_CHOICE' && question.maxSelections" class="question-help">最多选择 {{ question.maxSelections }} 项</span>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
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
const single = (code: string) => typeof answers[code] === 'string' ? answers[code] as string : ''
const multiple = (code: string) => Array.isArray(answers[code]) ? answers[code] as string[] : []
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
.questionnaire-fields { margin: 20px 0; padding: 20px 0 0; border-top: 1px solid var(--el-border-color-lighter); }
.questionnaire-fields :deep(.el-radio-group), .questionnaire-fields :deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 4px 20px; }
.questionnaire-fields :deep(.el-radio), .questionnaire-fields :deep(.el-checkbox) { margin-right: 0; white-space: normal; height: auto; min-height: 32px; }
.question-help { display: block; width: 100%; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
