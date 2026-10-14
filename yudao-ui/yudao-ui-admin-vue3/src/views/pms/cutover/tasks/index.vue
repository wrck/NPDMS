<template>
  <main class="cutover-task-list" data-testid="cutover-task-list">
    <ContentWrap>
      <el-form :model="query" inline @submit.prevent="search">
        <el-form-item v-if="checkPermi(['pms:project:query'])" label="项目">
          <el-select v-model="query.projectId" filterable remote clearable :remote-method="searchProjects" :loading="projectsLoading" placeholder="输入项目名称查找" class="!w-240px">
            <el-option v-for="project in projects" :key="String(project.id)" :value="String(project.id)" :label="project.projectName || project.projectCode || String(project.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前阶段"><el-select v-model="query.currentStage" clearable placeholder="全部阶段" class="!w-190px"><el-option v-for="(label, stage) in stageLabels" :key="stage" :value="stage" :label="label" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.taskStatus" clearable placeholder="全部状态" class="!w-160px"><el-option v-for="(label, status) in statusLabels" :key="status" :value="status" :label="label" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="loading">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </ContentWrap>
    <ContentWrap>
      <div class="list-heading"><h1>割接任务列表</h1><span>共 {{ total }} 项</span></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-12px" />
      <CutoverTaskTable :rows="rows" :loading="loading" :error="error" @open="openTask" />
      <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="loadPage" />
    </ContentWrap>
  </main>
</template>

<script setup lang="ts">
import { getCutoverTaskPage, type CutoverTaskListItem, type CutoverTaskListQuery } from '@/api/pms/cutover'
import { getProjectPage, type ProjectMasterVO } from '@/api/pms/project/projects'
import { checkPermi } from '@/utils/permission'
import CutoverTaskTable from '../components/CutoverTaskTable.vue'
import { stageLabels, statusLabels } from '../shared/taskPresentation'

defineOptions({ name: 'PmsCutoverTaskList' })
const route = useRoute()
const router = useRouter()
const rows = ref<CutoverTaskListItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const projects = ref<ProjectMasterVO[]>([])
const projectsLoading = ref(false)
const query = reactive<CutoverTaskListQuery>({ listKind: 'ALL', pageNo: 1, pageSize: 20 })
let sequence = 0
let projectSequence = 0

const searchProjects = async (projectName: string) => {
  const current = ++projectSequence
  projectsLoading.value = true
  try {
    const page = await getProjectPage({ pageNo: 1, pageSize: 30, projectName })
    if (current === projectSequence) projects.value = page.list
  } finally { if (current === projectSequence) projectsLoading.value = false }
}
const loadPage = async () => {
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const page = await getCutoverTaskPage({ ...query, projectId: query.projectId || undefined,
      currentStage: query.currentStage || undefined, taskStatus: query.taskStatus || undefined })
    if (current !== sequence) return
    rows.value = page.list
    total.value = Number(page.total)
  } catch {
    if (current !== sequence) return
    rows.value = []; total.value = 0
    error.value = '割接任务加载失败，请重新查询。'
  } finally { if (current === sequence) loading.value = false }
}
const search = () => { query.pageNo = 1; void loadPage() }
const reset = () => {
  Object.assign(query, { projectId: undefined, currentStage: undefined, taskStatus: undefined, pageNo: 1 })
  void loadPage()
}
const openTask = (task: CutoverTaskListItem) => {
  if (task.approvalOnly && task.approvalInstanceId) {
    return router.push(`/pms/cutover/approvals/${task.approvalInstanceId}`)
  }
  return router.push(`/pms/cutover/tasks/${task.id}`)
}
watch(() => route.query.projectId, (projectId) => {
  query.projectId = typeof projectId === 'string' && /^[1-9]\d*$/.test(projectId) ? projectId : undefined
  query.pageNo = 1
  void loadPage()
}, { immediate: true })
onActivated(() => { if (!loading.value) void loadPage() })
onBeforeUnmount(() => { sequence++; projectSequence++ })
</script>

<style scoped>
.cutover-task-list { min-width: 0; }
.list-heading { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.list-heading h1 { margin: 0; font-size: 18px; font-weight: 600; }
.list-heading span { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
