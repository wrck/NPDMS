<template>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <header class="report-heading"><h2>{{ acceptanceType ? typeLabel(acceptanceType) : '初验与终验' }}</h2><p>上传验收交付件，保存后记录验收结果。</p></header>
    <el-form :model="query" inline class="-mb-15px query-form" @submit.prevent>
      <el-form-item v-if="!scoped" label="项目">
        <PmsEntitySelect v-model="query.projectId" :api="ProjectApi.getProjectPage" :label-field="['projectCode', 'projectName']" value-field="id" query-field="projectName" placeholder="选择项目查看初验与终验" clearable :disabled="creating || detailRef?.isDirty()" class="!w-220px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable class="!w-160px" aria-label="验收状态">
          <el-option label="待完成" value="PENDING" />
          <el-option label="已完成" value="COMPLETED" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button :loading="loading" :disabled="!validProject" @click="load"><Icon icon="ep:search" />查询</el-button></el-form-item>
      <el-form-item v-if="canCreateActivity('PRELIMINARY') || canCreateActivity('FINAL')">
        <el-button v-if="canCreateActivity('PRELIMINARY')" type="primary" :loading="creating" :disabled="creating || detailRef?.isDirty()" @click="createActivity('PRELIMINARY')"><Icon icon="ep:plus" />上传初验交付件</el-button>
        <el-button v-if="canCreateActivity('FINAL')" type="primary" :loading="creating" :disabled="creating || detailRef?.isDirty()" @click="createActivity('FINAL')"><Icon icon="ep:plus" />上传终验交付件</el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" />
  </ContentWrap>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <el-skeleton v-if="loading" :rows="4" animated aria-label="正在加载验收报告活动" />
    <el-table
      v-else :data="visibleActivities" stripe data-testid="acceptance-activities" aria-label="验收报告活动列表"
      :empty-text="errorText ? '验收记录未加载成功' : validProject ? '暂无验收记录' : '请选择有权限的项目'">
      <el-table-column prop="id" label="验收编号" min-width="180" />
      <el-table-column label="验收类型" width="110"><template #default="{ row }">{{ typeLabel(row.acceptanceType) }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.activityStatus === 'COMPLETED' ? 'success' : 'warning'">{{ activityStatusLabel(row.activityStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="当前报告" width="110"><template #default="{ row }">{{ row.currentReportVersionId ? '已生效' : '未生效' }}</template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row.id, opensEditorFromList(row))">{{ opensEditorFromList(row) ? '编辑' : '查看' }}</el-button></template></el-table-column>
    </el-table>
  </ContentWrap>
  <AcceptanceReportDetail ref="detailRef" :readonly="detailReadonly" :allowed-actions="allowedActions" @changed="changed" @dirty-change="emit('dirty-change', $event)" />
</template>
<script setup lang="ts">
import { generateUUID } from '@/utils'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import * as ProjectApi from '@/api/pms/project/projects'
import { checkPermi } from '@/utils/permission'
import { isBusinessViewId, legacyOwnerId, sameBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import * as ReportApi from '@/api/pms/acceptance/acceptance-report'
import type { AcceptanceActivityVO, AcceptanceType } from '@/api/pms/acceptance/acceptance-report'
import AcceptanceReportDetail from './detail.vue'
import { activityAllowsReportWrite } from './reportState'
import { acceptanceUploadIntentKey } from './acceptanceUploadIntent'

defineOptions({ name: 'PmsAcceptanceReport' })
const props = defineProps<{ projectId?: number | string; projectName?: string; objectId?: number | string; acceptanceType?: AcceptanceType; readonly?: boolean; allowedActions?: string[] }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const route = useRoute()
const loading = ref(false)
const creating = ref(false)
let pendingCreate: { key: string; request: ReportApi.IndependentAcceptanceCreate } | undefined
const errorText = ref('')
const activities = ref<AcceptanceActivityVO[]>([])
const detailRef = ref<InstanceType<typeof AcceptanceReportDetail>>()
const query = reactive<{ projectId?: BusinessViewId; status?: string }>({})
const visibleActivities = computed(() => activities.value.filter(row => !query.status || row.activityStatus === query.status))
const uploadIntent = inject(acceptanceUploadIntentKey, undefined)
const scoped = computed(() => props.projectId !== undefined)
const effectiveProjectId = computed(() => scoped.value ? props.projectId : query.projectId)
const validProject = computed(() => isBusinessViewId(effectiveProjectId.value))
const embedded = computed(() => props.objectId !== undefined || props.allowedActions !== undefined)
const contextBlocked = ref(false)
const selectedDetail = ref<BusinessViewId>()
const detailReadonly = computed(() => props.readonly || contextBlocked.value
  || (embedded.value && selectedDetail.value !== undefined && (props.objectId === undefined || !sameBusinessViewId(props.objectId, selectedDetail.value))))
let listSequence = 0, switchSequence = 0
let activeProject: BusinessViewId | undefined, activeObject: BusinessViewId | undefined
let activeType: AcceptanceType | undefined
const canQuery = () => (props.allowedActions === undefined || props.allowedActions.includes('QUERY')) && checkPermi(['pms:acceptance:report:query'])
const canCreateActivity = (type: AcceptanceType) => !props.readonly && !contextBlocked.value && validProject.value && canQuery()
  && checkPermi(['pms:acceptance:report:write']) && (!embedded.value || (props.acceptanceType === type && !activities.value.length
    && (props.allowedActions?.includes('MANAGE') || (props.allowedActions?.includes('UPDATE') && props.allowedActions?.includes('PUBLISH')))))
const canUploadActivity = (row?: AcceptanceActivityVO) => !!row && !props.readonly && !contextBlocked.value && canQuery()
  && activityAllowsReportWrite(row) && checkPermi(['pms:acceptance:report:write'])
  && (!embedded.value || (props.objectId !== undefined && sameBusinessViewId(row.id, props.objectId)))
  && (props.allowedActions === undefined || props.allowedActions.includes('UPDATE'))
const opensEditorFromList = (row: AcceptanceActivityVO) => row.activityStatus === 'PENDING' && canUploadActivity(row)
const requestLeave = async () => !creating.value && (await detailRef.value?.requestLeave() ?? true)
const discardChanges = () => {
  if (detailRef.value?.discardChanges() === false) return false
  listSequence++; emit('dirty-change', false); return true
}
const openDetail = async (id: BusinessViewId, upload = false) => {
  if (contextBlocked.value || !canQuery() || !isBusinessViewId(activeProject)
      || ((activeObject !== undefined || activeType !== undefined) && !activities.value.some(row => sameBusinessViewId(row.id, id)))
      || (upload && !canUploadActivity(activities.value.find(row => sameBusinessViewId(row.id, id))))) return false
  const project = activeProject, token = switchSequence
  selectedDetail.value = id
  await nextTick()
  if (token !== switchSequence || contextBlocked.value || !canQuery()) return false
  return await detailRef.value?.open(id, project, upload)
}
const load = async () => {
  const token = ++listSequence, project = activeProject, object = activeObject, acceptanceType = activeType
  errorText.value = ''
  if (!isBusinessViewId(project) || contextBlocked.value || !canQuery()) { activities.value = []; loading.value = false; return }
  loading.value = true
  try {
    const result = await ReportApi.getActivities(legacyOwnerId(project))
    if (token !== listSequence || !canQuery()) return
    const projectReports = result.filter(item => sameBusinessViewId(item.projectId, project))
    const boundReport = object === undefined ? undefined : projectReports.find(item => sameBusinessViewId(item.id, object))
    const type = acceptanceType ?? boundReport?.acceptanceType
    activities.value = type ? projectReports.filter(item => item.acceptanceType === type)
      : object === undefined ? projectReports : []
  } catch {
    if (token === listSequence) { activities.value = []; errorText.value = '验收活动加载失败，请检查访问权限或重试；这不代表验收已完成。' }
  } finally { if (token === listSequence) loading.value = false }
}
const changed = () => { emit('changed'); void load() }
const createActivity = async (acceptanceType: ReportApi.AcceptanceType) => {
  const project = activeProject
  if (creating.value || !canCreateActivity(acceptanceType) || !isBusinessViewId(project) || !(await requestLeave())) return
  creating.value = true; errorText.value = ''; emit('dirty-change', true)
  let created = false
  try {
    if (!pendingCreate || !sameBusinessViewId(pendingCreate.request.projectId, project) || pendingCreate.request.acceptanceType !== acceptanceType) {
      const context = await ReportApi.getIndependentContext(legacyOwnerId(project))
      if (!sameBusinessViewId(context.projectId, project)) { errorText.value = '验收项目上下文不一致，请刷新后重试。'; return }
      if (context.lifecycleStatus !== 'ACTIVE') { errorText.value = '当前项目未在执行中，无法创建验收。'; return }
      pendingCreate = { key: generateUUID(), request: { projectId: legacyOwnerId(project), acceptanceType,
        expectedProjectVersion: context.projectVersion, expectedTreeVersion: context.treeVersion } }
    }
    const result = await ReportApi.createIndependent(pendingCreate.request, pendingCreate.key)
    if (!isBusinessViewId(result.acceptanceId) || !sameBusinessViewId(result.projectId, project) || result.acceptanceType !== acceptanceType) {
      errorText.value = '验收创建结果与当前项目或类型不一致，请刷新后核对。'; return
    }
    pendingCreate = undefined
    created = true
    if (embedded.value && uploadIntent) uploadIntent.value = { projectId: project, acceptanceId: result.acceptanceId, acceptanceType }
    if (sameBusinessViewId(activeProject, project)) {
      await load()
      if (!embedded.value) await openDetail(result.acceptanceId, true)
    }
  } catch { errorText.value = '验收创建未完成，请检查项目权限或重试。' }
  finally { creating.value = false; emit('dirty-change', false); if (created) emit('changed') }
}
const contextKey = () => [String(props.projectId ?? query.projectId ?? ''), String(props.objectId ?? ''), props.acceptanceType || ''].join('|')
const switchContext = async () => {
  const token = ++switchSequence, project = props.projectId ?? query.projectId, object = props.objectId
  if (sameBusinessViewId(project, activeProject) && String(object ?? '') === String(activeObject ?? '') && props.acceptanceType === activeType) { contextBlocked.value = false; return }
  listSequence++
  if (!(await requestLeave()) || token !== switchSequence) { if (token === switchSequence) contextBlocked.value = true; return }
  if (!discardChanges()) { contextBlocked.value = true; return }
  activeProject = project; activeObject = object; activeType = props.acceptanceType
  contextBlocked.value = !isBusinessViewId(project) || (object !== undefined && !isBusinessViewId(object))
  activities.value = []; loading.value = false
  if (contextBlocked.value) { errorText.value = '项目上下文无效，未查询其他项目。'; return }
  await load()
}
watch(() => props.projectId, value => { if (value !== undefined) query.projectId = value }, { immediate: true })
watch(contextKey, switchContext)
watch(canQuery, allowed => {
  if (allowed) void load()
  else { listSequence++; activities.value = []; loading.value = false }
})
// Only a preceding manual upload click may reopen after the Host selects its new Owner object.
watch(() => [uploadIntent?.value, props.objectId, props.allowedActions, props.readonly, loading.value], async () => {
  const intent = uploadIntent?.value
  if (!intent || loading.value || !sameBusinessViewId(intent.projectId, activeProject)
      || intent.acceptanceType !== props.acceptanceType || !sameBusinessViewId(intent.acceptanceId, props.objectId)
      || !canUploadActivity(activities.value.find(row => sameBusinessViewId(row.id, intent.acceptanceId)))) return
  uploadIntent!.value = undefined
  await openDetail(intent.acceptanceId, true)
}, { deep: true, flush: 'post' })
onMounted(() => {
  if (props.projectId === undefined && isBusinessViewId(route.query.projectId)) query.projectId = route.query.projectId
  else void switchContext()
})
onBeforeRouteLeave(requestLeave)
onBeforeRouteUpdate(requestLeave)
watch(() => route.query.projectId, value => { if (!scoped.value) query.projectId = isBusinessViewId(value) ? value : undefined })
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!detailRef.value?.isDirty()) return
  event.preventDefault(); event.returnValue = ''
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { listSequence++; switchSequence++; window.removeEventListener('beforeunload', beforeUnload) })
const typeLabel = (type: string) => (type === 'FINAL' ? '终验' : '初验')
const activityStatusLabel = (status: string) => ({ PENDING: '待完成', COMPLETED: '已完成' } as Record<string, string>)[status] || status
defineExpose({ requestLeave, discardChanges, isDirty: () => detailRef.value?.isDirty() ?? false })
</script>
<style scoped lang="scss">
.report-heading { margin-bottom: 20px; }
.report-heading h2 { margin: 0 0 6px; font-size: 16px; font-weight: 600; }
.report-heading p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
@media (width <= 767px) { .query-form :deep(.el-form-item) { width: 100%; } }
</style>
