<template>
  <main class="inspection-page inspection-task-list" data-testid="inspection-task-list">
    <template v-if="!createVisible">
      <section class="inspection-card">
        <header class="inspection-card__header">
          <h1>巡检任务列表</h1>
          <el-button v-if="canCreate" type="primary" @click="openCreate"
            ><Icon icon="ep:plus" />新增巡检任务</el-button
          >
        </header>
        <div class="inspection-card__body filter-body">
          <el-alert
            v-if="!canQuery"
            title="没有巡检任务查询权限"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-form v-if="canQuery" :model="query" inline @submit.prevent="search">
            <el-form-item label="任务名称">
              <el-input v-model="query.keyword" clearable placeholder="输入任务名称" />
            </el-form-item>
            <el-form-item label="项目" class="filter-project-item">
              <el-select
                v-model="query.projectId"
                filterable
                remote
                clearable
                :remote-method="(keyword: string) => searchProjects({ keyword, target: 'filter' })"
                :loading="filterProjectsLoading"
                placeholder="输入项目名称查找"
                class="project-select"
              >
                <el-option
                  v-for="project in filterProjects"
                  :key="String(project.id)"
                  :value="String(project.id)"
                  :label="project.name"
                >
                  <span>{{ project.name }}</span>
                  <span class="option-note">{{ project.code }}</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="巡检方式" class="filter-compact-item">
              <el-select
                v-model="query.mode"
                clearable
                placeholder="全部方式"
                class="compact-select"
              >
                <el-option label="在线巡检" value="ONLINE" />
                <el-option label="离线巡检" value="OFFLINE" />
              </el-select>
            </el-form-item>
            <el-form-item label="任务状态" class="filter-compact-item">
              <el-select
                v-model="query.statusCode"
                clearable
                placeholder="全部状态"
                class="compact-select"
              >
                <el-option
                  v-for="status in statuses"
                  :key="status.value"
                  :value="status.value"
                  :label="status.label"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
              <el-button @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="filterProjectsError"
            :title="filterProjectsError"
            type="error"
            :closable="false"
          />
        </div>
      </section>
      <section v-if="canQuery" class="inspection-card">
        <header class="inspection-card__header"
          ><h2>巡检任务</h2><span class="help-text">共 {{ total }} 项</span></header
        >
        <el-alert
          v-if="listError"
          :title="listError"
          type="error"
          :closable="false"
          show-icon
          class="mb-16px"
        >
          <template #default
            ><el-button link type="primary" @click="loadPage">重新加载</el-button></template
          >
        </el-alert>
        <el-table
          v-loading="loading"
          :data="rows"
          :empty-text="listError ? '加载失败，请重新加载' : '暂无符合条件的巡检任务'"
        >
          <el-table-column prop="taskCode" label="任务编号" min-width="190" />
          <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="projectName" label="项目" min-width="180" show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
          <el-table-column label="负责人" min-width="110">
            <template #default="{ row }">{{ row.ownerName || '未提供姓名' }}</template>
          </el-table-column>
          <el-table-column label="巡检方式" width="110">
            <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column label="任务状态" min-width="130">
            <template #default="{ row }">
              <el-tag :type="statusType(row.statusCode)">{{ statusLabel(row.statusCode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="计划时间" min-width="170">
            <template #default="{ row }">{{
              row.plannedAt ? formatDate(row.plannedAt) : '未设置'
            }}</template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{
              row.createTime ? formatDate(row.createTime) : '—'
            }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" :fixed="smallScreen ? false : 'right'">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTask(row.id)">{{
                actionLabel(row.statusCode)
              }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          v-model:page="query.pageNo"
          v-model:limit="query.pageSize"
          :total="total"
          @pagination="loadPage"
        />
      </section>
    </template>
    <template v-else>
      <header class="create-heading"><h1>新增巡检任务</h1><span>手动创建</span></header>
      <InspectionSteps :active="0" :reached="0" :interactive="false" />
      <el-alert
        v-if="createError"
        :title="createError"
        :type="pendingCreate ? 'warning' : 'error'"
        :closable="false"
        show-icon
        class="create-error"
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        :disabled="submitting || !!pendingCreate"
        @submit.prevent="submitCreate"
      >
        <ProjectInformationCard :project="projectInformation" :customer-service-level="projectCustomerServiceLevel" :loading="projectInformationLoading"
          :error="createProjectsError || projectInformationError" hint="从授权项目中选择">
              <el-form-item label="项目名称" prop="projectId">
                <el-select
                  v-model="form.projectId"
                  data-testid="inspection-create-project-select"
                  filterable
                  remote
                  clearable
                  :remote-method="
                    (keyword: string) => searchProjects({ keyword, target: 'create' })
                  "
                  :loading="createProjectsLoading"
                  placeholder="搜索并选择授权项目"
                  @change="changeProject"
                >
                  <el-option
                    v-for="project in createProjects"
                    :key="String(project.id)"
                    :value="String(project.id)"
                    :label="project.name"
                    ><span>{{ project.name }}</span
                    ><span class="option-note">{{ project.code }}</span></el-option
                  >
                </el-select>
              </el-form-item>
        </ProjectInformationCard>
        <el-form-item prop="deviceIds" class="device-selection-field" style="display: block">
          <ProjectDeviceSelection :model-value="devices" :project-id="form.projectId"
            :disabled="submitting || !!pendingCreate" :project-loading="projectInformationLoading"
            :project-error="projectInformationError" empty-text="请添加本次巡检设备"
            @update:model-value="updateDevices" @busy="devicesLoading = $event" />
        </el-form-item>
        <section class="inspection-card">
          <header class="inspection-card__header"
            ><h2><Icon icon="ep:setting" />巡检准备信息</h2></header
          >
          <div class="inspection-card__body">
            <div class="form-grid">
              <el-form-item label="计划时间"
                ><el-date-picker
                  v-model="form.plannedAt"
                  data-testid="inspection-create-planned-at"
                  type="datetime"
                  placeholder="选择计划巡检时间"
              /></el-form-item>
            </div>
            <el-form-item label="巡检背景" prop="background"
              ><el-input
                v-model="form.background"
                type="textarea"
                :rows="3"
                placeholder="填写本次巡检的背景与目的"
            /></el-form-item>
            <h3 class="section-title">巡检方式选择</h3>
            <el-form-item prop="mode" class="mode-field">
              <el-radio-group v-model="form.mode" class="mode-options">
                <el-radio value="ONLINE"
                  >在线巡检<span class="mode-description">通过设备连接采集巡检数据</span></el-radio
                >
                <el-radio value="OFFLINE"
                  >离线巡检<span class="mode-description"
                    >在平台外执行命令后上传日志</span
                  ></el-radio
                >
              </el-radio-group>
            </el-form-item>
            <p class="help-text"
              >保存后进入任务详情，继续选择巡检规则和配置执行信息。负责人默认为当前登录工程师，服务经理可在创建后分配。</p
            >
          </div>
        </section>
      </el-form>
      <footer class="create-actions">
        <el-button :disabled="submitting || !!pendingCreate" @click="returnToList"
          >返回任务列表</el-button
        >
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!canCreate || (!pendingCreate && (devicesLoading))"
          @click="submitCreate"
          >{{ pendingCreate ? '重试原请求' : '保存巡检准备' }}</el-button
        >
      </footer>
    </template>
  </main>
</template>

<script setup lang="ts">
import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  reactive,
  ref,
  watch
} from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules, TagProps } from 'element-plus'
import * as InspectionApi from '@/api/pms/inspection/tasks'
import type { CreateTaskInput, TaskSummary, WireLong } from '@/api/pms/inspection/tasks'
import { generateUUID } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { inspectionFailure } from '../shared/interaction'
import InspectionSteps from '../components/InspectionSteps.vue'
import ProjectInformationCard from '@/components/ProjectInformationCard/index.vue'
import { useProjectInformation } from '@/components/ProjectInformationCard/useProjectInformation'
import { useProjectOptions } from '@/components/ProjectInformationCard/useProjectOptions'
import type { ProjectDevice } from '@/api/pms/project/selection'
import ProjectDeviceSelection from '@/components/ProjectDeviceSelection/index.vue'
import '../shared/inspection.css'

defineOptions({ name: 'PmsInspectionTaskList' })
type DeviceOption = ProjectDevice
type ProjectSearch = { keyword: string; target: 'filter' | 'create' }

const router = useRouter()
const route = useRoute()
const message = useMessage()
const smallScreen = useMediaQuery('(max-width: 767px)')
const canQuery = computed(() => checkPermi(['pms:inspection-task:query']))
const canCreate = computed(() => checkPermi(['pms:inspection-task:create']))
const statuses = [
  { value: 'PREPARING', label: '待准备' },
  { value: 'PRECHECK_PENDING', label: '待预检' },
  { value: 'EXECUTING', label: '巡检中' },
  { value: 'REPORT_PENDING', label: '待报告' },
  { value: 'ANNOTATION_PENDING', label: '待标注' },
  { value: 'TRACKING', label: '待办跟踪中' },
  { value: 'CLOSED', label: '已闭环' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'CANCELLED', label: '已取消' }
]
const statusLabel = (code: string) =>
  statuses.find((status) => status.value === code)?.label || code
const statusType = (code: string): TagProps['type'] => {
  if (code === 'CLOSED' || code === 'ARCHIVED') return 'success'
  if (code === 'CANCELLED') return 'info'
  if (code === 'TRACKING' || code === 'ANNOTATION_PENDING') return 'warning'
  return 'primary'
}
const modeLabel = (mode: string) =>
  mode === 'ONLINE' ? '在线巡检' : mode === 'OFFLINE' ? '离线巡检' : mode
const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  projectId: '',
  mode: '' as '' | CreateTaskInput['mode'],
  statusCode: ''
})
const rows = ref<TaskSummary[]>([])
const total = ref(0)
const loading = ref(false)
const listError = ref('')
let pageSequence = 0

const loadPage = async () => {
  if (!canQuery.value) return
  const current = ++pageSequence
  loading.value = true
  listError.value = ''
  try {
    const page = await InspectionApi.getTaskPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
      projectId: query.projectId || undefined,
      mode: query.mode || undefined,
      statusCode: query.statusCode || undefined
    })
    if (current !== pageSequence) return
    rows.value = page.list
    total.value = page.total
  } catch {
    if (current !== pageSequence) return
    rows.value = []
    total.value = 0
    listError.value = '巡检任务加载失败，请重新加载。'
  } finally {
    if (current === pageSequence) loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  void loadPage()
}
const resetQuery = () => {
  Object.assign(query, { pageNo: 1, keyword: '', projectId: '', mode: '', statusCode: '' })
  void loadPage()
}
const actionLabel = (status: string) =>
  ['CLOSED', 'ARCHIVED', 'CANCELLED'].includes(status)
    ? '查看'
    : status === 'PREPARING' || status === 'PRECHECK_PENDING'
      ? '办理'
      : '继续'
const openTask = (id: WireLong) =>
  router.push(`/pms/inspection/tasks/${encodeURIComponent(String(id))}`)

const { options: filterProjects, loading: filterProjectsLoading, error: filterProjectsError, search: searchFilterProjects } =
  useProjectOptions(() => query.projectId)
const { options: createProjects, loading: createProjectsLoading, error: createProjectsError, search: searchCreateProjects, reset: resetCreateProjects } =
  useProjectOptions(() => form.projectId)
const searchProjects = ({ keyword, target }: ProjectSearch) =>
  target === 'filter' ? searchFilterProjects(keyword) : searchCreateProjects(keyword)

const createVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  projectId: '',
  deviceIds: [] as string[],
  background: '',
  plannedAt: null as Date | null,
  mode: 'ONLINE' as CreateTaskInput['mode']
})
const defaultPlannedAt = ref<number>()
const { project: projectInformation, customerServiceLevel: projectCustomerServiceLevel,
  loading: projectInformationLoading, error: projectInformationError, load: loadProjectInformation } = useProjectInformation()
watch(() => form.projectId, projectId => { void loadProjectInformation(projectId) })
const rules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  deviceIds: [
    { type: 'array', required: true, min: 1, message: '请选择至少一台巡检设备', trigger: 'change' }
  ],
  background: [{ required: true, whitespace: true, message: '请填写巡检背景', trigger: 'blur' }],
  mode: [{ required: true, message: '请选择巡检方式', trigger: 'change' }]
}
const devices = ref<DeviceOption[]>([])
const devicesLoading = ref(false)
const updateDevices = (selected: DeviceOption[]) => {
  devices.value = selected
  form.deviceIds = selected.map(device => String(device.deviceId))
  formRef.value?.clearValidate('deviceIds')
}
const changeProject = () => { updateDevices([]) }
const submitting = ref(false)
const createError = ref('')
const pendingCreate = ref<{ key: string; data: CreateTaskInput } | null>(null)
const createDirty = computed(
  () =>
    !!form.projectId ||
    form.deviceIds.length > 0 ||
    !!form.background ||
    form.plannedAt?.getTime() !== defaultPlannedAt.value ||
    form.mode !== 'ONLINE'
)
const resetCreateForm = () => {
  defaultPlannedAt.value = undefined
  Object.assign(form, {
      projectId: '',
    deviceIds: [],
    background: '',
    plannedAt: null,
    mode: 'ONLINE'
  })
  resetCreateProjects()
  devices.value = []
  devicesLoading.value = false
  createProjectsLoading.value = false
  createProjectsError.value = ''
  createError.value = ''
  formRef.value?.clearValidate()
}
const openCreate = () =>
  router.push({ path: '/pms/inspection/tasks', query: { ...route.query, create: '1' } })
const returnToList = () => {
  const nextQuery = { ...route.query }
  delete nextQuery.create
  void router.push({ path: '/pms/inspection/tasks', query: nextQuery })
}
const mayLeaveCreate = async () => {
  if (submitting.value || pendingCreate.value) {
    message.warning('请先重试并确认本次创建结果，再离开此页。')
    return false
  }
  if (!createVisible.value || !createDirty.value) return true
  try {
    await message.confirm('巡检准备尚未保存，离开后将清空本次填写内容。', '确认离开')
    return true
  } catch {
    return false
  }
}
onBeforeRouteLeave(mayLeaveCreate)
onBeforeRouteUpdate(async (to) => {
  if (createVisible.value && to.query.create !== '1') return mayLeaveCreate()
  return true
})
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (submitting.value || pendingCreate.value || (createVisible.value && createDirty.value)) {
    event.preventDefault()
    event.returnValue = ''
  }
}
const definiteRejection = (error: unknown) => {
  return !inspectionFailure(error).unknown
}
const submitCreate = async () => {
  if (submitting.value || !canCreate.value) return
  submitting.value = true
  if (!pendingCreate.value) {
    if (
      !(await formRef.value?.validate().catch(() => false)) ||
      devicesLoading.value
    ) {
      submitting.value = false
      return
    }
    pendingCreate.value = {
      key: generateUUID(),
      data: {
        projectId: form.projectId,
        deviceIds: [...form.deviceIds],
        background: form.background.trim(),
        ...(form.plannedAt ? { plannedAt: form.plannedAt.getTime() } : {}),
        mode: form.mode
      }
    }
  }
  createError.value = ''
  let created: TaskSummary | undefined
  try {
    created = await InspectionApi.createTask(pendingCreate.value.data, pendingCreate.value.key)
    pendingCreate.value = null
    createVisible.value = false
    Object.assign(form, {
          projectId: '',
      deviceIds: [],
      background: '',
      plannedAt: null,
      mode: 'ONLINE'
    })
      devices.value = []
      message.success('巡检任务已创建')
    void loadPage()
  } catch (error) {
    if (definiteRejection(error)) {
      pendingCreate.value = null
      createError.value = '创建未成功，请核对填写内容、项目设备范围和权限后重试。'
    } else {
      createError.value = '暂时无法确认创建结果。已保留本次内容，请重试原请求确认结果。'
    }
  } finally {
    submitting.value = false
  }
  if (created) await openTask(created.id)
}

const applyRouteQuery = () => {
  query.statusCode =
    typeof route.query.statusCode === 'string' &&
    statuses.some((row) => row.value === route.query.statusCode)
      ? route.query.statusCode
      : ''
  const showCreate = route.query.create === '1' && canCreate.value
  if (showCreate && !createVisible.value) {
    createVisible.value = true
    if (!pendingCreate.value) {
      const now = new Date()
      defaultPlannedAt.value = now.getTime()
      form.plannedAt = now
      createError.value = ''
      void searchProjects({ keyword: '', target: 'create' })
    }
  } else if (!showCreate && createVisible.value) {
    createVisible.value = false
    resetCreateForm()
  }
}
watch(
  () => [route.query.statusCode, route.query.create],
  () => {
    if (route.path !== '/pms/inspection/tasks') return
    applyRouteQuery()
    if (!createVisible.value) search()
  }
)
onMounted(() => {
  applyRouteQuery()
  if (!createVisible.value) void loadPage()
  window.addEventListener('beforeunload', beforeUnload)
})
onActivated(() => {
  applyRouteQuery()
  window.addEventListener('beforeunload', beforeUnload)
  if (!loading.value && !createVisible.value) void loadPage()
})
onDeactivated(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  resetCreateForm()
  createVisible.value = false
  pageSequence++
  loading.value = false
  filterProjectsLoading.value = false
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  pageSequence++
  resetCreateProjects()
})
</script>

<style scoped>
.inspection-task-list {
  min-width: 0;
  background: #f5f7fa;
  font-size: 13px;
}
.inspection-card {
  margin-bottom: 16px;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}
.inspection-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f2f5;
}
.inspection-card__header h1,
.inspection-card__header h2,
.create-heading h1 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a2332;
}
.inspection-card__body {
  padding: 20px;
}
.help-text {
  margin: 0;
  color: #888;
  font-size: 12px;
  line-height: 1.7;
}
.filter-body {
  padding-bottom: 2px;
}
.filter-body :deep(.filter-project-item > .el-form-item__content) {
  flex: 0 0 240px;
  width: 240px;
}
.filter-body :deep(.filter-compact-item > .el-form-item__content) {
  flex: 0 0 160px;
  width: 160px;
}
.option-note {
  margin-left: 12px;
  color: #888;
  font-size: 12px;
}
.create-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.create-heading > span {
  color: #888;
  font-size: 12px;
}
.create-error {
  margin-bottom: 16px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor),
:deep(.el-form-item__content > .el-select) {
  width: 100%;
}
:deep(.el-form-item__label) {
  font-size: 12px;
  color: #888;
}
.section-title {
  display: inline-block;
  margin: 0 0 14px;
  padding-bottom: 8px;
  border-bottom: 2px solid #4a90e2;
  font-size: 14px;
  font-weight: 600;
  color: #1a2332;
}
.mode-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
}
.mode-options :deep(.el-radio) {
  height: auto;
  min-height: 32px;
  margin-right: 0;
  white-space: normal;
}
.mode-options :deep(.el-radio__label) {
  line-height: 1.6;
}
.mode-description {
  margin-left: 10px;
  font-size: 12px;
  color: #888;
}
.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-bottom: 8px;
}
.create-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
:deep(.el-table) {
  --el-table-header-bg-color: #fafbfc;
  --el-table-border-color: #f0f2f5;
  font-size: 13px;
}
:deep(.el-table th.el-table__cell) {
  color: #666;
  font-size: 12px;
}
:deep(.el-table .cell) {
  padding-left: 14px;
  padding-right: 14px;
}
:deep(.el-pagination) {
  padding: 12px 20px;
}
@media (max-width: 767px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .filter-body :deep(.el-form--inline .el-form-item) {
    display: flex;
    width: 100%;
    margin-right: 0;
  }
  .filter-body :deep(.filter-project-item > .el-form-item__content),
  .filter-body :deep(.filter-compact-item > .el-form-item__content) {
    flex: 1 1 0%;
    width: 0;
  }
  .inspection-card__header {
    flex-wrap: wrap;
  }
  .mode-options {
    align-items: flex-start;
    flex-direction: column;
  }
  .mode-description {
    display: block;
    margin-left: 0;
  }
}
</style>
