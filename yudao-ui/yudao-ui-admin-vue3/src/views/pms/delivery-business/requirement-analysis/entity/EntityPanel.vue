<template>
  <div class="min-w-0 w-full max-w-full">
    <ContentWrap>
      <el-form inline class="-mb-15px">
        <el-form-item label="项目">
          <el-input :model-value="project.projectName || String(project.id || '')" disabled class="!w-220px" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" @click="refreshWorkspace"><Icon icon="ep:search" />查询</el-button>
          <el-button v-if="canCreateInitial" :loading="commandLoading" type="primary" @click="createInitial"><Icon icon="ep:plus" />创建需求分析草稿</el-button>
          <el-button v-if="canRevise" :loading="commandLoading" type="primary" @click="createRevision">从查看版本创建草稿</el-button>
          <el-button :disabled="!detail" @click="openHistory">修订记录</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>
    <ContentWrap>
      <el-skeleton v-if="loading && !overview" :rows="7" animated />
      <el-alert v-else-if="errorText" :title="errorText" type="error" show-icon :closable="false">
        <template #default><el-button link type="primary" @click="load">重新加载</el-button></template>
      </el-alert>
      <template v-else-if="overview">
        <el-table :data="currentVersions" data-testid="requirement-version-table" :row-key="row => String(row.revision.ref.revisionId)" empty-text="当前项目尚未创建需求分析">
          <el-table-column prop="revision.revisionNo" label="业务版本" width="110" :formatter="(_row, _column, value) => 'V' + value" />
          <el-table-column label="版本类型" min-width="160" :formatter="row => row.revision.state === 'DRAFT' ? '当前草稿' : '当前有效完成版'" />
          <el-table-column prop="revision.state" label="状态" width="100" :formatter="row => statusLabel(row.revision.state)" />
          <el-table-column prop="revision.frozenAt" label="完成时间" width="170" :formatter="row => formatDateTime(row.revision.frozenAt)" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }"><el-button link type="primary" @click="selectVersion(row.revision.ref.revisionId)">{{ row.revision.state === 'DRAFT' ? '编辑' : '查看' }}</el-button></template>
          </el-table-column>
        </el-table>
        <el-alert v-if="commandError" :title="commandError" type="warning" show-icon closable @close="commandError = ''" />
      </template>
    </ContentWrap>
    <ContentWrap v-if="overview">
      <el-empty v-if="!selectedRevisionId" description="创建草稿后可填写11项核心内容及项目模板扩展项" />
      <el-skeleton v-else-if="detailLoading" :rows="8" animated />
      <template v-else-if="detail">
        <el-descriptions :column="descriptionColumns" border aria-label="当前查看版本" class="mb-15px">
          <el-descriptions-item label="业务版本">V{{ detail.revision.revisionNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.revision.state) }}</el-descriptions-item>
          <el-descriptions-item label="版本关系">{{ relationLabel }}</el-descriptions-item>

          <el-descriptions-item label="模板修订">R{{ detail.form?.revisionNo }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(detail.revision.frozenAt) }}</el-descriptions-item>
        </el-descriptions>
        <EntityForm
          ref="dynamicFormRef"
          :key="`${detail.revision.ref.revisionId}-${detail.extensionValueVersion}`"
          :detail="detail" :allowed-actions="detailActions" :reload="reloadSelectedDetail" :execution="loadedExecution"
          @dirty-change="formDirty = $event" @saved="emit('changed')"
        />
        <el-button v-if="canComplete" :loading="commandLoading" type="success" class="mt-15px" @click="complete">完成并冻结当前草稿</el-button>
      </template>
    </ContentWrap>
  </div>
  <RevisionDrawer ref="historyRef" @view="viewHistorical" @compare="openCompare" />
  <CompareDrawer ref="compareRef" />
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import { useWindowSize } from '@vueuse/core'
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
import RevisionDrawer from './RevisionDrawer.vue'
import CompareDrawer from './CompareDrawer.vue'
import { stableCommandIntent } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'

// PM-03: optional host restrictions narrow, never replace, the SOL Owner permissions.
const props = defineProps<{ project: ProjectMasterVO; revisionId?: BusinessViewId; stageExecution?: StageExecutionContext; taskExecution?: TaskExecutionContext; allowedActions?: string[]; readonly?: boolean }>()
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
const { width } = useWindowSize()
const descriptionColumns = computed(() => width.value <= 767 ? 1 : width.value <= 1023 ? 2 : 3)
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
@media (width <= 1023px) {
  :deep(.el-descriptions__body) { overflow-x: auto; }
}
@media (width <= 767px) {
  :deep(.el-form--inline .el-form-item) { display: block; margin-right: 0; }
}
</style>
