<template>
  <main class="cutover-task-workbench">
    <template v-if="!detailVisible">
      <el-alert type="info" :closable="false" show-icon class="flow-intro">
        <template #title>
          <div><strong>标准流程：</strong>新增／待办割接任务 → 割接等级确认 → 调研及风险考察 → 割接方案 → 割接审批 → 割接跟踪及闭环。</div>
          <div><strong>简易流程：</strong>D级割接跳过调研及风险考察，直接编制操作与回退方案。</div>
        </template>
      </el-alert>
      <CutoverDashboardKpis :data="kpis" :loading="kpiLoading" :error="kpiError" :selected="selectedCategory" @select="selectKpi" />
      <div class="home-toolbar">
        <div><el-tag v-if="selectedCategory" closable @close="resetQuery">{{ categoryLabels[selectedCategory] }}</el-tag><span v-else class="cut-muted">割接工作台</span></div>
        <div class="home-toolbar__actions">
          <el-button link type="primary" :aria-expanded="filterVisible" @click="filterVisible = !filterVisible"><Icon icon="ep:filter" />筛选任务</el-button>
          <el-button link data-testid="open-approval-todos" v-hasPermi="['pms:cutover-task:query-approval']" @click="openApprovalTodos">我的审批待办</el-button>
          <el-button link data-testid="open-reassignment-queue" v-hasPermi="['pms:cutover-task:reassign-approval']" @click="openReassignmentQueue">审批改派</el-button>
          <el-button link data-testid="refresh-workbench" :loading="loading" @click="refreshListAndKpis"><Icon icon="ep:refresh" />刷新</el-button>
        </div>
      </div>
      <section v-if="filterVisible" class="cut-card filter-card">
        <el-form :model="query" inline class="filter-grid">
          <el-form-item v-if="checkPermi(['pms:project:query'])" label="项目">
            <el-select v-model="query.projectId" filterable remote clearable :remote-method="searchProjects" :loading="projectsLoading" placeholder="输入项目名称查找" class="!w-240px">
              <el-option v-for="project in projectChoices" :key="String(project.id)" :value="String(project.id)" :label="project.projectName || project.projectCode || String(project.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="当前步骤"><el-select v-model="query.currentStage" clearable class="!w-180px" @change="selectedCategory = ''"><el-option v-for="(label, stage) in stageLabels" :key="stage" :label="label" :value="stage" /></el-select></el-form-item>
          <el-form-item label="任务状态"><el-select v-model="query.taskStatus" clearable class="!w-150px" @change="selectedCategory = ''"><el-option v-for="(label, status) in statusLabels" :key="status" :label="label" :value="status" /></el-select></el-form-item>
          <el-form-item><el-button type="primary" :loading="loading" @click="searchTasks">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
        </el-form>
      </section>
      <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
      <section v-if="selectedCategory" class="cut-card">
        <header class="cut-card__header"><h2><Icon icon="ep:document" />{{ categoryLabels[selectedCategory] }}割接任务</h2><small>共 {{ total }} 项</small></header>
        <el-table v-loading="loading" :data="rows" :empty-text="listError ? '任务列表未加载成功' : '暂无符合条件的割接任务'" row-key="id" @row-dblclick="openDetail">
          <el-table-column prop="projectName" label="项目名称" min-width="230" show-overflow-tooltip />
          <el-table-column prop="taskName" label="割接任务" min-width="190" show-overflow-tooltip />
          <el-table-column label="割接来源" width="110"><template #default="{ row }"><el-tag size="small" effect="light">{{ sourceLabels[row.intakeSourceType] }}</el-tag></template></el-table-column>
          <el-table-column label="办事处" min-width="120"><template #default="{ row }">{{ row.departmentName || '—' }}</template></el-table-column>
          <el-table-column label="当前步骤" min-width="160"><template #default="{ row }"><el-tag size="small" :type="row.taskStatus === 'ARCHIVED' ? 'success' : 'warning'">{{ row.taskStatus === 'ARCHIVED' ? '已归档' : stageLabels[row.currentStage] || '历史记录' }}</el-tag></template></el-table-column>
          <el-table-column label="任务生成时间" min-width="160"><template #default="{ row }">{{ formatWireDateTime(row.generatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="88" fixed="right"><template #default="{ row }"><el-button size="small" type="primary" @click="openDetail(row)">{{ row.taskStatus === 'ARCHIVED' ? '查看' : '办理' }}</el-button></template></el-table-column>
        </el-table>
        <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="loadPage" />
      </section>
      <template v-else>
        <section class="cut-card" aria-labelledby="cutover-todo-list-title">
          <header class="cut-card__header"><h2 id="cutover-todo-list-title"><Icon icon="ep:document" />割接任务待办</h2><small>共 {{ workbenchTodoTotal }} 项</small></header>
          <el-table v-loading="loading" :data="workbenchTodos" :empty-text="listError ? '待办列表未加载成功' : '暂无需要办理的割接任务'" row-key="id" @row-dblclick="openDetail">
            <el-table-column label="项目名称" min-width="250"><template #default="{ row }"><button type="button" class="project-link" @click="openDetail(row)">{{ row.projectName }}</button><small v-if="row.taskName !== row.projectName" class="task-subtitle">{{ row.taskName }}</small></template></el-table-column>
            <el-table-column label="项目负责人" min-width="120"><template #default="{ row }">{{ row.projectManagerName || '—' }}</template></el-table-column>
            <el-table-column label="割接来源" width="110"><template #default="{ row }"><el-tag size="small" effect="light" :type="row.intakeSourceType === 'ITR' ? 'primary' : 'warning'">{{ sourceLabels[row.intakeSourceType] }}</el-tag></template></el-table-column>
            <el-table-column label="办事处" min-width="120"><template #default="{ row }">{{ row.departmentName || '—' }}</template></el-table-column>
            <el-table-column label="任务生成时间" min-width="170"><template #default="{ row }">{{ formatWireDateTime(row.generatedAt) }}</template></el-table-column>
            <el-table-column label="操作" width="88" fixed="right"><template #default="{ row }"><el-button size="small" type="primary" @click="openDetail(row)">办理</el-button></template></el-table-column>
          </el-table>
          <Pagination v-model:page="workbenchTodoQuery.pageNo" v-model:limit="workbenchTodoQuery.pageSize" :total="workbenchTodoTotal" @pagination="loadPage" />
        </section>
        <section class="cut-card" aria-labelledby="cutover-self-list-title">
          <header class="cut-card__header">
            <h2 id="cutover-self-list-title"><Icon icon="ep:edit-pen" />自建割接任务</h2>
            <el-button data-testid="create-cutover-task" type="primary" v-hasPermi="['pms:cutover-task:create']" @click="createVisible = true"><Icon icon="ep:plus" />新增割接任务</el-button>
          </header>
          <el-table v-loading="loading" :data="selfCreatedTasks" :empty-text="listError ? '自建列表未加载成功' : '暂无自建割接任务，可新增任务'" row-key="id" @row-dblclick="openDetail">
            <el-table-column label="项目名称" min-width="270"><template #default="{ row }"><button type="button" class="project-link" @click="openDetail(row)">{{ row.projectName }}</button><small v-if="row.taskName !== row.projectName" class="task-subtitle">{{ row.taskName }}</small></template></el-table-column>
            <el-table-column label="创建人" min-width="120"><template #default="{ row }">{{ row.creatorName || '—' }}</template></el-table-column>
            <el-table-column label="创建时间" min-width="180"><template #default="{ row }">{{ formatWireDateTime(row.generatedAt) }}</template></el-table-column>
            <el-table-column label="当前步骤" min-width="190"><template #default="{ row }"><el-tag size="small" :type="row.taskStatus === 'ARCHIVED' ? 'success' : 'warning'">{{ row.taskStatus === 'ARCHIVED' ? '已归档' : stageLabels[row.currentStage] || '历史记录' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="88" fixed="right"><template #default="{ row }"><el-button size="small" @click="openDetail(row)">{{ row.taskStatus === 'ARCHIVED' ? '查看' : '继续' }}</el-button></template></el-table-column>
          </el-table>
          <Pagination v-model:page="selfCreatedQuery.pageNo" v-model:limit="selfCreatedQuery.pageSize" :total="selfCreatedTotal" @pagination="loadPage" />
        </section>
      </template>
    </template>

    <CutoverTaskDetail v-if="requestedTaskId" :key="`${requestedTaskId}:${approvalOnly}`" :task-id="requestedTaskId"
      :approval-only="approvalOnly" @changed="refreshListAndKpis" @close="closeDetail" />
    <CutoverCreateWizard v-model="createVisible" @created="createdTask" />

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


  </main>
</template>

<script setup lang="ts">
import './cutoverWorkbench.css'
import { checkPermi } from '@/utils/permission'
import { getProject, getProjectPage, type ProjectMasterVO } from '@/api/pms/project/projects'
import * as CutoverApi from '@/api/pms/cutover/workbench'
import type { CutoverDashboardKpiData, CutoverTaskSummary } from '@/api/pms/cutover/workbench'
import CutoverTaskDetail from '../task-detail/index.vue'
import CutoverCreateWizard from './components/CutoverCreateWizard.vue'
import CutoverDashboardKpis from './components/CutoverDashboardKpis.vue'
import { formatWireDateTime } from './cutoverTaskInteraction'

const loading = ref(false)
const listError = ref<string | null>(null)
const filterVisible = ref(false)
const selectedCategory = ref('')
const categoryLabels: Record<string, string> = { todo: '待办', archived: '已归档', approving: '审批中', rejected: '驳回待修改' }
const router = useRouter()
const route = useRoute()
const kpiLoading = ref(false)
const kpis = ref<CutoverDashboardKpiData | null>(null)
const kpiError = ref<string | null>(null)
const requestedTaskId = ref<CutoverApi.WireLong | null>(null)
const approvalOnly = ref(false)
const detailVisible = computed(() => requestedTaskId.value !== null)
const createVisible = ref(false)
const todoVisible = ref(false)
const reassignmentQueueVisible = ref(false)
const approvalQueueLoading = ref(false)
const rows = ref<CutoverTaskSummary[]>([])
const workbenchTodos = ref<CutoverApi.CutoverWorkbenchTaskSummary[]>([])
const selfCreatedTasks = ref<CutoverApi.CutoverWorkbenchTaskSummary[]>([])
const workbenchTodoTotal = ref(0)
const selfCreatedTotal = ref(0)
const workbenchTodoQuery = reactive({ pageNo: 1, pageSize: 10 })
const selfCreatedQuery = reactive({ pageNo: 1, pageSize: 10 })
const approvalTodos = ref<CutoverApi.CutoverApprovalTodoItem[]>([])
const reassignmentCandidates = ref<CutoverApi.CutoverApprovalReassignmentCandidate[]>([])
const total = ref(0)
const projectChoices = ref<ProjectMasterVO[]>([])
const projectsLoading = ref(false)
const searchProjects = async (projectName: string) => {
  projectsLoading.value = true
  try { projectChoices.value = (await getProjectPage({ pageNo: 1, pageSize: 30, projectName })).list }
  finally { projectsLoading.value = false }
}

const todoTotal = ref(0)
const reassignmentTotal = ref(0)
const query = reactive({
  projectId: '',
  currentStage: undefined as CutoverApi.CutoverStage | undefined,
  taskStatus: undefined as CutoverApi.CutoverStatus | undefined,
  pageNo: 1,
  pageSize: 20
})
const todoQuery = reactive({ pageNo: 1, pageSize: 10 })
const reassignmentQuery = reactive({ pageNo: 1, pageSize: 10 })
const stageLabels: Record<string, string> = { P2: '割接等级确认', P3: '调研及风险考察', P4: '割接方案', P5: '割接审批', P6: '割接跟踪及闭环' }
const sourceLabels: Record<string, string> = {
  SELF_CREATED: '一线自建',
  ITR: 'ITR',
  PROJECT_EVENT: 'PMS',
  LEGACY_FORWARD: '历史只读'
}
const loadPage = async () => {
  loading.value = true
  listError.value = null
  try {
    if (selectedCategory.value) {
      const result = await CutoverApi.getDashboardTasks(selectedCategory.value, query.pageNo, query.pageSize)
      rows.value = result.list
      total.value = Number(result.total)
    } else {
      const filters = { projectId: query.projectId || undefined, currentStage: query.currentStage, taskStatus: query.taskStatus }
      const [todos, selfCreated] = await Promise.all([
        CutoverApi.getWorkbenchTasks({ listKind: 'TODO', ...filters, ...workbenchTodoQuery }),
        CutoverApi.getWorkbenchTasks({ listKind: 'SELF_CREATED', ...filters, ...selfCreatedQuery })
      ])
      if (checkPermi(['pms:project:query'])) {
        const projects = await Promise.allSettled([...new Set([...todos.list, ...selfCreated.list].filter(row => !row.approvalOnly).map(row => String(row.projectId)))].map(id => getProject(id)))
        const managers = new Map(projects.flatMap(result => result.status === 'fulfilled' && result.value.id ? [[String(result.value.id), result.value.managerName] as const] : []))
        for (const row of [...todos.list, ...selfCreated.list]) row.projectManagerName = managers.get(String(row.projectId)) || null
      }
      workbenchTodos.value = todos.list
      workbenchTodoTotal.value = Number(todos.total)
      selfCreatedTasks.value = selfCreated.list
      selfCreatedTotal.value = Number(selfCreated.total)
    }
  } catch {
    listError.value = '割接任务加载失败，请重试；如持续失败，请联系管理员。'
    rows.value = []; workbenchTodos.value = []; selfCreatedTasks.value = []
    total.value = 0; workbenchTodoTotal.value = 0; selfCreatedTotal.value = 0
  } finally {
    loading.value = false
  }
}

const loadKpis = async () => {
  if (kpiLoading.value) return
  kpiLoading.value = true
  kpiError.value = null
  try {
    kpis.value = await CutoverApi.getCutoverDashboardKpis()
  } catch {
    kpiError.value = '割接任务概览加载失败，请稍后重试'
  } finally {
    kpiLoading.value = false
  }
}

const refreshListAndKpis = async () => {
  void loadKpis()
  await loadPage()
}

const resetQuery = () => {
  selectedCategory.value = ''
  Object.assign(query, { projectId: '', currentStage: undefined, taskStatus: undefined, pageNo: 1 })
  workbenchTodoQuery.pageNo = 1
  selfCreatedQuery.pageNo = 1
  loadPage()
}

const searchTasks = () => { workbenchTodoQuery.pageNo = 1; selfCreatedQuery.pageNo = 1; query.pageNo = 1; loadPage() }

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
  void openDetail({ id: taskId, approvalOnly: true })
}
const openApprovalFromQueue = async (taskId: CutoverApi.WireLong) => {
  todoVisible.value = false
  reassignmentQueueVisible.value = false
  openApproval(taskId)
}

const openDetail = async (row: { id: CutoverApi.WireLong; approvalOnly?: boolean }) => {
  requestedTaskId.value = row.id
  approvalOnly.value = !!row.approvalOnly
  const { mode: _mode, ...query } = route.query
  await router.replace({ query: { ...query, taskId: String(row.id), ...(row.approvalOnly ? { mode: 'approval' } : {}) } })
}
const closeDetail = async () => {
  requestedTaskId.value = null
  approvalOnly.value = false
  const { taskId: _taskId, mode: _mode, ...query } = route.query
  await router.replace({ query })
  await refreshListAndKpis()
}
const createdTask = async (id: CutoverApi.WireLong) => { await refreshListAndKpis(); await openDetail({ id }) }
onMounted(async () => {
  await refreshListAndKpis()
  if (route.query.taskId) await openDetail({ id: String(route.query.taskId), approvalOnly: route.query.mode === 'approval' })
})
const selectKpi = async (key: string) => {
  selectedCategory.value = key
  query.projectId = ''
  query.pageNo = 1
  query.currentStage = undefined
  query.taskStatus = undefined
  await loadPage()
}
</script>

<style scoped>
.home-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.home-toolbar__actions { display: flex; align-items: center; gap: 16px; }
.home-toolbar__actions .el-button + .el-button { margin-left: 0; }
.filter-card { padding: 16px 20px 0; }
.project-link { padding: 0; border: 0; background: transparent; color: #264d77; font: inherit; text-align: left; cursor: pointer; }
.project-link:hover { color: #1677ff; text-decoration: underline; }
.task-subtitle { display: block; overflow: hidden; color: #999; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
@media (width <= 767px) { .home-toolbar { flex-wrap: wrap; } .home-toolbar__actions { flex-wrap: wrap; gap: 8px; } .filter-grid :deep(.el-form-item) { width: 100%; } }
</style>
