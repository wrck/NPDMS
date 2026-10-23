<template>
  <section class="cutover-task-workbench full-detail" data-testid="cutover-task-detail">
    <header class="detail-topbar">
      <div><strong>{{ approvalOnly ? '割接审批' : detail?.task.taskName || '割接任务' }}</strong><span>{{ approvalOnly ? '审批来源与方案只读' : detail?.task.taskNo }}</span></div>
      <el-tag v-if="readonly" type="info" size="small">只读查看</el-tag>
      <el-tag v-if="detail" :type="detail.task.taskStatus === 'ARCHIVED' ? 'success' : 'primary'" size="small">{{ statusLabels[detail.task.taskStatus] }}</el-tag>
    </header>
    <div v-loading="detailLoading" class="detail-body">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon />
      <CutoverApprovalPanel
        ref="approvalPanel" v-if="approvalOnly" :key="`${taskId}:${approvalReload}`" :readonly="readonly" :actions-target="stageActions"
        :task-id="taskId" complete-after-decision @changed="handleApprovalChanged" @decided="emit('close')" />
      <template v-else-if="detail">
        <el-alert
v-if="detail.task.currentStage === 'P2' && detail.assessmentSubmissionBlockReason"
          :title="detail.assessmentSubmissionBlockReason" type="warning" :closable="false" show-icon />
        <CutoverWorkbenchSteps :steps="detail.workbenchSteps" :selected="selectedStage" @select="selectStage" />
        <template v-if="activeStagePanel === 'ASSESSMENT'">
          <el-alert title="根据问卷填写评估信息，人工确定A/B/C/D等级。D级使用简易割接流程；已提交内容需通过修订创建新版本。" type="info" :closable="false" show-icon />
          <CutoverProjectContextCard :detail="detail" :approval-readonly="detail.approvalReadOnly" />
          <CutoverTaskDevices :detail="detail" />
          <CutoverAssessmentPanel
            :actions-target="stageActions"
            :model="assessmentModel" :customer-service-level="assessmentCustomerLevel"
            :editable="!readonly && !detailError && detail.allowedActions.includes('SAVE_ASSESSMENT')"
            :submittable="!readonly && !detailError && (detail.allowedActions.includes('SUBMIT_ASSESSMENT') || (detail.allowedActions.includes('SAVE_ASSESSMENT') && checkPermi(['pms:cutover-task:submit-assessment'])))"
            :saving="saving" :submitting="submitting" @save="saveAssessment" @submit="submitAssessment" />
        </template>
        <CutoverChecklistPanel
          ref="checklistPanel"
          :actions-target="stageActions"
          v-else-if="activeStagePanel === 'CHECKLIST'" :detail="detail" :readonly="readonly || !!detailError"
          :refresh-workspace="refreshAfterChecklistSubmit" :refresh-draft="handleTaskChanged" @navigate="handleChecklistNavigation" />
        <CutoverPlanPanel
          ref="planPanel"
          :actions-target="stageActions"
          v-else-if="activeStagePanel === 'PLAN'" :readonly="readonly || !!detailError" :task-id="detail.task.id"
          :task-version="detail.task.version" :manual-grade="detail.task.manualGrade" @changed="handleTaskChanged" />
        <CutoverApprovalPanel
          ref="approvalPanel" :actions-target="stageActions"
          v-else-if="activeStagePanel === 'APPROVAL'" :readonly="readonly || !!detailError" :task-id="detail.task.id"
          @changed="handleApprovalChanged" @decided="emit('close')" />
        <CutoverClosurePanel
          :actions-target="stageActions"
          v-else-if="activeStagePanel === 'CLOSURE'" :readonly="readonly || !!detailError" :task-id="detail.task.id"
          :devices="detail.devices" @changed="handleTaskChanged" />
        <el-empty v-else description="当前步骤暂无可查看的内容" />
      </template>
    </div>
    <footer class="cut-actions cut-actions--sticky detail-actions" aria-label="割接任务操作" data-testid="cutover-detail-actions">
      <div class="detail-navigation-actions">
        <el-button data-testid="close-cutover-detail" @click="closeDetail"><Icon icon="ep:back" />返回</el-button>
        <el-button data-testid="refresh-cutover-detail" :loading="detailLoading" @click="reloadDetail">刷新</el-button>
      </div>
      <el-button v-if="revisionTarget && !readonly && !detailError" data-testid="revise-current-step" type="warning" :loading="revising" @click="openRevision">
        {{ detail?.task.currentStage === 'P5' ? '撤回审批并修订此步骤' : '修订此步骤' }}
      </el-button>
      <div ref="stageActions" class="detail-stage-actions"></div>
    </footer>
    <el-dialog
      v-model="revisionVisible" title="创建修订" width="min(560px, 94vw)" append-to-body align-center
      :close-on-click-modal="!revising" :show-close="!revising">
      <el-alert :title="revisionImpact" type="warning" :closable="false" show-icon />
      <el-form label-position="top" class="mt-4">
        <el-form-item label="本次使用的配置">
          <el-select
            v-model="revisionConfigurationId" :loading="revisionOptionsLoading" :disabled="revising"
            class="w-full" data-testid="revision-configuration">
            <el-option label="沿用当前配置" value="" />
            <el-option
              v-for="option in revisionOptions" :key="String(option.revisionId)" :value="String(option.revisionId)"
              :label="`${option.name} · 版本 ${option.revisionNo}`" />
          </el-select>
          <span class="cut-help">选择新配置后需重新匹配；未变化的填报和材料可沿用，原版本保留在历史记录中。</span>
        </el-form-item>
        <el-form-item label="修订原因" required>
          <el-input
            v-model="revisionReason" type="textarea" :rows="3" maxlength="1000" show-word-limit
            :disabled="revising" data-testid="revision-reason" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="revising" @click="revisionVisible = false">取消</el-button>
        <el-button
          type="primary" :loading="revising" :disabled="!revisionReason.trim() || revisionOptionsLoading"
          data-testid="confirm-step-revision" @click="reviseStep">
          {{ detail?.task.currentStage === 'P5' ? '撤回审批并创建修订' : '创建修订' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import '../shared/cutover.css'
import { checkPermi } from '@/utils/permission'
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverApi from '@/api/pms/cutover'
import type { AssessmentAnswers, CutoverTaskDetail, ManualGrade, WireLong } from '@/api/pms/cutover'
import { activeCutoverStagePanel, createCutoverWriteCoordinator } from '../shared/cutoverTaskInteraction'
import CutoverProjectContextCard from '../components/CutoverProjectContextCard.vue'
import CutoverTaskDevices from '../components/CutoverTaskDevices.vue'
import CutoverAssessmentPanel from '../components/CutoverAssessmentPanel.vue'
import CutoverWorkbenchSteps from '../components/CutoverWorkbenchSteps.vue'

const CutoverChecklistPanel = defineAsyncComponent(() => import('../components/CutoverChecklistPanel.vue').then(module => module.default))
const CutoverApprovalPanel = defineAsyncComponent(() => import('../components/CutoverApprovalPanel.vue').then(module => module.default))
const CutoverClosurePanel = defineAsyncComponent(() => import('../components/CutoverClosurePanel.vue').then(module => module.default))
const CutoverPlanPanel = defineAsyncComponent(() => import('../components/CutoverPlanPanel.vue').then(module => module.default))

const props = defineProps<{ taskId: WireLong; projectId?: WireLong; readonly?: boolean; approvalOnly?: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const message = useMessage()
const planPanel = ref<InstanceType<typeof CutoverPlanPanel>>()
const checklistPanel = ref<InstanceType<typeof CutoverChecklistPanel>>()
const approvalPanel = ref<InstanceType<typeof CutoverApprovalPanel>>()
const stageActions = shallowRef<HTMLElement>()
const detail = ref<CutoverTaskDetail | null>(null)
const readonly = computed(() => !!props.readonly || !!detail.value?.approvalReadOnly)
const detailLoading = ref(false)
const detailError = ref('')
const selectedStage = ref<CutoverApi.CutoverStage | null>(null)
const approvalReload = ref(0)
const saving = ref(false)
const submitting = ref(false)
const revising = ref(false)
const revisionVisible = ref(false)
const revisionReason = ref('')
const revisionConfigurationId = ref('')
const revisionOptions = ref<CutoverApi.IntakeOption[]>([])
const revisionOptionsLoading = ref(false)
const revisionWrites = createCutoverWriteCoordinator()
const revisionTarget = computed<'P2' | 'P3' | null>(() => {
  if (selectedStage.value === 'P2' && detail.value?.allowedActions.includes('REVISE_ASSESSMENT')) return 'P2'
  if (selectedStage.value === 'P3' && detail.value?.allowedActions.includes('REVISE_CHECKLIST')) return 'P3'
  return null
})
const revisionImpact = computed(() => `${detail.value?.task.currentStage === 'P5' ? '当前审批将先撤回。' : ''}${revisionTarget.value === 'P2'
  ? '后续清单及方案将失效，需要重新确认等级并继续填写。' : '后续方案将失效，需要重新提交清单并编制方案。'}`)
let loadSequence = 0
const assessmentWrites = createCutoverWriteCoordinator()
const assessmentModel = reactive<{ answers: AssessmentAnswers; manualGrade: ManualGrade | null; manualCustomerServiceLevelCode: string | null }>({ answers: emptyAnswers(), manualGrade: null, manualCustomerServiceLevelCode: null })
const assessmentCustomerLevel = computed(() => {
  const saved = detail.value?.assessment?.customerServiceLevel
  return saved && saved.status !== 'UNAVAILABLE' ? saved : detail.value?.customerServiceLevel
})
const activeStagePanel = computed(() => activeCutoverStagePanel(selectedStage.value || detail.value?.task.currentStage || null))
const statusLabels: Record<string, string> = {
  GRADE_CONFIRMING: '人工分级中', SURVEYING: '现场调研', PLAN_DRAFTING: '方案编制',
  APPROVING: '审批中', CLOSURE_IN_PROGRESS: '跟踪与闭环', ARCHIVED: '已归档'
}

function emptyAnswers(): AssessmentAnswers {
  return { businessImportanceLevel: null, operationComplexityLevel: null, hiddenRiskLevel: null, sparePartApplied: null }
}

const refreshDetail = async () => {
  if (props.approvalOnly) { approvalReload.value++; return }
  const sequence = ++loadSequence
  detailLoading.value = true
  detailError.value = ''
  try {
    const loaded = await CutoverApi.getCutoverTaskDetail(props.taskId)
    if (sequence !== loadSequence) return
    if (props.projectId && String(loaded.task.projectId) !== String(props.projectId)) {
      detail.value = null
      throw new Error('割接任务不属于当前项目')
    }
    if (detail.value?.task.currentStage !== loaded.task.currentStage || !selectedStage.value) selectedStage.value = loaded.task.currentStage
    detail.value = loaded
    Object.assign(assessmentModel.answers, loaded.assessment?.answers || emptyAnswers())
    assessmentModel.manualGrade = loaded.assessment?.manualGrade || null
    assessmentModel.manualCustomerServiceLevelCode = loaded.assessment?.manualCustomerServiceLevelCode || null
  } catch (error) {
    if (sequence !== loadSequence) return
    detailError.value = '割接任务详情加载失败，请点击刷新重试。当前无法办理任务。'
    throw error
  } finally {
    if (sequence === loadSequence) detailLoading.value = false
  }
}
const reloadDetail = async () => {
  if (!(await saveBeforeLeave())) return
  try {
    await refreshDetail()
    if (!props.approvalOnly && activeStagePanel.value === 'APPROVAL') await approvalPanel.value?.refresh?.()
  } catch { /* 错误由详情页展示，历史数据不可继续办理。 */ }
}
const refreshKeepingAssessment = async () => {
  const taskId = String(props.taskId)
  const retained = JSON.parse(JSON.stringify(assessmentModel))
  try { await refreshDetail() }
  finally {
    if (taskId === String(props.taskId)) {
      Object.assign(assessmentModel.answers, retained.answers)
      assessmentModel.manualGrade = retained.manualGrade
      assessmentModel.manualCustomerServiceLevelCode = retained.manualCustomerServiceLevelCode
      message.warning('未提交的评估填写已保留；草稿尚未保存成功，请核对后重试。')
    }
  }
}

const openRevision = async () => {
  if (!revisionTarget.value || !detail.value) return
  revisionReason.value = ''
  revisionConfigurationId.value = ''
  revisionOptions.value = []
  revisionVisible.value = true
  revisionOptionsLoading.value = true
  try { revisionOptions.value = await CutoverApi.getCutoverRevisionOptions(detail.value.task.id) }
  catch { revisionVisible.value = false }
  finally { revisionOptionsLoading.value = false }
}
const reviseStep = async () => {
  const targetStage = revisionTarget.value
  if (!targetStage || !detail.value?.project.projectScopeVersion || revising.value) return
  const reason = revisionReason.value.trim()
  if (!reason || reason.length > 1000) { message.warning('请填写 1～1000 字的修订原因'); return }
  const command = { taskId: detail.value.task.id, taskVersion: detail.value.task.version, targetStage,
    data: { expectedProjectScopeVersion: detail.value.project.projectScopeVersion, reason,
      ...(revisionConfigurationId.value ? { configurationRevisionId: revisionConfigurationId.value } : {}) } }
  revising.value = true
  try {
    const result = await revisionWrites.run(JSON.stringify(command), idempotencyKey => CutoverApi.reviseCutoverTask({ ...command, idempotencyKey }), refreshDetail)
    if (!result.refreshSucceeded) { message.warning('修订已完成，详情刷新失败，请先刷新后继续'); return }
    revisionVisible.value = false
    if (result.writeCalled) { message.success('修订已创建，原版本已保留'); emit('changed') }
  } finally { revising.value = false }
}

const persistAssessment = async () => {
  if (!detail.value) return false
  const command = { taskId: detail.value.task.id, taskVersion: detail.value.task.version,
    assessmentVersion: detail.value.assessment?.rowVersion || 0,
    data: { ...JSON.parse(JSON.stringify(assessmentModel)), manualCustomerServiceLevelCode: assessmentCustomerLevel.value?.status === 'NOT_CONFIGURED' ? assessmentModel.manualCustomerServiceLevelCode : null } }
  const result = await assessmentWrites.run(JSON.stringify(['SAVE_ASSESSMENT', command]),
    idempotencyKey => CutoverApi.saveCutoverAssessment({ ...command, idempotencyKey }), refreshDetail,
    { refreshOnFailure: refreshKeepingAssessment })
  if (!result.writeCalled || !result.refreshSucceeded) {
    message.warning(result.refreshSucceeded ? '已刷新上次操作结果，请按最新任务继续' : '评估操作已成功，详情尚未刷新，请先刷新后继续')
    return false
  }
  emit('changed')
  return true
}
const saveAssessment = async () => {
  if (!detail.value) return
  saving.value = true
  try {
    if (await persistAssessment()) message.success('人工评估草稿已保存，任务阶段未推进')
  } finally { saving.value = false }
}
const submitAssessment = async () => {
  if (!detail.value) return
  submitting.value = true
  try {
    const stored = detail.value.assessment
    const currentLevel = assessmentCustomerLevel.value
    if (currentLevel?.status === 'NOT_CONFIGURED' && !assessmentModel.manualCustomerServiceLevelCode) {
      message.warning('请选择客户服务等级并保存评估。'); return
    }
    if (!stored || JSON.stringify(stored.answers) !== JSON.stringify(assessmentModel.answers) || stored.manualGrade !== assessmentModel.manualGrade || (stored.manualCustomerServiceLevelCode || null) !== assessmentModel.manualCustomerServiceLevelCode) {
      if (!(await persistAssessment())) return
    }
    if (!detail.value?.assessment || !detail.value.allowedActions.includes('SUBMIT_ASSESSMENT')) {
      const answers = assessmentModel.answers
      const incomplete = !assessmentModel.manualGrade || !answers.businessImportanceLevel
        || !answers.operationComplexityLevel || !answers.hiddenRiskLevel || answers.sparePartApplied == null
      message.warning(incomplete
        ? '请填写业务重要等级、操作复杂度、隐患风险、备件是否申请，并选择割接等级。'
        : detail.value?.assessmentSubmissionBlockReason || '评估信息已保存，但当前来源信息、实施条件或操作权限不满足提交要求，请刷新任务并核查。')
      return
    }
    const command = { taskId: detail.value.task.id, taskVersion: detail.value.task.version, assessmentVersion: detail.value.assessment.rowVersion }
    const result = await assessmentWrites.run(JSON.stringify(['SUBMIT_ASSESSMENT', command]),
      idempotencyKey => CutoverApi.submitCutoverAssessment({ ...command, idempotencyKey }), refreshDetail,
      { refreshOnFailure: refreshKeepingAssessment })
    if (!result.writeCalled || !result.refreshSucceeded) {
      message.warning(result.refreshSucceeded ? '已刷新上次操作结果，请按最新任务继续' : '评估提交已成功，详情尚未刷新，请先刷新后继续')
      return
    }
    message.success(assessmentModel.manualGrade === 'D' ? '已进入割接方案' : '已进入调研及风险考察')
    emit('changed')
  } finally { submitting.value = false }
}
const refreshAfterChecklistSubmit = async () => {
  await refreshDetail()
  emit('changed')
  if (detail.value?.task.currentStage !== 'P4') throw new Error('任务尚未进入P4')
}
const handleChecklistNavigation = (target: CutoverApi.CutoverNavigationTarget) => {
  if (target === 'TASK_OVERVIEW') emit('close')
}
const handleTaskChanged = async () => { await refreshDetail(); emit('changed') }
const handleApprovalChanged = async () => { if (!props.approvalOnly) await refreshDetail(); emit('changed') }
const assessmentDirty = () => !!detail.value && activeStagePanel.value === 'ASSESSMENT'
  && (JSON.stringify(detail.value.assessment?.answers || emptyAnswers()) !== JSON.stringify(assessmentModel.answers)
    || (detail.value.assessment?.manualGrade || null) !== assessmentModel.manualGrade
    || (detail.value.assessment?.manualCustomerServiceLevelCode || null) !== assessmentModel.manualCustomerServiceLevelCode)
const saveBeforeLeave = async () => {
  try {
    if (activeStagePanel.value === 'PLAN') return await planPanel.value?.saveBeforeLeave?.() ?? true
    if (activeStagePanel.value === 'CHECKLIST') return await checklistPanel.value?.saveBeforeLeave?.() ?? true
    if (!assessmentDirty()) return true
    if (saving.value || submitting.value) { message.warning('请等待评估保存完成后再离开。'); return false }
    if (readonly.value || !detail.value?.allowedActions.includes('SAVE_ASSESSMENT')) {
      message.warning('评估填写尚未保存，当前状态不能保存；填写已保留。'); return false
    }
    saving.value = true
    try { return await persistAssessment() } finally { saving.value = false }
  } catch {
    message.warning('草稿保存未完成，当前填写已保留，请保存成功后再离开。')
    return false
  }
}
const selectStage = async (stage: CutoverApi.CutoverStage) => {
  if (stage !== selectedStage.value && await saveBeforeLeave()) selectedStage.value = stage
}
const closeDetail = async () => { if (await saveBeforeLeave()) emit('close') }
const hasPendingDraft = () => assessmentDirty() || !!planPanel.value?.hasPendingDraft?.()
  || !!checklistPanel.value?.hasPendingDraft?.()
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (hasPendingDraft()) { event.preventDefault(); event.returnValue = '' }
}
defineExpose({ saveBeforeLeave, hasPendingDraft })
onMounted(() => window.addEventListener('beforeunload', beforeUnload))

watch(() => [props.taskId, props.projectId, props.approvalOnly], () => {
  loadSequence++
  detail.value = null
  detailError.value = ''
  selectedStage.value = null
  assessmentWrites.reset()
  void reloadDetail()
}, { immediate: true })
onBeforeUnmount(() => { loadSequence++; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<style scoped>
.detail-topbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; padding: 12px 20px; border-radius: 8px; background: #fff; }
.detail-topbar > div { display: flex; flex: 1; align-items: center; gap: 12px; min-width: 0; }
.detail-topbar strong { overflow: hidden; color: #1a2332; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.detail-topbar span { color: #999; font-size: 12px; }
.detail-body { min-height: 240px; }
.detail-actions { margin-top: 16px; }
.detail-navigation-actions { display: flex; align-items: center; gap: 8px; margin-right: auto; }
.detail-navigation-actions :deep(.el-button + .el-button) { margin-left: 0; }
.detail-stage-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; min-width: 0; }
.detail-stage-actions:empty { display: none; }
.detail-actions :deep(.el-button) { max-width: 100%; min-height: 32px; height: auto; white-space: normal; }
@media (width <= 767px) { .detail-topbar { flex-wrap: wrap; padding: 12px 14px; } .detail-topbar > div { flex-basis: calc(100% - 140px); flex-wrap: wrap; gap: 2px; } }
</style>
