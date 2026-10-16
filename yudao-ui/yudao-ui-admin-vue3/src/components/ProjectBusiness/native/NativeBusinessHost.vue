<template>
  <template v-for="item in bridgeList" :key="item.api.base">
    <el-alert v-if="item.state.error.value" :title="item.state.error.value" type="error" :closable="false" />
    <el-alert v-if="item.state.pending.value" title="有一次业务操作结果尚未确认" type="warning" :closable="false">
      <el-button :loading="item.state.executing.value" @click="recover(item)">查询操作回执</el-button>
    </el-alert>
  </template>
  <el-alert v-if="projectError" :title="projectError" type="error" :closable="false"><el-button @click="loadProject()">重新读取项目</el-button></el-alert>
  <ProjectBusinessFieldConfiguration v-if="showConfiguration && !readonly && can('configure-fields') && bridge.state.model.value?.operations.some(item=>item.kind==='CONFIGURE' && item.executable)"
    ref="configuration" :api="bridge.api" :project-id="scopeProjectId"
    :disabled="bridge.preparing.value || bridge.state.executing.value" @changed="reloadConfiguration" />
  <div :key="`${scopeProjectId}-${epoch}`" class="min-w-0"><slot>
    <component v-if="!needsProject || project" ref="pageRef" :is="page" :key="`${scopeProjectId}-${epoch}`" v-bind="pageProps" v-on="pageEvents" />
  </slot></div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, onErrorCaptured, provide, ref, shallowRef, useSlots, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { createProjectBusinessApi, type BusinessId, type BusinessPageInitialData } from '@/api/pms/platform/business'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'
import { nativeBusinessKey, type NativeBusinessContext, type NativeFormHandle } from './nativeBusinessContext'
import { createNativeBusinessBridge, nativeId, NativeBusinessOperationFailure } from './nativeBusinessBridge'
import { nativeEngineeringApi } from './nativeEngineeringApi'
import { nativeTrainingApi } from './nativeTrainingApi'
import { nativeRequirementApi } from './nativeRequirementApi'
import { nativeProcurementApi } from './nativeProcurementApi'
import { nativeSolutionApi, nativeSolutionReviewApi, nativeStagePlanApi, nativeDurationApi } from './nativePlanApi'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import * as StagePlanApi from '@/api/pms/engineering/stage-plan'
import * as ReviewApi from '@/api/pms/engineering/solution-review'
import { nativeContactApi } from './nativeContactApi'
import { nativeAcceptanceApi } from './nativeAcceptanceApi'
import * as AcceptanceApi from '@/api/pms/acceptance/acceptance-report'
import * as RequirementApi from '@/api/pms/engineering/requirement-analysis/entity'
import { getProject, type ProjectMasterVO } from '@/api/pms/project/projects'
import { nativeBusinessKind, nativeBusinessKinds, nativeBusinessApiBase, type NativeBusinessKind } from './nativeBusinessKinds'
import ProjectBusinessFieldConfiguration from '../ProjectBusinessFieldConfiguration.vue'
import { initialBusinessPage } from '../initialBusinessPage'

const props = defineProps<{ apiBase: string; stableCode: string; scopeProjectId?: BusinessId; initialEntityId?: BusinessId;
  readonly?: boolean; allowedActions?: string[]; execution?: ProjectBusinessExecutionSelection; stageCode?: string;
  bindingParameters?: Record<string, unknown>; showConfiguration?: boolean; initialProject?: ProjectMasterVO; initialization?: BusinessPageInitialData }>()
const entryEntityId = props.initialEntityId
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const slots = useSlots()
onErrorCaptured(error => {
  if (error instanceof NativeBusinessOperationFailure) {
    ElMessage.error(error.message)
    return false
  }
})
const pages = {
  IMP_ARRIVAL: defineAsyncComponent(() => import('@/views/pms/project/project-master-detail/components/ProjectArrivalReceiptPanel.vue')),
  IMP_INSTALLATION: defineAsyncComponent(() => import('@/views/pms/engineering/installation/ProjectInstallationWorkbench.vue')),
  IMP_CONFIGURATION: defineAsyncComponent(() => import('@/views/pms/engineering/configuration/with-device-logs.vue')),
  IMP_JOINT_TEST: defineAsyncComponent(() => import('@/views/pms/engineering/joint-test/with-device-logs.vue')),
  IMP_TRAINING: defineAsyncComponent(() => import('@/views/pms/engineering/training/index.vue')),
  SOL_REQUIREMENT_ANALYSIS_REVISION: defineAsyncComponent(() => import('@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue')),
  SOL_SITE_SURVEY: defineAsyncComponent(() => import('@/views/pms/delivery-business/site-survey/index.vue')),
  RES_OUTSOURCE_REQUEST: defineAsyncComponent(() => import('@/views/pms/engineering/outsource/index.vue')),
  IMP_MATERIAL_REQUISITION: defineAsyncComponent(() => import('@/views/pms/engineering/material-req/index.vue')),
  IMP_EXTERNAL_PROCUREMENT: defineAsyncComponent(() => import('@/views/pms/engineering/ext-proc/index.vue')),
  IMP_MATERIAL_EXCHANGE: defineAsyncComponent(() => import('@/views/pms/engineering/material-exch/index.vue')),
  SOL_SOLUTION: defineAsyncComponent(() => import('@/views/pms/engineering/solution-reviewed/index.vue')),
  SOL_CONSTRUCTION_PLAN: defineAsyncComponent(() => import('@/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue')),
  SOL_STAGE_PLAN_BATCH: defineAsyncComponent(() => import('@/views/pms/project/project-master-detail/components/ProjectSchedulePanel.vue')),
  CUS_PROJECT_CONTACT: defineAsyncComponent(() => import('@/views/pms/customer/contacts/index.vue')),
  ACC_ACCEPTANCE_ACTIVITY: defineAsyncComponent(() => import('@/views/pms/acceptance/acceptance-report/index.vue'))
}
const page = computed(() => pages[props.stableCode as keyof typeof pages])
const projectId = computed(() => props.scopeProjectId == null ? undefined : nativeId(props.scopeProjectId) as number)
const pageRef = ref<{ requestLeave?: () => Promise<boolean> }>()
const needsProject = computed(() => ['SOL_REQUIREMENT_ANALYSIS_REVISION', 'SOL_CONSTRUCTION_PLAN', 'SOL_STAGE_PLAN_BATCH'].includes(props.stableCode))
const project = shallowRef<ProjectMasterVO>(), projectError = ref('')
let projectRequest = 0
const loadProject = async (useInitialProject = false) => {
  if (!needsProject.value || slots.default || projectId.value == null) return
  const request = ++projectRequest
  projectError.value = ''; project.value = undefined
  if (useInitialProject && props.initialProject?.id != null && String(props.initialProject.id) === String(projectId.value)) {
    project.value = props.initialProject
    return
  }
  try { const value = await getProject(projectId.value); if (request === projectRequest) project.value = value }
  catch { if (request === projectRequest) projectError.value = '项目上下文读取失败，请重试。' }
}
watch(() => props.scopeProjectId, () => loadProject(true), { immediate: true })
const pageProps = computed(() => {
  const basic = { projectId: projectId.value, readonly: props.readonly }
  if (props.stableCode === 'ACC_ACCEPTANCE_ACTIVITY') return { ...basic, objectId: entryEntityId,
    allowedActions: props.allowedActions?.flatMap(action => ({ QUERY: ['QUERY'], create: ['CREATE'],
      'report-create': ['UPDATE'], 'report-save': ['UPDATE'], 'report-publish': ['PUBLISH'],
      'report-revoke': ['REVOKE'], FILE_WRITE: ['FILE_WRITE'], delivery: ['FILE_WRITE'] }[action] || [])) }
  if (props.stableCode === 'SOL_SITE_SURVEY') return { ...basic, objectId: props.initialEntityId,
    stageExecution: props.execution?.stage, taskExecution: props.execution?.task, taskId: props.execution?.task?.taskId, stageCode: props.stageCode,
    allowedActions: props.allowedActions?.flatMap(action => ({ create: ['CREATE'], save: ['UPDATE'], delete: ['DELETE'],
      confirm: ['CONFIRM'], reject: ['REJECT'], archive: ['ARCHIVE'], QUERY: ['QUERY'] }[action] || [])) }
  // The inherited service owns independent business objects. The enclosing host applies the
  // frozen node context; the legacy page must not resolve its different WorkBinding identity.
  if (props.stableCode === 'SOL_REQUIREMENT_ANALYSIS_REVISION') return { project: project.value, readonly: props.readonly,
    revisionId: props.initialEntityId,
    allowedActions: props.allowedActions?.flatMap(action => ({ create: ['CREATE_INITIAL_DRAFT', 'CREATE_DRAFT'],
      save: ['PATCH_FORM'], complete: ['COMPLETE', 'SUBMIT'], copy: ['CREATE_DRAFT'], QUERY: ['QUERY'] }[action] || [])) }
  if (props.stableCode === 'SOL_STAGE_PLAN_BATCH') return { project: project.value, readonly: props.readonly }
  return needsProject.value ? { ...basic, project: project.value } : basic
})
const pageEvents = computed(() => props.stableCode === 'SOL_STAGE_PLAN_BATCH'
  ? { changed: () => emit('changed') }
  : { changed: () => emit('changed'), saved: () => emit('changed'), 'dirty-change': (value: boolean) => emit('dirty-change', value) })
const rootKind = nativeBusinessKind(props.stableCode)
if (!rootKind || nativeBusinessApiBase(rootKind) !== props.apiBase) throw new Error('专用业务界面与接口不匹配')
const can = (action: string, kind = rootKind) => !props.readonly && (props.allowedActions == null ||
  (kind === rootKind ? props.allowedActions.includes(action) : props.allowedActions.some(item => ['create', 'save'].includes(item))))
const initialReads = new Map<string, ReturnType<typeof initialBusinessPage>>()
const createApi = (base: string) => {
  const api = createProjectBusinessApi(base)
  const pages = props.initialization ? [props.initialization, ...props.initialization.relatedPages] : []
  const initial = pages.find(data => {
    const kind = nativeBusinessKind(data.model.stableCode)
    return kind && nativeBusinessApiBase(kind) === base && String(data.request.projectId) === String(props.scopeProjectId)
  })
  if (!initial) return api
  const read = initialBusinessPage(api, initial); initialReads.set(base, read)
  return read.api
}
const bridge = createNativeBusinessBridge(createApi(props.apiBase), () => props.scopeProjectId, can, () => props.bindingParameters ?? {})
const bridges = new Map<string, typeof bridge>([[rootKind, bridge]])
const bridgeList = shallowRef([bridge])
const configuration = ref<InstanceType<typeof ProjectBusinessFieldConfiguration>>()
const reloadConfiguration = async () => {
  try { await bridge.reloadModel() }
  catch { bridge.state.error.value = '配置已保存，重新读取失败，请刷新页面后重试。' }
}
const getBridge = (kind: string = rootKind) => {
  if (!Object.hasOwn(nativeBusinessKinds, kind)) throw new Error('不支持的业务适配类型')
  if (!bridges.has(kind)) {
    bridges.set(kind, createNativeBusinessBridge(createApi(nativeBusinessApiBase(kind as NativeBusinessKind)),
      () => props.scopeProjectId, action => can(action, kind as NativeBusinessKind)))
    bridgeList.value = [...bridges.values()]
  }
  return bridges.get(kind)!
}
const forms = new Set<NativeFormHandle>(), epoch = ref(0)
const context: NativeBusinessContext = {
  rootKind, bridge, getBridge, projectId: () => projectId.value, can,
  initialEntityId: kind => kind === rootKind ? props.initialEntityId : undefined,
  changed: () => emit('changed'),
  api: (kind, original) => ({ ...original, ...(kind === 'training' ? nativeTrainingApi(getBridge(kind))
    : kind === 'contact' ? nativeContactApi(getBridge(kind))
    : kind === 'acceptance' ? nativeAcceptanceApi(getBridge(kind), AcceptanceApi)
    : kind === 'solution' ? nativeSolutionApi(getBridge(kind))
    : kind === 'solutionReview' ? nativeSolutionReviewApi(getBridge('solution'), ReviewApi)
    : kind === 'stagePlan' ? nativeStagePlanApi(getBridge(kind), StagePlanApi,
      projectId => initialReads.get(nativeBusinessApiBase('stagePlan'))?.takeContext(projectId))
    : kind === 'duration' ? nativeDurationApi(getBridge(kind), DurationApi)
    : kind === 'requirement' ? nativeRequirementApi(getBridge(kind), RequirementApi)
      : nativeProcurementApi(kind, getBridge(kind)) ?? nativeEngineeringApi(kind, getBridge(kind), original)) }),
  Form: defineAsyncComponent(() => import('./NativeBusinessFormSupport.vue')),
  Attachment: defineAsyncComponent(() => import('./NativeBusinessAttachment.vue')),
  registerForm: handle => { forms.add(handle); return () => { forms.delete(handle) } },
  formInput: async (kind = rootKind) => Object.assign({}, ...await Promise.all([...forms]
    .filter(form => (form.kind ?? rootKind) === kind).map(form => form.buildInput()))),
  requestLeave: async kind => {
    const relevant = kind ? [getBridge(kind)] : [...bridges.values()]
    if (relevant.some(item => item.preparing.value || item.state.executing.value || item.state.pending.value)) return false
    for (const form of forms) if ((!kind || (form.kind ?? rootKind) === kind) && !await form.requestLeave()) return false
    return true
  }
}
provide(nativeBusinessKey, context)
const requestLeave = async () => {
  if (configuration.value && !await configuration.value.requestLeave()) return false
  if (!await context.requestLeave()) return false
  if (pageRef.value?.requestLeave && !await pageRef.value.requestLeave()) return false
  return true
}
const recover = async (item: typeof bridge) => { if (await item.state.recover()) { epoch.value++; emit('changed') } }
onBeforeRouteLeave(requestLeave); onBeforeRouteUpdate(requestLeave)
// An automatic first link to this workspace's own successful command is metadata refresh,
// not a request to discard the native editor. Other object changes still use the host leave guard.
const canAdoptAssociatedObject = (id: BusinessId) => {
  const receipt = bridge.state.receipt.value
  return entryEntityId == null && receipt?.entityRef != null && !['FAILED', 'DELETED'].includes(receipt.outcome)
    && String(receipt.entityRef.entityId) === String(id)
}
defineExpose({ requestLeave, canAdoptAssociatedObject })
</script>
