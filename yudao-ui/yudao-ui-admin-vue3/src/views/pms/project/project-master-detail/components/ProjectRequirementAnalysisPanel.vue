<template>
  <ContentWrap :class="`requirement-analysis--${responsiveMode}`">
    <div class="panel-heading">
      <div>
        <h3>需求分析</h3>
        <p>在线填写、完成版本冻结与历史对比</p>
      </div>
      <div class="heading-actions">
        <el-button :disabled="!overview?.currentEffective" @click="openHistory">
          完成历史
        </el-button>
        <el-button :loading="loading" @click="refreshWorkspace">刷新</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !overview" :rows="7" animated />
    <el-alert v-else-if="errorText" :title="errorText" type="error" show-icon :closable="false">
      <template #default>
        <el-button link type="primary" @click="load">重新加载</el-button>
      </template>
    </el-alert>
    <template v-else-if="overview">
      <div class="version-strip">
        <button
          v-if="overview.draft"
          class="version-choice"
          :class="{
            'version-choice--active': selectedPreparationId === overview.draft.preparationId
          }"
          type="button"
          @click="selectVersion(overview.draft.preparationId)"
        >
          <span>当前草稿</span>
          <strong>V{{ overview.draft.businessVersion }}</strong>
          <small>内容版本 {{ overview.draft.contentVersion }}</small>
        </button>
        <button
          v-if="overview.currentEffective"
          class="version-choice"
          :class="{
            'version-choice--active':
              selectedPreparationId === overview.currentEffective.preparationId
          }"
          type="button"
          @click="selectVersion(overview.currentEffective.preparationId)"
        >
          <span>当前有效完成版</span>
          <strong>V{{ overview.currentEffective.businessVersion }}</strong>
          <small>内容版本 {{ overview.currentEffective.contentVersion }}</small>
        </button>
        <div v-if="!overview.draft && !overview.currentEffective" class="no-version">
          当前项目尚未创建需求分析。
        </div>
      </div>

      <div class="primary-actions">
        <el-button
          v-if="canCreateInitial"
          :loading="commandLoading"
          type="primary"
          @click="createInitial"
        >
          创建需求分析草稿
        </el-button>
        <el-button v-if="canComplete" :loading="commandLoading" type="success" @click="complete">
          完成并冻结当前草稿
        </el-button>
        <el-button
          v-if="canRevise"
          :loading="commandLoading"
          type="primary"
          @click="createRevision"
        >
          从当前有效版创建修订草稿
        </el-button>
      </div>
      <el-alert
        v-if="commandError"
        :title="commandError"
        type="warning"
        show-icon
        closable
        @close="commandError = ''"
      />

      <el-empty
        v-if="!selectedPreparationId"
        description="创建草稿后可填写11项核心内容及项目模板扩展项"
      />
      <el-skeleton v-else-if="detailLoading" :rows="8" animated />
      <template v-else-if="detail">
        <el-alert
          v-if="detail.status === 'COMPLETED'"
          title="该完成版本正文和附件已冻结，只能查看或对比。"
          type="info"
          :closable="false"
          show-icon
        />
        <section
          v-if="detail.completionBlockers.length"
          class="completion-blockers"
          aria-label="当前完成阻断"
        >
          <strong>当前尚不能完成</strong>
          <ul>
            <li v-for="blocker in detail.completionBlockers" :key="blockerKey(blocker)">
              {{ blocker.fieldKey || '表单' }}：{{ blocker.message || blockerLabel(blocker.code) }}
            </li>
          </ul>
        </section>
        <div class="version-meta" aria-label="当前查看版本">
          <div
            ><span>业务版本</span><strong>V{{ detail.businessVersion }}</strong></div
          >
          <div
            ><span>状态</span><strong>{{ statusLabel(detail.status) }}</strong></div
          >
          <div
            ><span>表单实例版本</span><strong>{{ detail.dynamicFormInstanceVersion }}</strong></div
          >
          <div>
            <span>版本关系</span>
            <strong>{{ relationLabel }}</strong>
          </div>
          <div
            ><span>模板修订</span><strong>R{{ detail.dynamicFormRevisionNo }}</strong></div
          >
          <div>
            <span>完成时间</span><strong>{{ formatDateTime(detail.completedAt) }}</strong>
          </div>
        </div>

        <RequirementAnalysisDynamicForm
          ref="dynamicFormRef"
          :key="`${detail.preparationId}-${detail.dynamicFormInstanceVersion}`"
          :detail="detail"
          :reload="reloadSelectedDetail"
          @dirty-change="formDirty = $event"
        />
      </template>
    </template>
  </ContentWrap>

  <RequirementAnalysisHistoryDrawer
    ref="historyRef"
    @view="viewHistorical"
    @compare="openCompare"
  />
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
  requirementAnalysisLayout,
  requirementIntentOf
} from './requirementAnalysisInteraction'

const props = defineProps<{ project: ProjectMasterVO }>()
const { width } = useWindowSize()
const responsiveMode = computed(() => requirementAnalysisLayout(width.value))
const message = useMessage()
const loading = ref(false)
const detailLoading = ref(false)
const commandLoading = ref(false)
const errorText = ref('')
const commandError = ref('')
const overview = ref<RequirementAnalysisOverviewVO>()
const detail = ref<RequirementAnalysisDetailVO>()
const selectedPreparationId = ref<number>()
const historyRef = ref<InstanceType<typeof RequirementAnalysisHistoryDrawer>>()
const compareRef = ref<InstanceType<typeof RequirementAnalysisCompareDrawer>>()
const dynamicFormRef = ref<{
  save: () => Promise<boolean>
  discardChanges: () => void
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
const overviewActions = computed(() => overview.value?.allowedActions || [])
const detailActions = computed(() => detail.value?.allowedActions || [])
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
  if (!formDirty.value) return true
  try {
    await message.confirm(`当前表单尚未保存，是否放弃这些本地修改并${target}？`)
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
  if (!selectedPreparationId.value) throw new Error('没有选中的需求分析版本')
  return await loadDetail(selectedPreparationId.value)
}
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!formDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(() => props.project.id, load, { immediate: true })
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
onBeforeRouteLeave(async () => await guardCurrentForm('离开当前页面'))
</script>

<style scoped lang="scss">
.panel-heading,
.heading-actions,
.primary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-heading {
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-heading h3,
.panel-heading p {
  margin: 0;
}

.panel-heading p,
.version-choice span,
.version-choice small,
.version-meta span,
.section-navigation small {
  color: var(--el-text-color-secondary);
}

.panel-heading p {
  margin-top: 4px;
}

.version-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.version-choice {
  display: grid;
  padding: 12px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  gap: 4px;
}

.version-choice--active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.no-version {
  padding: 20px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color-lighter);
  grid-column: 1 / -1;
}

.primary-actions {
  justify-content: flex-end;
  min-height: 32px;
  margin: 12px 0;
}

.version-meta {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0;
}

.completion-blockers {
  padding: 12px;
  margin-top: 12px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--el-border-radius-base);
}

.completion-blockers ul {
  padding-left: 20px;
  margin: 6px 0 0;
}

.version-meta > div {
  min-width: 0;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.version-meta span,
.version-meta strong {
  display: block;
  overflow-wrap: anywhere;
}

.version-meta strong {
  margin-top: 4px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(0, 1fr);
  gap: 12px;
}

.section-navigation {
  display: grid;
  align-content: start;
  gap: 4px;
}

.section-navigation button {
  display: grid;
  padding: 9px 10px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-blank);
  border: 1px solid transparent;
  border-radius: var(--el-border-radius-base);
  gap: 3px;
}

.section-navigation button:hover,
.section-navigation .section-link--active {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
}

.section-navigation .section-link--active {
  color: var(--el-color-primary);
}

.attachment-status--in_sync {
  color: var(--el-color-success);
}

.attachment-status--pending {
  color: var(--el-color-warning);
}

.attachment-status--unknown {
  color: var(--el-color-danger);
}

.section-canvas {
  min-width: 0;
}

@media (width <= 1023px) {
  .version-meta {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .section-navigation {
    display: flex;
    padding-bottom: 6px;
    overflow-x: auto;
  }

  .section-navigation button {
    min-width: 150px;
  }
}

@media (width <= 767px) {
  .panel-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .heading-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .primary-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .version-strip,
  .version-meta {
    grid-template-columns: 1fr;
  }

  .primary-actions :deep(.el-button) {
    width: 100%;
    margin: 0;
  }

  .version-meta {
    gap: 5px;
  }
}
</style>
