<template>
  <section v-if="survey && outsourceEligible" class="shortcut" aria-label="整单转包">
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
      v-hasPermi="['pms:res-outsource:create']"
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
import { computed, watchEffect } from 'vue'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import { outsourceDetailUrl } from './siteSurveyOutsource'
const props = defineProps<{
  getSurvey?: () => SiteSurveyVO
  readonly?: boolean
  hasManufacturerQuestion?: boolean
  updateRequired?: (value: boolean) => void
  launch?: (kind: string) => void
}>()
const survey = computed(() => props.getSurvey?.())
// Demo 2.2 第8项联动：模板含第8项时，仅“上架加电需要原厂实施”为是才展示外包子块，
// 选否时清空未提交的转包标记（不撤回已提交申请）；模板缺该项时保持原可见性。
const outsourceEligible = computed(() => {
  if (props.hasManufacturerQuestion !== true) return true
  return survey.value?.formExtraValues?.extra_manufacturerInstallation === true
})
watchEffect(() => {
  const current = survey.value
  if (
    props.hasManufacturerQuestion === true &&
    current &&
    current.formExtraValues?.extra_manufacturerInstallation === false &&
    current.outsourceRequired &&
    !current.outsourceRequestId &&
    !props.readonly
  )
    props.updateRequired?.(false)
})
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
