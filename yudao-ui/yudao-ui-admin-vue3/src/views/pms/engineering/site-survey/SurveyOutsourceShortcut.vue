<template>
  <section v-if="survey" class="shortcut" aria-label="整单转包">
    <el-checkbox
      :model-value="!!survey.outsourceRequired"
      :disabled="readonly"
      @update:model-value="(value) => updateRequired?.(!!value)"
      >需要转包（整单共用）</el-checkbox
    >
    <el-button
      v-if="survey.outsourceRequired && !survey.outsourceRequestId && !readonly"
      link
      type="primary"
      @click="launch?.('outsource')"
      v-hasPermi="['pms:eng-outsource:create']"
      >保存工勘并发起转包申请</el-button
    >
    <el-link
      v-if="survey.outsourceRequestId"
      :href="outsourceDetailUrl(survey.outsourceRequestId)"
      target="_blank"
      rel="noopener"
      type="primary"
      >查看转包申请 #{{ survey.outsourceRequestId }}</el-link
    >
    <p v-if="survey.outsourceRequestId">取消勾选不撤回申请；关联ID继续保留。</p>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import { outsourceDetailUrl } from './siteSurveyOutsource'
const props = defineProps<{
  getSurvey?: () => SiteSurveyVO
  readonly?: boolean
  updateRequired?: (value: boolean) => void
  launch?: (kind: string) => void
}>()
const survey = computed(() => props.getSurvey?.())
</script>
<style scoped>
.shortcut {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.shortcut p {
  width: 100%;
  margin: 0;
  color: var(--el-text-color-secondary);
}
.shortcut :deep(.el-button) {
  white-space: normal;
  height: auto;
  min-height: 32px;
}
</style>
