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
          <el-button v-if="canRevise" :loading="commandLoading" type="primary" @click="createRevision">从当前有效版创建修订草稿</el-button>
          <el-button :disabled="!overview?.currentEffective" @click="openHistory">完成历史</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>
    <ContentWrap>
      <el-skeleton v-if="loading && !overview" :rows="7" animated />
      <el-alert v-else-if="errorText" :title="errorText" type="error" show-icon :closable="false">
        <template #default><el-button link type="primary" @click="load">重新加载</el-button></template>
      </el-alert>
      <template v-else-if="overview">
        <el-table :data="currentVersions" data-testid="requirement-version-table" row-key="preparationId" empty-text="当前项目尚未创建需求分析">
          <el-table-column prop="businessVersion" label="业务版本" width="110" :formatter="(_row, _column, value) => 'V' + value" />
          <el-table-column label="版本类型" min-width="160" :formatter="row => row.currentDraft ? '当前草稿' : '当前有效完成版'" />
          <el-table-column prop="contentVersion" label="内容版本" width="110" />
          <el-table-column prop="status" label="状态" width="100" :formatter="row => statusLabel(row.status)" />
          <el-table-column prop="completedAt" label="完成时间" width="170" :formatter="row => formatDateTime(row.completedAt)" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }"><el-button link type="primary" @click="selectVersion(row.preparationId)">{{ row.currentDraft ? '编辑' : '查看' }}</el-button></template>
          </el-table-column>
        </el-table>
        <el-alert v-if="commandError" :title="commandError" type="warning" show-icon closable @close="commandError = ''" />
      </template>
    </ContentWrap>
    <ContentWrap v-if="overview">
      <el-empty v-if="!selectedPreparationId" description="创建草稿后可填写11项核心内容及项目模板扩展项" />
      <el-skeleton v-else-if="detailLoading" :rows="8" animated />
      <template v-else-if="detail">
        <el-descriptions :column="descriptionColumns" border aria-label="当前查看版本" class="mb-15px">
          <el-descriptions-item label="业务版本">V{{ detail.businessVersion }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="版本关系">{{ relationLabel }}</el-descriptions-item>
          <el-descriptions-item label="内容版本">{{ detail.contentVersion }}</el-descriptions-item>
          <el-descriptions-item label="模板修订">R{{ detail.dynamicFormRevisionNo }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(detail.completedAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="detail.completionBlockers.length" type="warning" title="当前尚不能完成" :closable="false" class="mb-15px">
          <ul><li v-for="blocker in detail.completionBlockers" :key="blockerKey(blocker)">{{ blocker.fieldKey || '表单' }}：{{ blocker.message || blockerLabel(blocker.code) }}</li></ul>
        </el-alert>
        <RequirementAnalysisDynamicForm
          ref="dynamicFormRef"
          :key="`${detail.preparationId}-${detail.dynamicFormInstanceVersion}`"
          :detail="detail" :allowed-actions="detailActions" :reload="reloadSelectedDetail"
          @dirty-change="formDirty = $event" @saved="emit('changed')"
        />
        <el-button v-if="canComplete" :loading="commandLoading" type="success" class="mt-15px" @click="complete">完成并冻结当前草稿</el-button>
      </template>
    </ContentWrap>
  </div>
  <RequirementAnalysisHistoryDrawer ref="historyRef" @view="viewHistorical" @compare="openCompare" />
  <RequirementAnalysisCompareDrawer ref="compareRef" />
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import { useWindowSize } from '@vueuse/core'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import type {
  RequirementAnalysisCompletionBlockerCode,
  RequirementAnalysisCompletionBlockerVO,
  RequirementAnalysisDetailVO,
  RequirementAnalysisOverviewVO
} from '@/api/pms/engineering/requirement-analysis'
import { useMessage } from '@/hooks/web/useMessage'
import { onBeforeRouteLeave } from 'vue-router'
import RequirementAnalysisDynamicForm from './RequirementAnalysisDynamicForm.vue'
import RequirementAnalysisHistoryDrawer from './RequirementAnalysisHistoryDrawer.vue'
import RequirementAnalysisCompareDrawer from './RequirementAnalysisCompareDrawer.vue'
import {
  createRequirementIntentStore,
  requirementIntentOf
} from './requirementAnalysisInteraction'

// PM-03: optional host restrictions narrow, never replace, the SOL Owner permissions.
const props = defineProps<{ project: ProjectMasterVO; allowedActions?: string[]; readonly?: boolean }>()
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
const overview = ref<RequirementAnalysisOverviewVO>()
const currentVersions = computed(() => [overview.value?.draft, overview.value?.currentEffective].filter((item) => !!item))
const detail = ref<RequirementAnalysisDetailVO>()
const selectedPreparationId = ref<number>()
const historyRef = ref<InstanceType<typeof RequirementAnalysisHistoryDrawer>>()
const compareRef = ref<InstanceType<typeof RequirementAnalysisCompareDrawer>>()
const dynamicFormRef = ref<{
  save: () => Promise<boolean>
  discardChanges: () => void
  isSaving: () => boolean
}>()
const formDirty = ref(false)
const intentKeys = createRequirementIntentStore()

const hasAction = (actions: string[], candidates: string[]) =>
  candidates.some((action) => actions.includes(action))
const relationLabel = computed(() => {
  if (detail.value?.currentDraft) return '当前草稿'
  if (detail.value?.currentEffective) return '当前有效'
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
    detail.value?.currentDraft === true && hasAction(detailActions.value, ['COMPLETE', 'SUBMIT'])
)
const canRevise = computed(
  () =>
    !overview.value?.draft &&
    detail.value?.currentEffective === true &&
    hasAction(overviewActions.value, ['CREATE_DRAFT', 'CREATE_REVISION'])
)

const formatDateTime = (value?: string) => (value ? formatDate(value) : '-')
const statusLabel = (status: string) => ({ DRAFT: '草稿', COMPLETED: '已完成' })[status] || status
const commandErrorText = (error: any) => {
  const code = error?.data?.code || error?.code || error?.message
  return code ? `操作未完成：${String(code)}` : '操作未完成，请刷新权威事实后重试。'
}
const blockerLabel = (code: RequirementAnalysisCompletionBlockerCode) =>
  ({
    REQUIRED_VALUE_MISSING: '必填内容未填写',
    FORM_VALUE_INVALID: '内容不符合冻结模板约束',
    CONTROLLED_FILE_INVALID: '受控文件事实已失效',
    FACT_PROVIDER_UNAVAILABLE: '表单或文件事实暂不可确认'
  })[code]
const blockerKey = (blocker: RequirementAnalysisCompletionBlockerVO) =>
  `${blocker.fieldKey || 'FORM'}:${blocker.code}`

const loadDetail = async (preparationId: number) => {
  detailLoading.value = true
  try {
    detail.value = await RequirementAnalysisApi.getDetail(preparationId)
    formDirty.value = false
    selectedPreparationId.value = preparationId
    return detail.value
  } finally {
    detailLoading.value = false
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
const selectVersion = async (preparationId: number) => {
  if (preparationId === selectedPreparationId.value) return
  if (!(await guardCurrentForm('切换版本'))) return
  errorText.value = ''
  try {
    await loadDetail(preparationId)
  } catch {
    detail.value = undefined
    errorText.value = '需求分析版本已变化或当前主体无权查看，请刷新后重试。'
  }
}
const load = async () => {
  if (!props.project.id) return
  loading.value = true
  errorText.value = ''
  try {
    overview.value = await RequirementAnalysisApi.getCurrent(props.project.id)
    const preferred = overview.value.draft || overview.value.currentEffective
    if (preferred) await loadDetail(preferred.preparationId)
    else {
      detail.value = undefined
      selectedPreparationId.value = undefined
    }
  } catch {
    overview.value = undefined
    detail.value = undefined
    errorText.value = '需求分析工作区加载失败，请检查项目范围或稍后重试。'
  } finally {
    loading.value = false
  }
}
const refreshWorkspace = async () => {
  if (!(await guardCurrentForm('刷新'))) return
  await load()
}
const openHistory = async () => {
  if (!props.project.id || !(await guardCurrentForm('查看完成历史'))) return
  historyRef.value?.open(
    props.project.id,
    selectedPreparationId.value,
    detail.value?.status,
    detail.value?.sourcePreparationId
  )
}

const createInitial = async () => {
  if (!props.project.id) return
  const payload = { projectId: props.project.id, projectVersion: props.project.version || 0 }
  const intent = requirementIntentOf('CREATE_INITIAL_DRAFT', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.createInitialDraft(payload.projectId, intentKeys.key(intent))
    intentKeys.complete(intent)
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
  if (!(await guardCurrentForm('完成草稿'))) return
  await message.confirm('完成后正文与附件将永久冻结，是否继续？')
  const payload = {
    preparationId: detail.value.preparationId,
    instanceVersion: detail.value.dynamicFormInstanceVersion,
    solVersion: detail.value.version
  }
  const intent = requirementIntentOf('COMPLETE', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.completeDraft(
      payload.preparationId,
      payload.instanceVersion,
      payload.solVersion,
      intentKeys.key(intent)
    )
    intentKeys.complete(intent)
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
  await message.confirm('将复制当前有效版本的冻结目录、正文和附件，是否创建修订草稿？')
  const payload = {
    preparationId: detail.value.preparationId,
    instanceVersion: detail.value.dynamicFormInstanceVersion,
    solVersion: detail.value.version
  }
  const intent = requirementIntentOf('CREATE_REVISION', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.createNextDraft(
      payload.preparationId,
      payload.instanceVersion,
      payload.solVersion,
      intentKeys.key(intent)
    )
    intentKeys.complete(intent)
    message.success('修订草稿已创建，原完成版本保持不变')
    await load()
  } catch (error) {
    commandError.value = commandErrorText(error)
  } finally {
    commandLoading.value = false
  }
}
const viewHistorical = async (preparationId: number) => {
  await selectVersion(preparationId)
}
const openCompare = (preparationId: number, targetPreparationId: number) => {
  compareRef.value?.open(preparationId, targetPreparationId)
}

const reloadSelectedDetail = async () => {
  if (!selectedPreparationId.value || !props.project.id) throw new Error('没有选中的需求分析版本')
  // Keep the saving form mounted, and publish the overview/detail together only
  // after both authoritative reads succeed. A failed read must retain the edit.
  const [current, selected] = await Promise.all([
    RequirementAnalysisApi.getCurrent(props.project.id),
    RequirementAnalysisApi.getDetail(selectedPreparationId.value)
  ])
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
watch(() => overview.value, (value, previous) => {
  if (previous && value) emit('changed')
})
defineExpose({ requestLeave, isDirty: () => formDirty.value })
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!formDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(() => props.project.id, load, { immediate: true })
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
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
