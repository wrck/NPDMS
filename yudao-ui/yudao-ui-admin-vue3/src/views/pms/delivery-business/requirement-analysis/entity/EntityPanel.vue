<template>
  <div class="min-w-0 w-full max-w-full">
    <ContentWrap :body-style="{ padding: '20px' }">
      <div class="requirement-toolbar">
        <div><strong>需求分析</strong><p class="requirement-caption">维护项目需求与附件，按版本保存和确认。</p></div>
        <div class="requirement-actions">
          <el-button :loading="loading" @click="refreshWorkspace"><Icon icon="ep:refresh" />刷新</el-button>
          <el-button v-if="canCreateInitial" :loading="commandLoading" type="primary" @click="createInitial">创建需求分析草稿</el-button>
          <el-button v-if="canRevise" :loading="commandLoading" type="primary" @click="createRevision">从查看版本创建草稿</el-button>
          <el-button :disabled="!detail" @click="openHistory">修订记录</el-button>
        </div>
      </div>
      <el-divider />
      <el-skeleton v-if="loading && !overview" :rows="7" animated />
      <el-alert v-else-if="errorText" :title="errorText" type="error" show-icon :closable="false">
        <template #default><el-button link type="primary" @click="load">重新加载</el-button></template>
      </el-alert>
      <template v-else-if="overview">
        <div class="revision-options" data-testid="requirement-version-table" aria-label="选择需求分析版本">
          <button v-for="item in currentVersions" :key="String(item.revision.ref.revisionId)"
            class="revision-option" :class="{ 'is-selected': item.revision.ref.revisionId === selectedRevisionId }"
            :aria-pressed="item.revision.ref.revisionId === selectedRevisionId"
            @click="selectVersion(item.revision.ref.revisionId)">
            <strong>{{ item.revision.state === 'DRAFT' ? '编辑当前草稿' : '查看有效完成版' }}</strong>
            <span>V{{ item.revision.revisionNo }} · {{ statusLabel(item.revision.state) }}</span>
            <small v-if="item.revision.frozenAt">{{ formatDateTime(item.revision.frozenAt) }}</small>
          </button>
        </div>
        <el-alert v-if="commandError" :title="commandError" type="warning" show-icon closable @close="commandError = ''" />
      </template>
    </ContentWrap>
    <ContentWrap v-if="overview" :body-style="{ padding: '20px' }">
      <el-empty v-if="!selectedRevisionId" description="创建草稿后可填写11项核心内容及项目模板扩展项" />
      <el-skeleton v-else-if="detailLoading" :rows="8" animated />
      <template v-else-if="detail">
        <div class="analysis-heading">
          <div><h3>项目需求分析</h3><p>记录客户目标、网络现状和实施要求，作为实施方案与工程交底的输入。</p></div>
          <el-tag :type="detail.revision.state === 'DRAFT' ? 'warning' : 'success'">{{ relationLabel }} · V{{ detail.revision.revisionNo }}</el-tag>
        </div>
        <EntityForm
          ref="dynamicFormRef"
          :key="`${detail.revision.ref.revisionId}-${detail.extensionValueVersion}`"
          :detail="detail" :allowed-actions="detailActions" :reload="reloadSelectedDetail" :execution="loadedExecution"
          @dirty-change="formDirty = $event" @saved="emit('changed')"
        />
        <RequirementBriefingSection v-if="project.id" :project-id="project.id" :manage="openBriefing" />
        <el-button v-if="canComplete" :loading="commandLoading" type="success" class="mt-15px" @click="complete">完成并冻结当前草稿</el-button>
      </template>
    </ContentWrap>
  </div>
  <RevisionDrawer ref="historyRef" @view="viewHistorical" @compare="openCompare" />
  <CompareDrawer ref="compareRef" />
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'
import type { TaskExecutionContext } from '@/api/pms/project/task-business'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis/entity'
import { type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import type { EntityId, View, Workspace } from '@/api/pms/engineering/requirement-analysis/entity'
import { useMessage } from '@/hooks/web/useMessage'
import { onBeforeRouteLeave } from 'vue-router'
import EntityForm from './EntityForm.vue'
import RequirementBriefingSection from './RequirementBriefingSection.vue'
import RevisionDrawer from './RevisionDrawer.vue'
import CompareDrawer from './CompareDrawer.vue'
import { stableCommandIntent } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'

// PM-03: optional host restrictions narrow, never replace, the SOL Owner permissions.
const props = defineProps<{ project: ProjectMasterVO; revisionId?: BusinessViewId; stageExecution?: StageExecutionContext; taskExecution?: TaskExecutionContext; allowedActions?: string[]; readonly?: boolean; openBriefing?: () => unknown }>()
const selectedExecution = (): ProjectBusinessExecutionSelection | undefined =>
  props.taskExecution || props.stageExecution ? {
    ...(props.taskExecution ? { task: { ...props.taskExecution } } : {}),
    ...(props.stageExecution ? { stage: { ...props.stageExecution } } : {})
  } : undefined
const loadedExecution = ref<ProjectBusinessExecutionSelection>()
const emit = defineEmits<{ changed: []; 'dirty-change': [dirty: boolean] }>()
const restrictActions = <T extends string>(actions: T[]): T[] => props.readonly ? [] : actions.filter(
  (action) => props.allowedActions === undefined || props.allowedActions.includes(action)
)
const message = useMessage()
const loading = ref(false)
const detailLoading = ref(false)
const commandLoading = ref(false)
const errorText = ref('')
const commandError = ref('')
const overview = ref<Workspace>()
const currentVersions = computed(() => [overview.value?.draft, overview.value?.currentEffective].filter((item) => !!item))
const detail = ref<View>()
const selectedRevisionId = ref<EntityId>()
const historyRef = ref<InstanceType<typeof RevisionDrawer>>()
const compareRef = ref<InstanceType<typeof CompareDrawer>>()
const dynamicFormRef = ref<{
  save: () => Promise<boolean>
  discardChanges: () => void
  isSaving: () => boolean
}>()
const formDirty = ref(false)

const hasAction = (actions: string[], candidates: string[]) =>
  candidates.some((action) => actions.includes(action))
const relationLabel = computed(() => {
  if (detail.value?.revision.state === 'DRAFT') return '当前草稿'
  if (detail.value?.revision.effective) return '当前有效'
  return '历史完成版'
})
const overviewActions = computed(() => restrictActions(overview.value?.allowedActions || []))
const detailActions = computed(() => restrictActions(detail.value?.allowedActions || []))
const canCreateInitial = computed(
  () =>
    !overview.value?.draft &&
    !overview.value?.currentEffective &&
    hasAction(overviewActions.value, ['CREATE_INITIAL_DRAFT', 'CREATE_DRAFT'])
)
const canComplete = computed(
  () =>
    detail.value?.revision.state === 'DRAFT' && hasAction(detailActions.value, ['COMPLETE', 'SUBMIT'])
)
const canRevise = computed(
  () =>
    !overview.value?.draft &&
    hasAction(detailActions.value, ['CREATE_DRAFT'])
)

const formatDateTime = (value?: string) => (value ? formatDate(value) : '-')
const statusLabel = (status: string) => ({ DRAFT: '草稿', FROZEN: '已完成' })[status] || status
const commandErrorText = (error: any) => {
  const code = error?.data?.code || error?.code || error?.message
  return code ? `操作未完成：${String(code)}` : '操作未完成，请刷新权威事实后重试。'
}
let loadSequence = 0
const readDetail = async (revisionId: EntityId, projectId = props.project.id) => {
  const value = await RequirementAnalysisApi.read(revisionId)
  if (String(value.projectId) !== String(projectId) || String(value.revision.ref.revisionId) !== String(revisionId))
    throw new Error('需求分析版本不属于当前项目或对象不匹配')
  return value
}
const readWorkspaceDetail = async (
  current: Workspace,
  revisionId: EntityId | undefined,
  projectId: number
) => {
  if (String(current.projectId) !== String(projectId)) throw new Error('需求分析项目不匹配')
  // The workspace response already contains the complete draft/effective version.
  // Only an explicitly selected historical version needs a separate detail read.
  const selected = revisionId == null ? current.draft || current.currentEffective
    : [current.draft, current.currentEffective].find(
      (value) => value && String(value.revision.ref.revisionId) === String(revisionId)
    )
  if (selected && String(selected.projectId) !== String(projectId))
    throw new Error('需求分析版本不属于当前项目')
  return selected || (revisionId == null ? undefined : await readDetail(revisionId, projectId))
}
const loadDetail = async (revisionId: EntityId) => {
  const sequence = ++loadSequence
  detailLoading.value = true
  try {
    const value = await readDetail(revisionId)
    if (sequence !== loadSequence) return
    detail.value = value
    loadedExecution.value = selectedExecution()
    formDirty.value = false
    selectedRevisionId.value = revisionId
    return detail.value
  } catch (error) {
    if (sequence === loadSequence) throw error
  } finally {
    if (sequence === loadSequence) detailLoading.value = false
  }
}
const guardCurrentForm = async (target: string) => {
  if (dynamicFormRef.value?.isSaving()) return false
  if (!formDirty.value) return true
  try {
    await message.confirm(`当前表单尚未保存，是否放弃这些本地修改并${target}？`)
    if (dynamicFormRef.value?.isSaving()) return false
    dynamicFormRef.value?.discardChanges()
    return true
  } catch {
    return false
  }
}
const selectVersion = async (revisionId: EntityId) => {
  if (revisionId === selectedRevisionId.value) return
  if (!(await guardCurrentForm('切换版本'))) return
  errorText.value = ''
  try {
    await loadDetail(revisionId)
  } catch {
    detail.value = undefined
    errorText.value = '需求分析版本已变化或当前主体无权查看，请刷新后重试。'
  }
}
const load = async () => {
  const sequence = ++loadSequence
  const projectId = props.project.id
  const execution = selectedExecution()
  const revisionId = props.revisionId
  if (!projectId) return
  loading.value = true
  detailLoading.value = true
  errorText.value = ''
  try {
    const current = await RequirementAnalysisApi.workspace(projectId, props.stageExecution?.stageId, props.taskExecution?.taskId)
    if (sequence !== loadSequence) return
    const value = await readWorkspaceDetail(current, revisionId, projectId)
    if (sequence !== loadSequence) return
    overview.value = current
    detail.value = value
    loadedExecution.value = execution
    selectedRevisionId.value = value?.revision.ref.revisionId
    formDirty.value = false
  } catch {
    if (sequence !== loadSequence) return
    overview.value = undefined
    detail.value = undefined
    errorText.value = '需求分析工作区加载失败，请检查项目范围或稍后重试。'
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
      detailLoading.value = false
    }
  }
}
const refreshWorkspace = async () => {
  if (!(await guardCurrentForm('刷新'))) return
  await load()
}
const openHistory = async () => {
  if (!detail.value || !(await guardCurrentForm('查看修订记录'))) return
  historyRef.value?.open(
    detail.value!.revision.ref.entity.entityId,
    selectedRevisionId.value
  )
}

const createInitial = async () => {
  if (!props.project.id) return
  const payload = { projectId: props.project.id, projectVersion: props.project.version || 0,
    execution: selectedExecution() }
  const intent = stableCommandIntent('requirement-create', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.create(payload.projectId, intent.key, payload.execution)
    intent.clear()
    message.success('需求分析草稿已创建')
    await load()
  } catch (error) {
    commandError.value = commandErrorText(error)
  } finally {
    commandLoading.value = false
  }
}
const complete = async () => {
  if (!detail.value) return
  const payload = { revision: detail.value.revision, execution: loadedExecution.value }
  if (!(await guardCurrentForm('完成草稿'))) return
  await message.confirm('完成后正文与附件将永久冻结，是否继续？')
  const intent = stableCommandIntent('requirement-complete', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.complete(payload.revision, intent.key, payload.execution)
    intent.clear()
    message.success('需求分析已完成并冻结为当前有效版本')
    await load()
  } catch (error) {
    commandError.value = commandErrorText(error)
  } finally {
    commandLoading.value = false
  }
}
const createRevision = async () => {
  if (!detail.value) return
  const payload = { revision: detail.value.revision, execution: selectedExecution() }
  if (!(await guardCurrentForm('创建草稿'))) return
  await message.confirm('将复制查看版本的正文和附件，是否创建修订草稿？')
  const intent = stableCommandIntent('requirement-copy', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.copy(payload.revision, intent.key, payload.execution)
    intent.clear()
    message.success('修订草稿已创建，原完成版本保持不变')
    await load()
  } catch (error) {
    commandError.value = commandErrorText(error)
  } finally {
    commandLoading.value = false
  }
}
const viewHistorical = async (revisionId: EntityId) => {
  await selectVersion(revisionId)
}
const openCompare = (revisionId: EntityId, targetRevisionId: EntityId) => {
  detail.value && compareRef.value?.open(detail.value.revision.ref.entity.entityId, revisionId, targetRevisionId)
}

const reloadSelectedDetail = async () => {
  const revisionId = selectedRevisionId.value, projectId = props.project.id, sequence = loadSequence
  if (!revisionId || !projectId) throw new Error('没有选中的需求分析版本')
  // Keep the saving form mounted, and publish the overview/detail together only
  // after the fresh workspace read succeeds. A failed read must retain the edit.
  const current = await RequirementAnalysisApi.workspace(projectId, props.stageExecution?.stageId, props.taskExecution?.taskId)
  if (sequence !== loadSequence) throw new Error('需求分析工作区已切换，请重新查询')
  const selected = await readWorkspaceDetail(current, revisionId, projectId)
  if (sequence !== loadSequence || !selected) throw new Error('需求分析工作区已切换，请重新查询')
  overview.value = current
  detail.value = selected
  return selected
}
const requestLeave = async () => {
  if (commandLoading.value || detailLoading.value || dynamicFormRef.value?.isSaving()) {
    message.warning('操作进行中，请等待结果后再切换。')
    return false
  }
  // Dirty host exits stay conservative; authorization updates never discard unsaved input.
  if (formDirty.value) {
    message.warning('需求分析尚有未保存内容，请先保存，或在面板中刷新并确认放弃，再切换视图。')
    return false
  }
  return true
}
watch(formDirty, (value) => emit('dirty-change', value), { immediate: true })
// Refresh execution metadata only while idle; an edited form keeps the execution it was opened under.
watch([() => props.taskExecution, () => props.stageExecution, formDirty, commandLoading, loading, detailLoading], () => {
  if (!formDirty.value && !commandLoading.value && !loading.value && !detailLoading.value && !dynamicFormRef.value?.isSaving())
    loadedExecution.value = selectedExecution()
}, { deep: true })
// START/rework can grant creation while this retained Owner panel still has its pre-start overview.
// Refresh only action metadata, never the selected version or an unsaved form.
// Vue watcher cleanup: https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup
const missingCreationActions = computed(() => overview.value && !overview.value.draft && !props.readonly
  ? (props.allowedActions || []).filter((action) =>
      (action === 'CREATE_INITIAL_DRAFT' || action === 'CREATE_DRAFT') && !overview.value!.allowedActions.includes(action)
    ).join('|')
  : '')
watch([() => props.project.id, missingCreationActions], async ([projectId, missing], _previous, onCleanup) => {
  if (!projectId || !missing || !overview.value) return
  const previous = overview.value
  let cancelled = false
  onCleanup(() => { cancelled = true })
  try {
    const current = await RequirementAnalysisApi.workspace(projectId, props.stageExecution?.stageId, props.taskExecution?.taskId)
    if (!cancelled && overview.value === previous) {
      const selected = [current.draft, current.currentEffective].find(value => value &&
        String(value.revision.ref.revisionId) === String(detail.value?.revision.ref.revisionId))
      if (selected && detail.value) detail.value = { ...detail.value, allowedActions: selected.allowedActions }
      overview.value = { ...previous, allowedActions: current.allowedActions }
    }
  } catch {
    if (!cancelled) errorText.value = '业务操作状态刷新失败，请点击查询重试；当前正文未改变。'
  }
})
watch(() => overview.value, (value, previous) => {
  if (previous && value) emit('changed')
})
defineExpose({ requestLeave, isDirty: () => formDirty.value })
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!formDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

// A new round on the same node must not discard an in-progress Owner form.
watch([() => props.project.id, () => props.stageExecution?.stageId, () => props.taskExecution?.taskId, () => props.revisionId], load, { immediate: true })
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => {
  ++loadSequence
  window.removeEventListener('beforeunload', beforeUnload)
})
onBeforeRouteLeave(async () => props.allowedActions === undefined
  ? await guardCurrentForm('离开当前页面')
  : await requestLeave())
</script>

<style scoped lang="scss">
.requirement-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.requirement-toolbar strong { font-size: 16px; color: var(--el-text-color-primary); }
.requirement-caption { margin: 8px 0 0; font-size: 13px; color: var(--el-text-color-secondary); }
.requirement-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.requirement-actions .el-button { margin-left: 0; }

.revision-options { display: flex; flex-wrap: wrap; gap: 12px; }
.revision-option { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 10px 14px; text-align: left; color: var(--el-text-color-primary); background: var(--el-bg-color); border: 1px solid var(--el-border-color); border-radius: 4px; cursor: pointer; }
.revision-option.is-selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.revision-option:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 3px; }
.revision-option span, .revision-option small, .analysis-heading p { color: var(--el-text-color-secondary); }
.analysis-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 24px; }
.analysis-heading h3 { margin: 0 0 8px; font-size: 16px; }
.analysis-heading p { margin: 0; line-height: 1.6; }

@media (width <= 1023px) {
  :deep(.el-descriptions__body) { overflow-x: auto; }
}
@media (width <= 767px) {
  :deep(.el-form--inline .el-form-item) { display: block; margin-right: 0; }
}
</style>
