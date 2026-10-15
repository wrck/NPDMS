<template>
  <section class="cutover-task-workbench full-detail" data-testid="cutover-task-detail">
    <header class="detail-topbar">
      <el-button link data-testid="close-cutover-detail" @click="emit('close')"><Icon icon="ep:back" />返回任务列表</el-button>
      <div><strong>{{ approvalOnly ? '割接审批' : detail?.task.taskName || '割接任务' }}</strong><span>{{ approvalOnly ? '审批来源与方案只读' : detail?.task.taskNo }}</span></div>
      <el-tag v-if="detail" :type="detail.task.taskStatus === 'ARCHIVED' ? 'success' : 'primary'" size="small">{{ statusLabels[detail.task.taskStatus] }}</el-tag>
      <el-button link data-testid="refresh-cutover-detail" :loading="detailLoading" @click="reloadDetail">刷新</el-button>
    </header>
    <div v-loading="detailLoading" class="detail-body">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon />
      <CutoverApprovalPanel v-if="approvalOnly" :key="`${taskId}:${approvalReload}`" :readonly="readonly"
        :task-id="taskId" complete-after-decision @changed="handleApprovalChanged" @decided="emit('close')" />
      <template v-else-if="detail">
        <CutoverWorkbenchSteps :steps="detail.workbenchSteps" :selected="selectedStage" @select="selectedStage = $event" />
        <template v-if="activeStagePanel === 'ASSESSMENT'">
          <el-alert title="根据问卷填写评估信息，人工确定A/B/C/D等级。D级使用简易割接流程；历史步骤仅供查看。" type="info" :closable="false" show-icon />
          <CutoverProjectContextCard :detail="detail" />
          <CutoverAssessmentPanel :model="assessmentModel" :customer-service-level="detail.customerServiceLevel?.serviceLevelCode"
            :editable="!readonly && !detailError && detail.allowedActions.includes('SAVE_ASSESSMENT')"
            :submittable="!readonly && !detailError && (detail.allowedActions.includes('SUBMIT_ASSESSMENT') || (detail.allowedActions.includes('SAVE_ASSESSMENT') && checkPermi(['pms:cutover-task:submit-assessment'])))"
            :saving="saving" :submitting="submitting" @save="saveAssessment" @submit="submitAssessment" />
        </template>
        <CutoverChecklistPanel v-else-if="activeStagePanel === 'CHECKLIST'" :detail="detail" :readonly="readonly || !!detailError"
          :refresh-workspace="refreshAfterChecklistSubmit" :refresh-draft="handleTaskChanged" @navigate="handleChecklistNavigation" />
        <CutoverPlanPanel v-else-if="activeStagePanel === 'PLAN'" :readonly="readonly || !!detailError" :task-id="detail.task.id"
          :task-version="detail.task.version" :manual-grade="detail.task.manualGrade" @changed="handleTaskChanged" />
        <CutoverApprovalPanel v-else-if="activeStagePanel === 'APPROVAL'" :readonly="readonly || !!detailError" :task-id="detail.task.id"
          @changed="handleApprovalChanged" @decided="emit('close')" />
        <CutoverClosurePanel v-else-if="activeStagePanel === 'CLOSURE'" :readonly="readonly || !!detailError" :task-id="detail.task.id"
          :devices="detail.devices" @changed="handleTaskChanged" />
        <el-empty v-else description="当前步骤暂无可查看的内容" />
      </template>
    </div>
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
import CutoverAssessmentPanel from '../components/CutoverAssessmentPanel.vue'
import CutoverApprovalPanel from '../components/CutoverApprovalPanel.vue'
import CutoverChecklistPanel from '../components/CutoverChecklistPanel.vue'
import CutoverClosurePanel from '../components/CutoverClosurePanel.vue'
import CutoverPlanPanel from '../components/CutoverPlanPanel.vue'
import CutoverWorkbenchSteps from '../components/CutoverWorkbenchSteps.vue'

const props = defineProps<{ taskId: WireLong; projectId?: WireLong; readonly?: boolean; approvalOnly?: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const message = useMessage()
const detail = ref<CutoverTaskDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const selectedStage = ref<CutoverApi.CutoverStage | null>(null)
const approvalReload = ref(0)
const saving = ref(false)
const submitting = ref(false)
let loadSequence = 0
const assessmentWrites = createCutoverWriteCoordinator()
const assessmentModel = reactive<{ answers: AssessmentAnswers; manualGrade: ManualGrade | null }>({ answers: emptyAnswers(), manualGrade: null })
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
  } catch (error) {
    if (sequence !== loadSequence) return
    detailError.value = '割接任务详情加载失败，请点击刷新重试。当前无法办理任务。'
    throw error
  } finally {
    if (sequence === loadSequence) detailLoading.value = false
  }
}
const reloadDetail = async () => { try { await refreshDetail() } catch { /* 错误由详情页展示，历史数据不可继续办理。 */ } }

const persistAssessment = async () => {
  if (!detail.value) return false
  const command = { taskId: detail.value.task.id, taskVersion: detail.value.task.version,
    assessmentVersion: detail.value.assessment?.rowVersion || 0,
    data: JSON.parse(JSON.stringify(assessmentModel)) }
  const result = await assessmentWrites.run(JSON.stringify(['SAVE_ASSESSMENT', command]),
    idempotencyKey => CutoverApi.saveCutoverAssessment({ ...command, idempotencyKey }), refreshDetail)
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
    if (!stored || JSON.stringify(stored.answers) !== JSON.stringify(assessmentModel.answers) || stored.manualGrade !== assessmentModel.manualGrade) {
      if (!(await persistAssessment())) return
    }
    if (!detail.value?.assessment || !detail.value.allowedActions.includes('SUBMIT_ASSESSMENT')) {
      const answers = assessmentModel.answers
      const incomplete = !assessmentModel.manualGrade || !answers.businessImportanceLevel
        || !answers.operationComplexityLevel || !answers.hiddenRiskLevel || answers.sparePartApplied == null
      message.warning(incomplete
        ? '请填写业务重要等级、操作复杂度、隐患风险、备件是否申请，并选择割接等级。'
        : '评估信息已保存，但当前来源信息、实施条件或操作权限不满足提交要求，请刷新任务并核查。')
      return
    }
    const command = { taskId: detail.value.task.id, taskVersion: detail.value.task.version, assessmentVersion: detail.value.assessment.rowVersion }
    const result = await assessmentWrites.run(JSON.stringify(['SUBMIT_ASSESSMENT', command]),
      idempotencyKey => CutoverApi.submitCutoverAssessment({ ...command, idempotencyKey }), refreshDetail)
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

watch(() => [props.taskId, props.projectId, props.approvalOnly], () => {
  loadSequence++
  detail.value = null
  detailError.value = ''
  selectedStage.value = null
  assessmentWrites.reset()
  void reloadDetail()
}, { immediate: true })
onBeforeUnmount(() => { loadSequence++ })
</script>

<style scoped>
.detail-topbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; padding: 12px 20px; border-radius: 8px; background: #fff; }
.detail-topbar > div { display: flex; flex: 1; align-items: center; gap: 12px; min-width: 0; }
.detail-topbar strong { overflow: hidden; color: #1a2332; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.detail-topbar span { color: #999; font-size: 12px; }
.detail-body { min-height: 240px; }
@media (width <= 767px) { .detail-topbar { flex-wrap: wrap; padding: 12px 14px; } .detail-topbar > div { flex-basis: calc(100% - 140px); flex-wrap: wrap; gap: 2px; } }
</style>
