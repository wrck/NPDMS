<template>
  <ContentWrap>
    <header class="page-heading">
      <div><h1>初验 / 终验报告</h1><p>管理不可变报告版本、附件历史与交付件归档状态。</p></div>
      <el-button :loading="loading" @click="load"><Icon icon="ep:refresh" />刷新</el-button>
    </header>
    <el-alert v-if="embedded" title="报告发布/附件办理尚未接入当前任务上下文，请从原业务入口处理" type="info" :closable="false" show-icon />
    <el-form :model="query" class="query-form" label-position="top" @submit.prevent>
      <el-form-item label="项目">
        <PmsEntitySelect v-model="query.projectId" :disabled="projectId !== undefined" :api="ProjectApi.getProjectPage" :label-field="['code', 'name']" value-field="id" query-field="name" placeholder="选择项目查看初验与终验" clearable class="project-select" />
      </el-form-item>
      <el-button type="primary" :disabled="!query.projectId" @click="load"><Icon icon="ep:search" />查询报告活动</el-button>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-skeleton v-if="loading" :rows="4" animated aria-label="正在加载验收报告活动" />
    <el-empty v-else-if="!activities.length" description="请选择有权限的项目，或当前项目尚未形成初验/终验活动" />
    <section v-else class="activity-grid" aria-label="验收报告活动列表">
      <article v-for="item in activities" :key="item.id" class="activity-card">
        <div class="activity-title"><div><span class="eyebrow">{{ typeLabel(item.acceptanceType) }}</span><h2>{{ typeLabel(item.acceptanceType) }}报告</h2></div><el-tag :type="item.activityStatus === 'COMPLETED' ? 'success' : 'warning'">{{ activityStatusLabel(item.activityStatus) }}</el-tag></div>
        <dl><div><dt>项目任务</dt><dd>{{ item.projectTaskId }}</dd></div><div><dt>活动版本</dt><dd>{{ item.version }}</dd></div><div><dt>当前报告</dt><dd>{{ item.currentReportVersionId ? '已生效' : '未生效' }}</dd></div></dl>
        <el-button type="primary" plain class="open-button" @click="openDetail(item.id)">进入报告工作台</el-button>
      </article>
    </section>
  </ContentWrap>
  <AcceptanceReportDetail ref="detailRef" :readonly="readonly || contextBlocked" :allowed-actions="allowedActions" @changed="changed" @dirty-change="emit('dirty-change', $event)" />
</template>

<script setup lang="ts">
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { checkPermi } from '@/utils/permission'
import { isBusinessViewId, legacyOwnerId, sameBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import * as ProjectApi from '@/api/pms/project/project'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import type { AcceptanceActivityVO } from '@/api/pms/project/acceptance-report'
import AcceptanceReportDetail from './detail.vue'

defineOptions({ name: 'PmsAcceptanceReport' })
const props = defineProps<{ projectId?: number | string; objectId?: number | string; readonly?: boolean; allowedActions?: string[] }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const route = useRoute()
const loading = ref(false)
const activities = ref<AcceptanceActivityVO[]>([])
const detailRef = ref<InstanceType<typeof AcceptanceReportDetail>>()
const query = reactive<{ projectId?: BusinessViewId }>({})
const embedded = computed(() => props.projectId !== undefined || props.objectId !== undefined || props.allowedActions !== undefined)
const contextBlocked = ref(false)
let listSequence = 0
let switchSequence = 0
let activeProject: BusinessViewId | undefined
let activeObject: BusinessViewId | undefined
const canQuery = () => (props.allowedActions === undefined || props.allowedActions.includes('QUERY')) && checkPermi(['pms:acceptance:report:query'])
const requestLeave = async () => await detailRef.value?.requestLeave() ?? true
const discardChanges = () => {
  if (detailRef.value?.discardChanges() === false) return false
  listSequence++
  emit('dirty-change', false)
  return true
}
const openDetail = async (id: BusinessViewId) => {
  if (contextBlocked.value || !canQuery() || !isBusinessViewId(activeProject)) return false
  return await detailRef.value?.open(id, activeProject)
}
const load = async () => {
  const token = ++listSequence
  const project = activeProject
  if (!isBusinessViewId(project) || contextBlocked.value || !canQuery()) { activities.value = []; loading.value = false; return }
  loading.value = true
  try {
    const result = await ReportApi.getActivities(legacyOwnerId(project))
    if (token !== listSequence || !canQuery()) return
    activities.value = result.filter(item => sameBusinessViewId(item.projectId, project))
  } finally { if (token === listSequence) loading.value = false }
}
const changed = () => { emit('changed'); void load() }
const contextKey = () => [String(props.projectId ?? query.projectId ?? ''), String(props.objectId ?? '')].join('|')
const switchContext = async () => {
  const token = ++switchSequence
  const project = props.projectId ?? query.projectId
  const object = props.objectId
  if (sameBusinessViewId(project, activeProject) && String(object ?? '') === String(activeObject ?? '')) {
    contextBlocked.value = false
    return
  }
  listSequence++
  if (!(await requestLeave()) || token !== switchSequence) { if (token === switchSequence) contextBlocked.value = true; return }
  if (!discardChanges()) { contextBlocked.value = true; return }
  activeProject = project
  activeObject = object
  contextBlocked.value = !isBusinessViewId(project) || (object !== undefined && !isBusinessViewId(object))
  activities.value = []
  loading.value = false
  if (contextBlocked.value) return
  await load()
  if (token === switchSequence && isBusinessViewId(object)) await openDetail(object)
}
watch(() => props.projectId, value => { if (value !== undefined) query.projectId = value }, { immediate: true })
watch(contextKey, switchContext)
onMounted(() => {
  if (props.projectId === undefined && isBusinessViewId(route.query.projectId)) query.projectId = route.query.projectId
  else void switchContext()
})
onBeforeRouteLeave(requestLeave)
onBeforeUnmount(() => { listSequence++; switchSequence++ })
const typeLabel = (type: string) => (type === 'FINAL' ? '终验' : '初验')
const activityStatusLabel = (status: string) => ({ PENDING: '待完成', COMPLETED: '已完成' } as Record<string, string>)[status] || status
defineExpose({ requestLeave, discardChanges, isDirty: () => detailRef.value?.isDirty() ?? false })
</script>

<style scoped lang="scss">
.page-heading, .query-form, .activity-title { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h1 { margin: 0; font-size: 24px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.query-form { justify-content: flex-start; margin-top: 20px; }.query-form :deep(.el-form-item) { margin-bottom: 0; }.project-select { width: min(440px, 100%); }
.activity-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.activity-card { padding: 20px; border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-blank); }
.activity-title { align-items: flex-start; }.activity-title h2 { margin: 4px 0 0; font-size: 18px; color: var(--el-text-color-primary); }.eyebrow { font-size: 12px; color: var(--el-color-primary); }
.activity-card dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 20px 0; }.activity-card dt { font-size: 12px; color: var(--el-text-color-secondary); }.activity-card dd { margin: 4px 0 0; color: var(--el-text-color-primary); }.open-button { width: 100%; }
@media (width <= 767px) { .page-heading, .query-form { align-items: stretch; flex-direction: column; }.query-form .el-button { width: 100%; }.activity-grid { grid-template-columns: 1fr; }.activity-card dl { grid-template-columns: 1fr 1fr; } }
</style>
