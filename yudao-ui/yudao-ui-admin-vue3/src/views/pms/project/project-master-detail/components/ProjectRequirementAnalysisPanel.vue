<template>
  <ContentWrap :class="`requirement-analysis--${responsiveMode}`">
    <div class="panel-heading">
      <div>
        <h3>需求分析</h3>
        <p>在线填写、完成版本冻结与历史对比</p>
      </div>
      <div class="heading-actions">
        <el-button
          :disabled="!overview?.currentEffective"
          @click="openHistory"
        >
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
              {{ blockerSectionName(blocker.sectionCode) }}：{{ blockerLabel(blocker.code) }}
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
            ><span>内容版本</span><strong>{{ detail.contentVersion }}</strong></div
          >
          <div>
            <span>版本关系</span>
            <strong>{{ relationLabel }}</strong>
          </div>
          <div
            ><span>模板修订</span><strong>#{{ detail.templateRevisionId }}</strong></div
          >
          <div>
            <span>完成时间</span><strong>{{ formatDateTime(detail.completedAt) }}</strong>
          </div>
        </div>

        <div class="workspace">
          <nav class="section-navigation" aria-label="需求分析章节">
            <button
              v-for="section in detail.sections"
              :key="section.sectionId"
              type="button"
              :class="{ 'section-link--active': selectedSectionId === section.sectionId }"
              @click="switchSection(section.sectionId)"
            >
              <span>{{ section.sectionName }}</span>
              <small>
                {{ section.sectionKind === 'CORE' ? '核心' : '扩展' }}
                <template v-if="section.required"> · 必填</template>
              </small>
              <small :class="`attachment-status--${section.attachmentSyncStatus.toLowerCase()}`">
                {{ attachmentStatusLabel(section.attachmentSyncStatus) }}
              </small>
            </button>
          </nav>
          <main class="section-canvas">
            <RequirementAnalysisSectionCard
              v-if="selectedSection"
              ref="sectionCardRef"
              :key="`${detail.preparationId}-${selectedSection.sectionId}-${selectedSection.version}`"
              :preparation-id="detail.preparationId"
              :preparation-version="detail.version"
              :content-version="detail.contentVersion"
              :project-version="project.version || 0"
              :section="selectedSection"
              :reload="load"
              @edit-state-change="sectionEditState = $event"
            />
            <el-empty v-else description="当前版本没有可显示章节" />
          </main>
        </div>
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
  RequirementAnalysisAttachmentSyncStatus,
  RequirementAnalysisCompletionBlockerCode,
  RequirementAnalysisCompletionBlockerVO,
  RequirementAnalysisDetailVO,
  RequirementAnalysisOverviewVO
} from '@/api/pms/engineering/requirement-analysis'
import { useMessage } from '@/hooks/web/useMessage'
import RequirementAnalysisSectionCard from './RequirementAnalysisSectionCard.vue'
import RequirementAnalysisHistoryDrawer from './RequirementAnalysisHistoryDrawer.vue'
import RequirementAnalysisCompareDrawer from './RequirementAnalysisCompareDrawer.vue'
import {
  createRequirementIntentStore,
  requirementAnalysisLayout,
  requirementAnalysisTransitionDecision,
  requirementIntentOf
} from './requirementAnalysisInteraction'
import type { RequirementAnalysisSectionEditState } from './requirementAnalysisInteraction'

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
const selectedSectionId = ref<number>()
const historyRef = ref<InstanceType<typeof RequirementAnalysisHistoryDrawer>>()
const compareRef = ref<InstanceType<typeof RequirementAnalysisCompareDrawer>>()
const sectionCardRef = ref<{
  save: () => Promise<boolean>
  discardBodyChanges: () => void
}>()
const sectionEditState = ref<RequirementAnalysisSectionEditState>()
const intentKeys = createRequirementIntentStore()

const selectedSection = computed(() =>
  detail.value?.sections.find((section) => section.sectionId === selectedSectionId.value)
)
const hasAction = (actions: string[], candidates: string[]) =>
  candidates.some((action) => actions.includes(action))
const effectiveEditState = computed<RequirementAnalysisSectionEditState>(() => {
  const editState = sectionEditState.value
  if (editState && editState.sectionId === selectedSection.value?.sectionId) {
    return editState
  }
  const actions = selectedSection.value?.allowedActions || []
  const editable = actions.includes('EDIT')
  return {
    sectionId: selectedSection.value?.sectionId || 0,
    bodyDirty: false,
    attachmentSyncStatus: selectedSection.value?.attachmentSyncStatus || 'IN_SYNC',
    guardsNavigation:
      editable && selectedSection.value?.attachmentSyncStatus !== 'IN_SYNC'
  }
})
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
const attachmentStatusLabel = (status: RequirementAnalysisAttachmentSyncStatus) =>
  ({ IN_SYNC: '附件已同步', PENDING: '附件待提交', UNKNOWN: '附件事实未知' })[status]
const blockerLabel = (code: RequirementAnalysisCompletionBlockerCode) =>
  ({
    REQUIRED_VALUE_MISSING: '必填内容未填写',
    VALUE_INVALID: '内容不符合冻结模板约束',
    ATTACHMENT_SET_PENDING: '附件当前集合尚未保存',
    ATTACHMENT_FACT_INVALID: '已保存附件事实已失效',
    FACT_PROVIDER_UNAVAILABLE: '附件事实暂不可确认'
  })[code]
const blockerSectionName = (sectionCode: string) =>
  detail.value?.sections.find((section) => section.sectionCode === sectionCode)?.sectionName ||
  sectionCode
const blockerKey = (blocker: RequirementAnalysisCompletionBlockerVO) =>
  `${blocker.sectionCode}:${blocker.code}`

const loadDetail = async (preparationId: number) => {
  detailLoading.value = true
  try {
    detail.value = await RequirementAnalysisApi.getDetail(preparationId)
    sectionEditState.value = undefined
    selectedPreparationId.value = preparationId
    const stillVisible = detail.value.sections.some(
      (section) => section.sectionId === selectedSectionId.value
    )
    if (!stillVisible) selectedSectionId.value = detail.value.sections[0]?.sectionId
  } finally {
    detailLoading.value = false
  }
}
const guardCurrentSection = async (target: string) => {
  const decision = requirementAnalysisTransitionDecision(effectiveEditState.value)
  if (decision === 'ALLOW') return true
  if (decision === 'BLOCK_UNKNOWN_ATTACHMENT_FACTS') {
    message.warning(`附件事实暂不可确认，不能${target}；请刷新后重试`)
    return false
  }
  if (decision === 'SAVE_ATTACHMENT_SET') {
    try {
      await message.confirm(`当前章节附件待提交，必须先保存完整附件集合，才能${target}。是否保存？`)
    } catch {
      return false
    }
    try {
      return (await sectionCardRef.value?.save()) === true
    } catch {
      return false
    }
  }
  try {
    await message.confirm(`当前章节正文尚未保存，是否放弃这些本地修改并${target}？`)
    sectionCardRef.value?.discardBodyChanges()
    return true
  } catch {
    return false
  }
}
const selectVersion = async (preparationId: number) => {
  if (preparationId === selectedPreparationId.value) return
  if (!(await guardCurrentSection('切换版本'))) return
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
      selectedSectionId.value = undefined
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
  if (
    effectiveEditState.value.attachmentSyncStatus === 'UNKNOWN' &&
    !effectiveEditState.value.bodyDirty
  ) {
    await load()
    return
  }
  if (!(await guardCurrentSection('刷新'))) return
  await load()
}
const switchSection = async (sectionId: number) => {
  if (sectionId === selectedSectionId.value) return
  if (!(await guardCurrentSection('切换章节'))) return
  selectedSectionId.value = sectionId
  sectionEditState.value = undefined
}
const openHistory = async () => {
  if (!props.project.id || !(await guardCurrentSection('查看完成历史'))) return
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
  if (!(await guardCurrentSection('完成草稿'))) return
  await message.confirm('完成后正文与附件将永久冻结，是否继续？')
  const payload = {
    preparationId: detail.value.preparationId,
    preparationVersion: detail.value.version,
    contentVersion: detail.value.contentVersion,
    projectVersion: props.project.version || 0
  }
  const intent = requirementIntentOf('COMPLETE', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.completeDraft(
      payload.preparationId,
      payload.preparationVersion,
      payload.contentVersion,
      payload.projectVersion,
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
    preparationVersion: detail.value.version,
    contentVersion: detail.value.contentVersion,
    projectVersion: props.project.version || 0
  }
  const intent = requirementIntentOf('CREATE_REVISION', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.createNextDraft(
      payload.preparationId,
      payload.preparationVersion,
      payload.contentVersion,
      payload.projectVersion,
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

watch(() => props.project.id, load, { immediate: true })
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
