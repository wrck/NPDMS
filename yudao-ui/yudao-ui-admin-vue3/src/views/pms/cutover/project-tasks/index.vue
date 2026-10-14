<template>
  <CutoverTaskDetail v-if="selectedTaskId" :key="`${projectId}:${selectedTaskId}`" :task-id="selectedTaskId"
    :project-id="projectId" :readonly="readonly" @close="closeDetail" @changed="loadTasks" />
  <section v-else class="project-cutover-tasks" data-testid="project-cutover-tasks">
    <div class="task-toolbar">
      <strong>割接任务 <span class="task-count">{{ total }}</span></strong>
      <div>
        <el-button v-if="canCreate" data-testid="create-project-cutover" type="primary" v-hasPermi="['pms:cutover-task:create']" @click="createVisible = true">新增割接任务</el-button>
        <el-button :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-12px" />
    <el-table v-loading="loading" :data="tasks" row-key="id" :empty-text="error ? '任务列表未加载成功' : '当前项目暂无割接任务'">
      <el-table-column prop="taskNo" label="任务编号" min-width="165" />
      <el-table-column prop="taskName" label="任务名称" min-width="210" show-overflow-tooltip />
      <el-table-column label="来源" width="100"><template #default="{ row }">{{ sourceLabels[row.intakeSourceType] || row.intakeSourceType }}</template></el-table-column>
      <el-table-column label="等级" width="80"><template #default="{ row }">{{ row.manualGrade || '待确认' }}</template></el-table-column>
      <el-table-column label="当前步骤" min-width="155"><template #default="{ row }">{{ stageLabels[row.currentStage] || '历史记录' }}</template></el-table-column>
      <el-table-column label="状态" min-width="120"><template #default="{ row }"><el-tag :type="row.taskStatus === 'ARCHIVED' ? 'success' : 'info'">{{ statusLabels[row.taskStatus] || row.taskStatus }}</el-tag></template></el-table-column>
      <el-table-column label="生成时间" min-width="165"><template #default="{ row }">{{ formatWireDateTime(row.generatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="85" fixed="right"><template #default="{ row }">
        <el-button link type="primary" data-testid="open-project-cutover" @click="selectedTaskId = row.id">{{ readonly || row.taskStatus === 'ARCHIVED' || row.intakeSourceType === 'LEGACY_FORWARD' ? '查看' : '办理' }}</el-button>
      </template></el-table-column>
    </el-table>
    <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="loadTasks" />
    <CutoverCreateWizard v-if="canCreate" v-model="createVisible" :project-id="projectId" @created="createdTask" />
  </section>
</template>

<script setup lang="ts">
import * as CutoverApi from '@/api/pms/cutover/workbench'
import type { CutoverTaskSummary, WireLong } from '@/api/pms/cutover/workbench'
import { useOperationClient } from '@/components/BusinessView/operationHost'
import { formatWireDateTime } from '../workbench/cutoverTaskInteraction'
import CutoverTaskDetail from '../task-detail/index.vue'
import CutoverCreateWizard from '../workbench/components/CutoverCreateWizard.vue'

const props = defineProps<{ projectId: WireLong; objectId?: WireLong; readonly?: boolean; allowedActions?: string[] }>()
const operationClient = useOperationClient()
const canCreate = computed(() => !props.readonly && !!operationClient.value && !!props.allowedActions?.includes('CREATE'))
const selectedTaskId = ref<WireLong | null>(null)
const createVisible = ref(false)
const tasks = ref<CutoverTaskSummary[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ pageNo: 1, pageSize: 10 })
let loadSequence = 0
const sourceLabels: Record<string, string> = { SELF_CREATED: '一线自建', ITR: 'ITR', PROJECT_EVENT: 'PMS', LEGACY_FORWARD: '历史只读' }
const stageLabels: Record<string, string> = { P2: '割接等级确认', P3: '调研及风险考察', P4: '割接方案', P5: '割接审批', P6: '割接跟踪及闭环' }
const statusLabels: Record<string, string> = { GRADE_CONFIRMING: '人工分级中', SURVEYING: '现场调研', PLAN_DRAFTING: '方案编制', APPROVING: '审批中', CLOSURE_IN_PROGRESS: '跟踪与闭环', ARCHIVED: '已归档', LEGACY_UNKNOWN: '历史状态' }

const loadTasks = async () => {
  const sequence = ++loadSequence
  if (!props.projectId) { tasks.value = []; total.value = 0; return }
  loading.value = true
  error.value = ''
  try {
    const page = await CutoverApi.getCutoverTaskPage({ projectId: String(props.projectId), ...query })
    if (sequence !== loadSequence) return
    tasks.value = page.list
    total.value = Number(page.total)
  } catch {
    if (sequence !== loadSequence) return
    tasks.value = []
    total.value = 0
    error.value = '当前项目的割接任务加载失败，请刷新重试。'
  } finally { if (sequence === loadSequence) loading.value = false }
}
const closeDetail = async () => { selectedTaskId.value = null; await loadTasks() }
const createdTask = async (id: WireLong) => { selectedTaskId.value = id; await loadTasks() }
watch(() => [props.projectId, props.objectId], () => {
  tasks.value = []
  total.value = 0
  query.pageNo = 1
  selectedTaskId.value = props.objectId || null
  createVisible.value = false
  void loadTasks()
}, { immediate: true })
onBeforeUnmount(() => { loadSequence++ })
</script>

<style scoped>
.project-cutover-tasks { min-width: 0; padding: 16px; border-radius: var(--el-border-radius-base); background: var(--el-bg-color); }
.task-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.task-count { margin-left: 6px; color: var(--el-text-color-secondary); font-size: 13px; font-weight: normal; }
@media (width <= 767px) { .task-toolbar { flex-wrap: wrap; } }
</style>
