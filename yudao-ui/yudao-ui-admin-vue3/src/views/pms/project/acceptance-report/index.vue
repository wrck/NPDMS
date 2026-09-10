<template>
  <ContentWrap>
    <el-alert v-if="embedded" title="报告发布/附件办理尚未接入当前任务上下文，请从原业务入口处理" type="info" :closable="false" show-icon />
    <el-form :model="query" inline class="-mb-15px query-form" @submit.prevent>
      <el-form-item label="项目">
        <el-input v-if="scoped" :model-value="projectName || `项目 #${projectId}`" disabled class="!w-220px" />
        <PmsEntitySelect v-else v-model="query.projectId" :api="ProjectApi.getProjectPage" :label-field="['projectCode', 'projectName']" value-field="id" query-field="projectName" placeholder="选择项目查看初验与终验" clearable :disabled="detailRef?.isDirty()" class="!w-220px" />
      </el-form-item>
      <el-form-item><el-button :loading="loading" :disabled="!validProject" @click="load"><Icon icon="ep:search" />查询报告活动</el-button></el-form-item>
    </el-form>
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" />
  </ContentWrap>

  <ContentWrap>
    <el-skeleton v-if="loading" :rows="4" animated aria-label="正在加载验收报告活动" />
    <el-table v-else :data="activities" data-testid="acceptance-activities" aria-label="验收报告活动列表"
      :empty-text="errorText ? '验收活动未加载成功' : validProject ? '当前可见范围暂无验收活动，不自动创建或判定完成' : '请选择有权限的项目'">
      <el-table-column prop="id" label="活动编号" min-width="180" />
      <el-table-column label="验收类型" width="110"><template #default="{ row }">{{ typeLabel(row.acceptanceType) }}</template></el-table-column>
      <el-table-column label="活动状态" width="110"><template #default="{ row }"><el-tag :type="row.activityStatus === 'COMPLETED' ? 'success' : 'warning'">{{ activityStatusLabel(row.activityStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="projectTaskId" label="来源任务" min-width="170" />
      <el-table-column prop="version" label="活动版本" width="100" />
      <el-table-column label="当前报告" width="110"><template #default="{ row }">{{ row.currentReportVersionId ? '已生效' : '未生效' }}</template></el-table-column>
      <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row.id)">进入报告工作台</el-button></template></el-table-column>
    </el-table>
  </ContentWrap>
  <AcceptanceReportDetail ref="detailRef" :readonly="readonly || contextBlocked" :allowed-actions="allowedActions" @changed="changed" @dirty-change="emit('dirty-change', $event)" />
</template>

<script setup lang="ts">
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import * as ProjectApi from '@/api/pms/project/projects'
import { checkPermi } from '@/utils/permission'
import { isBusinessViewId, legacyOwnerId, sameBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import type { AcceptanceActivityVO } from '@/api/pms/project/acceptance-report'
import AcceptanceReportDetail from './detail.vue'

defineOptions({ name: 'PmsAcceptanceReport' })
const props = defineProps<{ projectId?: number | string; projectName?: string; objectId?: number | string; readonly?: boolean; allowedActions?: string[] }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const route = useRoute()
const loading = ref(false)
const errorText = ref('')
const activities = ref<AcceptanceActivityVO[]>([])
const detailRef = ref<InstanceType<typeof AcceptanceReportDetail>>()
const query = reactive<{ projectId?: BusinessViewId }>({})
const scoped = computed(() => props.projectId !== undefined)
const effectiveProjectId = computed(() => scoped.value ? props.projectId : query.projectId)
const validProject = computed(() => isBusinessViewId(effectiveProjectId.value))
const embedded = computed(() => props.objectId !== undefined || props.allowedActions !== undefined)
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
  errorText.value = ''
  if (!isBusinessViewId(project) || contextBlocked.value || !canQuery()) { activities.value = []; loading.value = false; return }
  loading.value = true
  try {
    const result = await ReportApi.getActivities(legacyOwnerId(project))
    if (token !== listSequence || !canQuery()) return
    activities.value = result.filter(item => sameBusinessViewId(item.projectId, project))
  } catch {
    if (token === listSequence) {
      activities.value = []
      errorText.value = '验收活动加载失败，请检查访问权限或重试；这不代表验收已完成。'
    }
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
  if (contextBlocked.value) { errorText.value = '项目上下文无效，未查询其他项目。'; return }
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
onBeforeRouteUpdate(requestLeave)
watch(() => route.query.projectId, value => {
  if (!scoped.value) query.projectId = isBusinessViewId(value) ? value : undefined
})
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!detailRef.value?.isDirty()) return
  event.preventDefault()
  event.returnValue = ''
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { listSequence++; switchSequence++; window.removeEventListener('beforeunload', beforeUnload) })
const typeLabel = (type: string) => (type === 'FINAL' ? '终验' : '初验')
const activityStatusLabel = (status: string) => ({ PENDING: '待完成', COMPLETED: '已完成' } as Record<string, string>)[status] || status
defineExpose({ requestLeave, discardChanges, isDirty: () => detailRef.value?.isDirty() ?? false })
</script>

<style scoped lang="scss">
@media (width <= 767px) { .query-form :deep(.el-form-item) { width: 100%; } }
</style>
