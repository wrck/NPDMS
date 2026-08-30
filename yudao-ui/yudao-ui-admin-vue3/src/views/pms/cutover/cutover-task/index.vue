<template>
  <main class="cutover-task-workbench">
    <ContentWrap>
      <header class="page-heading">
        <div><h1>割接任务工作台</h1><p>创建任务、完成人工分级，并查看 P2～P6 进度。</p></div>
        <el-button type="primary" v-hasPermi="['pms:cutover-task:create']" @click="createVisible = true"><Icon icon="ep:plus" />创建割接任务</el-button>
      </header>
      <el-form :model="query" label-position="top" class="filter-grid">
        <el-form-item label="项目ID"><el-input v-model="query.projectId" clearable /></el-form-item>
        <el-form-item label="当前阶段">
          <el-select v-model="query.currentStage" clearable><el-option v-for="stage in ['P2', 'P3', 'P4']" :key="stage" :label="stage" :value="stage" /></el-select>
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="query.taskStatus" clearable>
            <el-option label="人工分级中" value="GRADE_CONFIRMING" /><el-option label="现场调研" value="SURVEYING" /><el-option label="方案编制" value="PLAN_DRAFTING" />
          </el-select>
        </el-form-item>
        <el-form-item label=" "><el-button type="primary" @click="loadPage">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="rows" row-key="id" @row-dblclick="openDetail">
        <el-table-column prop="taskNo" label="任务编号" min-width="150" />
        <el-table-column prop="taskName" label="任务名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" min-width="180" show-overflow-tooltip />
        <el-table-column label="来源" min-width="120"><template #default="{ row }">{{ sourceLabels[row.intakeSourceType] }}</template></el-table-column>
        <el-table-column label="办事处" min-width="150"><template #default="{ row }">{{ row.officeName || '—' }}</template></el-table-column>
        <el-table-column label="计划时间" min-width="170"><template #default="{ row }">{{ formatWireDateTime(row.scheduledTime) }}</template></el-table-column>
        <el-table-column label="状态" min-width="140"><template #default="{ row }"><el-tag>{{ statusLabels[row.taskStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="人工等级" width="100"><template #default="{ row }">{{ row.manualGrade || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="loadPage" />
    </ContentWrap>

    <el-drawer v-model="detailVisible" title="割接任务详情" :size="drawerSize">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-heading"><div><span>{{ detail.task.taskNo }}</span><h2>{{ detail.task.taskName }}</h2></div><el-tag>{{ statusLabels[detail.task.taskStatus] }}</el-tag></div>
          <el-descriptions :column="detailColumns" border>
            <el-descriptions-item label="项目">{{ detail.task.projectName }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ sourceLabels[detail.source.intakeSourceType] }}</el-descriptions-item>
            <el-descriptions-item label="计划时间">{{ formatWireDateTime(detail.task.scheduledTime) }}</el-descriptions-item>
            <el-descriptions-item label="人工等级">{{ detail.task.manualGrade || '—' }}</el-descriptions-item>
            <el-descriptions-item label="客户等级">{{ detail.customerServiceLevel?.serviceLevelCode || '未配置' }}</el-descriptions-item>
            <el-descriptions-item label="实施就绪">{{ detail.implementationReadiness?.decision || '—' }}</el-descriptions-item>
          </el-descriptions>
          <CutoverWorkbenchSteps :steps="detail.workbenchSteps" class="workbench-steps" />
          <CutoverAssessmentPanel
            v-if="detail.task.currentStage === 'P2' || detail.assessment"
            :model="assessmentModel"
            :editable="detail.allowedActions.includes('SAVE_ASSESSMENT')"
            :submittable="detail.allowedActions.includes('SUBMIT_ASSESSMENT')"
            :saving="saving"
            :submitting="submitting"
            @save="saveAssessment"
            @submit="submitAssessment"
          />
          <el-empty v-else description="当前阶段暂无可编辑内容" />
        </template>
      </div>
    </el-drawer>

    <CutoverCreateWizard v-model="createVisible" @created="loadPage" />
  </main>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverApi from '@/api/pms/cutover/cutover-task'
import type { AssessmentAnswers, CutoverTaskDetail, CutoverTaskSummary, ManualGrade } from '@/api/pms/cutover/cutover-task'
import CutoverAssessmentPanel from './components/CutoverAssessmentPanel.vue'
import CutoverCreateWizard from './components/CutoverCreateWizard.vue'
import CutoverWorkbenchSteps from './components/CutoverWorkbenchSteps.vue'
import { formatWireDateTime, newIntentKey } from './cutoverTaskInteraction'

const message = useMessage()
const { width } = useWindowSize()
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const rows = ref<CutoverTaskSummary[]>([])
const total = ref(0)
const detail = ref<CutoverTaskDetail | null>(null)
const query = reactive({ projectId: '', currentStage: undefined as CutoverApi.CutoverStage | undefined, taskStatus: undefined as CutoverApi.CutoverStatus | undefined, pageNo: 1, pageSize: 20 })
const assessmentModel = reactive<{ answers: AssessmentAnswers; manualGrade: ManualGrade | null }>({ answers: emptyAnswers(), manualGrade: null })

const drawerSize = computed(() => (width.value < 768 ? '100%' : width.value < 1200 ? '80%' : '68%'))
const detailColumns = computed(() => (width.value < 768 ? 1 : 2))
const sourceLabels: Record<string, string> = { SELF_CREATED: '一线自建', ITR: 'ITR', PROJECT_EVENT: '项目事件', LEGACY_FORWARD: '历史只读' }
const statusLabels: Record<string, string> = { GRADE_CONFIRMING: '人工分级中', SURVEYING: '现场调研', PLAN_DRAFTING: '方案编制', LEGACY_UNKNOWN: '历史状态' }

function emptyAnswers(): AssessmentAnswers { return { businessImportanceLevel: null, operationComplexityLevel: null, hiddenRiskLevel: null, sparePartApplied: null } }

const loadPage = async () => {
  loading.value = true
  try {
    const result = await CutoverApi.getCutoverTaskPage({ projectId: query.projectId || undefined, currentStage: query.currentStage, taskStatus: query.taskStatus, pageNo: query.pageNo, pageSize: query.pageSize })
    rows.value = result.list
    total.value = Number(result.total)
  } finally { loading.value = false }
}

const resetQuery = () => { Object.assign(query, { projectId: '', currentStage: undefined, taskStatus: undefined, pageNo: 1 }); loadPage() }

const openDetail = async (row: { id: CutoverApi.WireLong }) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await CutoverApi.getCutoverTaskDetail(row.id)
    Object.assign(assessmentModel.answers, detail.value.assessment?.answers || emptyAnswers())
    assessmentModel.manualGrade = detail.value.assessment?.manualGrade || null
  } finally { detailLoading.value = false }
}

const refreshDetail = async () => { if (detail.value) await openDetail(detail.value.task) }

const saveAssessment = async () => {
  if (!detail.value) return
  saving.value = true
  try {
    await CutoverApi.saveCutoverAssessment(detail.value.task.id, detail.value.task.version, detail.value.assessment?.rowVersion || 0, assessmentModel)
    message.success('人工评估草稿已保存，任务阶段未推进')
    await refreshDetail()
  } finally { saving.value = false }
}

const submitAssessment = async () => {
  if (!detail.value?.assessment) return
  submitting.value = true
  try {
    await CutoverApi.submitCutoverAssessment(detail.value.task.id, detail.value.task.version, detail.value.assessment.rowVersion, newIntentKey())
    message.success(assessmentModel.manualGrade === 'D' ? '已进入 P4 方案编制' : '已进入 P3 现场调研')
    await refreshDetail(); await loadPage()
  } finally { submitting.value = false }
}

onMounted(loadPage)
</script>

<style scoped>
.cutover-task-workbench { min-width: 0; }
.page-heading, .detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h1, .detail-heading h2 { margin: 0; }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0 16px; }
.detail-body { min-height: 240px; }
.detail-heading { margin-bottom: 18px; }
.detail-heading span { color: var(--el-text-color-secondary); }
.workbench-steps { margin: 22px 0; }
@media (max-width: 1023px) { .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) { .page-heading { align-items: stretch; flex-direction: column; } .filter-grid { grid-template-columns: 1fr; } }
</style>
