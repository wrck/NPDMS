<template>
  <div class="editor-block resource-confirmation">
    <Editor v-model="body" height="180px" :readonly="readOnly" />
    <el-button class="mt-4px" size="small" type="primary" plain
      :disabled="readOnly || loading || referencing || !!error || (!latestSurvey && !requirementContent)"
      :loading="referencing" @click="referenceResources">引用工勘和需求分析</el-button>
    <el-alert v-if="error" class="mt-8px" :title="error" type="error" :closable="false">
      <el-button v-if="!readOnly" link @click="loadSurvey">重新加载</el-button>
    </el-alert>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getSiteSurvey, getSiteSurveyPage, type SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import { requirementResources, surveyResources } from './resourceConfirmation'
const props = defineProps<{ projectId?: number; readOnly: boolean; requirementValues: Record<string, unknown> }>()
const body = defineModel<string>({ required: true })
const latestSurvey = ref<SiteSurveyVO>()
const loading = ref(false)
const referencing = ref(false)
const error = ref('')
const requirementContent = computed(() => requirementResources(props.requirementValues))
let request = 0
const latestConfirmed = async (projectId: number) => {
  // The Owner's confirmed-record query is ordered by descending record ID.
  const page = await getSiteSurveyPage({ projectId, status: 1, pageNo: 1, pageSize: 1 })
  return page.list[0] as SiteSurveyVO | undefined
}
const loadSurvey = async () => {
  const current = ++request
  const projectId = props.projectId
  latestSurvey.value = undefined
  error.value = ''
  referencing.value = false
  loading.value = false
  if (!projectId || props.readOnly) return
  loading.value = true
  try {
    const survey = await latestConfirmed(projectId)
    if (current === request) latestSurvey.value = survey
  } catch {
    if (current === request) error.value = '已确认工勘记录加载失败，请重试'
  } finally {
    if (current === request) loading.value = false
  }
}
const referenceResources = async () => {
  if (props.readOnly || !props.projectId || referencing.value || loading.value || error.value) return
  const current = request
  const projectId = props.projectId
  referencing.value = true
  error.value = ''
  try {
    // Requery at the action so a record confirmed after opening the page is included.
    const latest = await latestConfirmed(projectId)
    if (current !== request || props.readOnly) return
    latestSurvey.value = latest
    const survey = latest?.id ? await getSiteSurvey(latest.id) : undefined
    if (current !== request || props.readOnly) return
    if (survey && (String(survey.projectId) !== String(projectId) || survey.status !== 1)) {
      error.value = '工勘记录与当前项目或已确认状态不一致，请重试'
      return
    }
    const content = `${survey ? surveyResources(survey) : ''}${requirementContent.value}`
    if (content) body.value = content
    else error.value = '暂无可引用的工勘或需求分析内容'
  } catch {
    if (current === request) error.value = '现网资源引用失败，请重新加载后重试'
  } finally {
    if (current === request) referencing.value = false
  }
}
watch(() => [props.projectId, props.readOnly], () => { void loadSurvey() }, { immediate: true })
</script>
<style scoped lang="scss">
.resource-confirmation { width: 100%; min-width: 0; }
.resource-confirmation :deep(.w-e-scroll) { padding: 12px 16px; }
.resource-confirmation :deep(.table-container),
.resource-confirmation :deep([data-w-e-type='table']) { max-width: 100%; overflow-x: auto; }
.resource-confirmation :deep(table) { width: 100%; max-width: 100%; table-layout: fixed; border-collapse: collapse; }
.resource-confirmation :deep(table:has(th:nth-child(8))) { min-width: 760px; }
.resource-confirmation :deep(th),
.resource-confirmation :deep(td) { padding: 8px 10px; vertical-align: top; white-space: normal; overflow-wrap: anywhere; border: 1px solid var(--el-border-color); line-height: 1.6; }
.resource-confirmation :deep(th) { background: var(--el-fill-color-light); font-weight: 600; }
.resource-confirmation :deep(table:not(:has(th:nth-child(8))) tr > :first-child) { width: 28%; }
.resource-confirmation :deep(h3) { margin: 18px 0 10px; font-size: 15px; line-height: 1.5; }
.resource-confirmation :deep(h3:first-child) { margin-top: 0; }
@media (width <= 767px) {
  .resource-confirmation :deep(.w-e-scroll) { padding: 10px; }
  .resource-confirmation :deep(th),
  .resource-confirmation :deep(td) { padding: 6px 8px; }
}
</style>
