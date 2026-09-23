<template>
  <section aria-label="工勘独立支线判断" class="survey-preparation-branches">
    <h4>工前独立支线</h4>
    <p>根据本份工勘记录判断，沿用各流程的办理入口；不增加填报，也不要求统一回流。</p>
    <div v-for="branch in evaluated" :key="branch.title">
      <span>{{ branch.title }}</span>
      <el-tag :type="branch.state === 'MATCHED' ? 'warning' : 'info'">
        {{ branch.state === 'MATCHED' ? '需独立办理' : branch.state === 'UNKNOWN' ? '相关工勘项尚未填写' : '条件未命中' }}
      </el-tag>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import { extractSurveyValues } from './siteSurveyForm'
import { surveyPreparationBranches, type SurveyPreparationBranch } from './surveyPreparationBranches'
const props = defineProps<{ getSurvey?: () => SiteSurveyVO; branches?: SurveyPreparationBranch[] }>()
const evaluated = computed(() => {
  const survey = props.getSurvey?.()
  return surveyPreparationBranches(props.branches || [], survey ? extractSurveyValues(survey) : {})
})
</script>
<style scoped>
.survey-preparation-branches { display: grid; gap: 8px; }
.survey-preparation-branches h4, .survey-preparation-branches p { margin: 0; }
.survey-preparation-branches p { color: var(--el-text-color-secondary); }
.survey-preparation-branches > div { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
</style>
