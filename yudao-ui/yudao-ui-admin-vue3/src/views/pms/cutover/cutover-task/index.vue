<template>
  <main class="cutover-task-workbench">
    <ContentWrap>
      <header class="page-heading">
        <div><h1>割接任务工作台</h1><p>创建任务、完成人工分级，并查看 P2～P6 进度。</p></div>
        <div class="heading-actions">
          <el-button
            data-testid="open-approval-todos"
            v-hasPermi="['pms:cutover-task:query-approval']"
            @click="openApprovalTodos"
            >我的审批待办</el-button
          >
          <el-button
            data-testid="open-reassignment-queue"
            v-hasPermi="['pms:cutover-task:reassign-approval']"
            @click="openReassignmentQueue"
            >审批改派队列</el-button
          >
          <el-button
            type="primary"
            v-hasPermi="['pms:cutover-task:create']"
            @click="createVisible = true"
            ><Icon icon="ep:plus" />创建割接任务</el-button
          >
        </div>
      </header>
      <el-form :model="query" label-position="top" class="filter-grid">
        <el-form-item label="项目ID"><el-input v-model="query.projectId" clearable /></el-form-item>
        <el-form-item label="当前阶段">
          <el-select v-model="query.currentStage" clearable
            ><el-option
              v-for="stage in ['P2', 'P3', 'P4', 'P5', 'P6']"
              :key="stage"
              :label="stage"
              :value="stage"
          /></el-select>
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="query.taskStatus" clearable>
            <el-option label="人工分级中" value="GRADE_CONFIRMING" /><el-option
              label="现场调研"
              value="SURVEYING"
            /><el-option label="方案编制" value="PLAN_DRAFTING" /><el-option
              label="审批中"
              value="APPROVING"
            /><el-option label="关闭处理中" value="CLOSURE_IN_PROGRESS" />
          </el-select>
        </el-form-item>
        <el-form-item label=" "
          ><el-button type="primary" @click="loadPage">查询</el-button
          ><el-button @click="resetQuery">重置</el-button></el-form-item
        >
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="rows" row-key="id" @row-dblclick="openDetail">
        <el-table-column prop="taskNo" label="任务编号" min-width="150" />
        <el-table-column prop="taskName" label="任务名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" min-width="180" show-overflow-tooltip />
        <el-table-column label="来源" min-width="120"
          ><template #default="{ row }">{{
            sourceLabels[row.intakeSourceType]
          }}</template></el-table-column
        >
        <el-table-column label="办事处" min-width="150"
          ><template #default="{ row }">{{ row.officeName || '—' }}</template></el-table-column
        >
        <el-table-column label="生成时间" min-width="170"
          ><template #default="{ row }">{{
            formatWireDateTime(row.generatedAt)
          }}</template></el-table-column
        >
        <el-table-column label="状态" min-width="140"
          ><template #default="{ row }"
            ><el-tag>{{ statusLabels[row.taskStatus] }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="人工等级" width="100"
          ><template #default="{ row }">{{ row.manualGrade || '—' }}</template></el-table-column
        >
        <el-table-column label="操作" width="150" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row)">详情</el-button
            ><el-button
              data-testid="open-approval-from-task"
              link
              type="primary"
              v-hasPermi="['pms:cutover-task:query-approval']"
              @click="openApproval(row.id)"
              >审批</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <Pagination
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="loadPage"
      />
    </ContentWrap>

    <el-drawer v-model="detailVisible" title="割接任务详情" :size="drawerSize">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-heading"
            ><div
              ><span>{{ detail.task.taskNo }}</span
              ><h2>{{ detail.task.taskName }}</h2></div
            ><el-tag>{{ statusLabels[detail.task.taskStatus] }}</el-tag></div
          >
          <el-descriptions :column="detailColumns" border>
            <el-descriptions-item label="项目">{{ detail.task.projectName }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{
              sourceLabels[detail.source.intakeSourceType]
            }}</el-descriptions-item>
            <el-descriptions-item label="计划时间">{{
              formatWireDateTime(detail.task.scheduledTime)
            }}</el-descriptions-item>
            <el-descriptions-item label="人工等级">{{
              detail.task.manualGrade || '—'
            }}</el-descriptions-item>
            <el-descriptions-item label="客户等级">{{
              detail.customerServiceLevel?.serviceLevelCode || '未配置'
            }}</el-descriptions-item>
            <el-descriptions-item label="实施就绪">{{
              detail.implementationReadiness?.decision || '—'
            }}</el-descriptions-item>
          </el-descriptions>
          <CutoverWorkbenchSteps :steps="detail.workbenchSteps" class="workbench-steps" />
          <CutoverAssessmentPanel
            v-if="activeStagePanel === 'ASSESSMENT'"
            :model="assessmentModel"
            :editable="detail.allowedActions.includes('SAVE_ASSESSMENT')"
            :submittable="detail.allowedActions.includes('SUBMIT_ASSESSMENT')"
            :saving="saving"
            :submitting="submitting"
            @save="saveAssessment"
            @submit="submitAssessment"
          />
          <CutoverChecklistPanel
            v-else-if="activeStagePanel === 'CHECKLIST'"
            :detail="detail"
            @submitted="handleChecklistSubmitted"
          />
          <CutoverPlanPanel
            v-else-if="activeStagePanel === 'PLAN'"
            :task-id="detail.task.id"
            :task-version="detail.task.version"
            :manual-grade="detail.task.manualGrade"
            @changed="handlePlanChanged"
          />
          <CutoverApprovalPanel
            v-else-if="activeStagePanel === 'APPROVAL'"
            :task-id="detail.task.id"
            @changed="handleApprovalChanged"
          />
          <el-empty v-else description="当前阶段暂无可编辑内容" />
        </template>
      </div>
    </el-drawer>

    <CutoverCreateWizard v-model="createVisible" @created="loadPage" />

    <el-dialog v-model="todoVisible" title="我的审批待办" width="min(920px, 94vw)">
      <el-table v-loading="approvalQueueLoading" :data="approvalTodos" row-key="approvalInstanceId">
        <el-table-column prop="taskCode" label="任务编号" min-width="150" />
        <el-table-column prop="taskName" label="任务名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="grade" label="等级" width="80" />
        <el-table-column prop="nodeCode" label="当前节点" min-width="130" />
        <el-table-column label="进入待办时间" min-width="170"
          ><template #default="{ row }">{{
            formatWireDateTime(row.createdAt)
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button
              data-testid="open-approval-from-todo"
              link
              type="primary"
              @click="openApprovalFromQueue(row.taskId)"
              >办理</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <Pagination
        v-model:page="todoQuery.pageNo"
        v-model:limit="todoQuery.pageSize"
        :total="todoTotal"
        @pagination="loadApprovalTodos"
      />
    </el-dialog>

    <el-dialog v-model="reassignmentQueueVisible" title="审批改派队列" width="min(1040px, 94vw)">
      <el-table v-loading="approvalQueueLoading" :data="reassignmentCandidates" row-key="nodeId">
        <el-table-column prop="taskCode" label="任务编号" min-width="150" />
        <el-table-column prop="taskName" label="任务名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="grade" label="等级" width="80" />
        <el-table-column prop="nodeCode" label="节点" min-width="120" />
        <el-table-column prop="nodeStatus" label="状态" width="100" />
        <el-table-column label="当前审批人" min-width="140"
          ><template #default="{ row }">{{
            row.currentApproverUserId || '未指派'
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button
              data-testid="open-approval-from-reassignment"
              link
              type="primary"
              @click="openApprovalFromQueue(row.taskId)"
              >改派</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <Pagination
        v-model:page="reassignmentQuery.pageNo"
        v-model:limit="reassignmentQuery.pageSize"
        :total="reassignmentTotal"
        @pagination="loadReassignmentQueue"
      />
    </el-dialog>

    <el-drawer v-model="approvalVisible" title="P5 分级审批" :size="drawerSize">
      <CutoverApprovalPanel
        v-if="approvalTaskId !== null"
        :key="String(approvalTaskId)"
        :task-id="approvalTaskId"
        @changed="handleApprovalWorkspaceChanged"
      />
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverApi from '@/api/pms/cutover/cutover-task'
import type {
  AssessmentAnswers,
  CutoverTaskDetail,
  CutoverTaskSummary,
  ManualGrade
} from '@/api/pms/cutover/cutover-task'
import CutoverAssessmentPanel from './components/CutoverAssessmentPanel.vue'
import CutoverApprovalPanel from './components/CutoverApprovalPanel.vue'
import CutoverCreateWizard from './components/CutoverCreateWizard.vue'
import CutoverChecklistPanel from './components/CutoverChecklistPanel.vue'
import CutoverPlanPanel from './components/CutoverPlanPanel.vue'
import CutoverWorkbenchSteps from './components/CutoverWorkbenchSteps.vue'
import { activeCutoverStagePanel, formatWireDateTime, newIntentKey } from './cutoverTaskInteraction'

const message = useMessage()
const { width } = useWindowSize()
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const todoVisible = ref(false)
const reassignmentQueueVisible = ref(false)
const approvalVisible = ref(false)
const approvalTaskId = ref<CutoverApi.WireLong | null>(null)
const approvalQueueLoading = ref(false)
const rows = ref<CutoverTaskSummary[]>([])
const approvalTodos = ref<CutoverApi.CutoverApprovalTodoItem[]>([])
const reassignmentCandidates = ref<CutoverApi.CutoverApprovalReassignmentCandidate[]>([])
const total = ref(0)
const todoTotal = ref(0)
const reassignmentTotal = ref(0)
const detail = ref<CutoverTaskDetail | null>(null)
const query = reactive({
  projectId: '',
  currentStage: undefined as CutoverApi.CutoverStage | undefined,
  taskStatus: undefined as CutoverApi.CutoverStatus | undefined,
  pageNo: 1,
  pageSize: 20
})
const todoQuery = reactive({ pageNo: 1, pageSize: 10 })
const reassignmentQuery = reactive({ pageNo: 1, pageSize: 10 })
const assessmentModel = reactive<{ answers: AssessmentAnswers; manualGrade: ManualGrade | null }>({
  answers: emptyAnswers(),
  manualGrade: null
})

const drawerSize = computed(() => (width.value < 768 ? '100%' : width.value < 1200 ? '80%' : '68%'))
const detailColumns = computed(() => (width.value < 768 ? 1 : 2))
const activeStagePanel = computed(() =>
  activeCutoverStagePanel(detail.value?.task.currentStage || null)
)
const sourceLabels: Record<string, string> = {
  SELF_CREATED: '一线自建',
  ITR: 'ITR',
  PROJECT_EVENT: '项目事件',
  LEGACY_FORWARD: '历史只读'
}
const statusLabels: Record<string, string> = {
  GRADE_CONFIRMING: '人工分级中',
  SURVEYING: '现场调研',
  PLAN_DRAFTING: '方案编制',
  APPROVING: '审批中',
  CLOSURE_IN_PROGRESS: '关闭处理中',
  LEGACY_UNKNOWN: '历史状态'
}

function emptyAnswers(): AssessmentAnswers {
  return {
    businessImportanceLevel: null,
    operationComplexityLevel: null,
    hiddenRiskLevel: null,
    sparePartApplied: null
  }
}

const loadPage = async () => {
  loading.value = true
  try {
    const result = await CutoverApi.getCutoverTaskPage({
      projectId: query.projectId || undefined,
      currentStage: query.currentStage,
      taskStatus: query.taskStatus,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    rows.value = result.list
    total.value = Number(result.total)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  Object.assign(query, { projectId: '', currentStage: undefined, taskStatus: undefined, pageNo: 1 })
  loadPage()
}

const loadApprovalTodos = async () => {
  approvalQueueLoading.value = true
  try {
    const result = await CutoverApi.getCutoverApprovalTodos(todoQuery)
    approvalTodos.value = result.list
    todoTotal.value = Number(result.total)
  } finally {
    approvalQueueLoading.value = false
  }
}

const loadReassignmentQueue = async () => {
  approvalQueueLoading.value = true
  try {
    const result = await CutoverApi.getCutoverApprovalReassignmentCandidates(reassignmentQuery)
    reassignmentCandidates.value = result.list
    reassignmentTotal.value = Number(result.total)
  } finally {
    approvalQueueLoading.value = false
  }
}

const openApprovalTodos = async () => {
  todoVisible.value = true
  await loadApprovalTodos()
}
const openReassignmentQueue = async () => {
  reassignmentQueueVisible.value = true
  await loadReassignmentQueue()
}
const openApproval = (taskId: CutoverApi.WireLong) => {
  approvalTaskId.value = taskId
  approvalVisible.value = true
}
const openApprovalFromQueue = async (taskId: CutoverApi.WireLong) => {
  todoVisible.value = false
  reassignmentQueueVisible.value = false
  openApproval(taskId)
}

const openDetail = async (row: { id: CutoverApi.WireLong }) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await CutoverApi.getCutoverTaskDetail(row.id)
    Object.assign(assessmentModel.answers, detail.value.assessment?.answers || emptyAnswers())
    assessmentModel.manualGrade = detail.value.assessment?.manualGrade || null
  } finally {
    detailLoading.value = false
  }
}

const refreshDetail = async () => {
  if (detail.value) await openDetail(detail.value.task)
}

const saveAssessment = async () => {
  if (!detail.value) return
  saving.value = true
  try {
    await CutoverApi.saveCutoverAssessment(
      detail.value.task.id,
      detail.value.task.version,
      detail.value.assessment?.rowVersion || 0,
      assessmentModel
    )
    message.success('人工评估草稿已保存，任务阶段未推进')
    await refreshDetail()
  } finally {
    saving.value = false
  }
}

const submitAssessment = async () => {
  if (!detail.value?.assessment) return
  submitting.value = true
  try {
    await CutoverApi.submitCutoverAssessment(
      detail.value.task.id,
      detail.value.task.version,
      detail.value.assessment.rowVersion,
      newIntentKey()
    )
    message.success(
      assessmentModel.manualGrade === 'D' ? '已进入 P4 方案编制' : '已进入 P3 现场调研'
    )
    await refreshDetail()
    await loadPage()
  } finally {
    submitting.value = false
  }
}

const handleChecklistSubmitted = async () => {
  await refreshDetail()
  await loadPage()
}
const handlePlanChanged = async () => {
  await refreshDetail()
  await loadPage()
}
const handleApprovalChanged = async () => {
  await refreshDetail()
  await loadPage()
}
const handleApprovalWorkspaceChanged = async () => {
  await loadPage()
  if (todoVisible.value) await loadApprovalTodos()
  if (reassignmentQueueVisible.value) await loadReassignmentQueue()
}

onMounted(loadPage)
</script>

<style scoped>
.cutover-task-workbench {
  min-width: 0;
}

.page-heading,
.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h1,
.detail-heading h2 {
  margin: 0;
}

.page-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.heading-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 16px;
}

.detail-body {
  min-height: 240px;
}

.detail-heading {
  margin-bottom: 18px;
}

.detail-heading span {
  color: var(--el-text-color-secondary);
}

.workbench-steps {
  margin: 22px 0;
}

@media (width <= 1023px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 767px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .heading-actions {
    justify-content: stretch;
  }

  .heading-actions :deep(.el-button) {
    flex: 1 1 100%;
    margin-left: 0;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
