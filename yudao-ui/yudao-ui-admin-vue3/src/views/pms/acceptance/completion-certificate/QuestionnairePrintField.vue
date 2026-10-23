<template>
  <QuestionnaireTable
    v-if="modelValue"
    class="questionnaire-print"
    :definition="modelValue.definition"
    :answers="modelValue.answers"
    disabled
    :show-signature="false"
  />
</template>
<script setup lang="ts">
import QuestionnaireTable from '../satisfaction/QuestionnaireTable.vue'
import type { QuestionnaireDefinition } from '@/api/pms/acceptance/satisfaction'
defineProps<{ modelValue?: { definition: QuestionnaireDefinition; answers: Record<string, string | string[]> } }>()
</script>
<style scoped>
.questionnaire-print :deep(.survey-table) { min-width: 0; font-size: 11px; line-height: 1.5; }
.questionnaire-print :deep(.survey-table tr > th), .questionnaire-print :deep(.survey-table tr > td) { height: 32px; padding: 5px; }
.questionnaire-print :deep(.survey-introduction) { font-size: 11px; line-height: 1.5; margin-bottom: 10px; }
.questionnaire-print :deep(.rating-choice) { min-height: 20px; }
.questionnaire-print :deep(.rating-choice input) { width: 16px; height: 16px; }
.questionnaire-print :deep(.el-textarea__inner) { font-size: 11px; }
.questionnaire-print :deep(.category-column) { width: 65px; }
.questionnaire-print :deep(.question-column) { width: 110px; }
.questionnaire-print :deep(.remark-column) { width: 40px; }
.questionnaire-print :deep(.survey-scroll) { overflow: visible; }
</style>
