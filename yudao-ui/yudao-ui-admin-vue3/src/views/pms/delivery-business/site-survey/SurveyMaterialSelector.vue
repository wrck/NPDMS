<template>
  <section v-if="!inBranches && survey?.businessValues?.materialMatches === false" aria-label="工勘换货申请">
    <el-button
      v-if="!readonly"
      type="primary"
      :disabled="disabled"
      v-hasPermi="['pms:imp-material-exch:create']"
      @click="launch?.('exchange')"
    >
      新增换货申请
    </el-button>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import type { SurveyMaterialSelection } from './surveyBusinessForm'
const props = defineProps<{
  modelValue?: SurveyMaterialSelection[]
  getSurvey?: () => SiteSurveyVO
  readonly?: boolean
  disabled?: boolean
  inBranches?: boolean
  launch?: (kind: string) => void
}>()
const survey = computed(() => props.getSurvey?.())
</script>
