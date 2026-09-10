<template>
  <section
    v-if="survey?.formExtraValues?.extra_railTrayRequired === true"
    aria-label="导轨托盘办理入口"
  >
    <el-button
      :disabled="readonly"
      @click="launch?.('material')"
      v-hasPermi="['pms:eng-material-req:create']"
      >保存工勘并发起领料申请</el-button
    >
    <el-button
      :disabled="readonly"
      @click="launch?.('procurement')"
      v-hasPermi="['pms:eng-ext-proc:create']"
      >保存工勘并发起外采申请</el-button
    >
    <p>复用原申请入口；保存工勘或申请草稿不等于OA审批、采购或到货完成。</p>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
const props = defineProps<{
  getSurvey?: () => SiteSurveyVO
  readonly?: boolean
  launch?: (kind: string) => void
}>()
const survey = computed(() => props.getSurvey?.())
</script>
<style scoped>
p {
  color: var(--el-text-color-secondary);
}
section {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
section :deep(.el-button) {
  margin: 0;
  white-space: normal;
  height: auto;
  min-height: 32px;
}
</style>
