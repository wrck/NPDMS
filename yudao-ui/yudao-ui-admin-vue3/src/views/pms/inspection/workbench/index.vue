<template>
  <main class="inspection-page inspection-workbench" data-testid="inspection-workbench">
    <header class="page-heading">
      <h1>巡检工作台</h1>
      <div class="page-actions">
        <el-button v-if="canCreate && !canQuery" type="primary" @click="openCreate">
          <Icon icon="ep:plus" />新增巡检任务
        </el-button>
        <el-button v-if="canQuery" @click="openTaskList()">任务列表</el-button>
        <el-button v-if="canReadRules" @click="router.push('/pms/inspection/rules')"
          >巡检规则</el-button
        >
        <el-button v-if="canReadWorkbench" :loading="loading" @click="loadWorkbench"
          ><Icon icon="ep:refresh" />刷新</el-button
        >
      </div>
    </header>
    <el-alert
      v-if="!canReadWorkbench"
      title="没有巡检任务或本人待办的查询权限"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="load-error"
    >
      <template #default
        ><el-button link type="primary" :disabled="loading" @click="loadWorkbench"
          >重新加载</el-button
        ></template
      >
    </el-alert>
    <section
      v-if="canQuery"
      class="summary-grid"
      aria-label="巡检任务状态统计"
      :aria-busy="loading"
    >
      <button
        v-for="group in countGroups"
        :key="group.label"
        type="button"
        class="summary-card"
        :aria-label="`${group.label}：${data ? countTasks(group.codes) : '尚未获取'}，${group.description}，查看任务列表`"
        @click="openTaskList(group.filter)"
      >
        <strong class="summary-count" :style="{ color: group.color }">{{
          data ? countTasks(group.codes) : '—'
        }}</strong>
        <span class="summary-label">{{ group.label }}</span>
        <span class="summary-description">{{ group.description }}</span>
      </button>
    </section>
    <template v-if="canQuery">
      <section v-for="section in taskSections" :key="section.key" class="inspection-card">
        <header class="inspection-card__header">
          <h2><Icon :icon="section.icon" />{{ section.title }}</h2>
          <el-button
            v-if="section.key === 'created' && canCreate"
            type="primary"
            @click="openCreate"
            ><Icon icon="ep:plus" />新增巡检任务</el-button
          >
          <el-button
            v-else-if="section.key === 'pending'"
            link
            type="primary"
            @click="openTaskList()"
            >查看全部</el-button
          >
        </header>
        <el-table
          v-loading="loading"
          :data="section.rows"
          :row-key="(task: TaskSummary) => String(task.id)"
          :empty-text="emptyText(section.empty)"
        >
          <el-table-column prop="taskCode" label="编号" min-width="180" />
          <el-table-column prop="taskName" label="任务名称" min-width="190" show-overflow-tooltip />
          <el-table-column
            prop="projectName"
            label="项目名称"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column v-if="section.key === 'pending'" label="任务负责人" min-width="110"
            ><template #default="{ row }">{{ row.ownerName || '—' }}</template></el-table-column
          >
          <el-table-column v-else label="创建人" min-width="100"
            ><template #default="{ row }">{{ row.creatorName || '—' }}</template></el-table-column
          >
          <el-table-column
            v-if="section.key === 'pending'"
            prop="customerName"
            label="客户"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column label="创建时间" min-width="165"
            ><template #default="{ row }">{{
              row.createTime ? formatDate(row.createTime) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="当前状态" min-width="120"
            ><template #default="{ row }"
              ><el-tag :type="taskStatusType(row.statusCode)" size="small">{{
                taskStatusLabel(row.statusCode)
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="操作" width="90" :fixed="smallScreen ? false : 'right'"
            ><template #default="{ row }"
              ><el-button
                :type="section.key === 'pending' ? 'primary' : 'default'"
                size="small"
                @click="openTask(row.id)"
                >{{ actionLabel(row.statusCode) }}</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </section>
    </template>
    <section v-if="canReadIssues" class="inspection-card issue-section">
      <el-collapse>
        <el-collapse-item name="issues">
          <template #title
            ><span class="issue-heading"
              ><Icon icon="ep:list" />本人问题待办<span class="issue-count"
                >{{ data ? data.myIssues.length : '—' }} 项</span
              ></span
            ></template
          >
          <el-table
            v-loading="loading"
            :data="data?.myIssues || []"
            :row-key="(issue: IssueSummary) => String(issue.id)"
            :empty-text="emptyText('暂无本人负责的巡检问题待办')"
          >
            <el-table-column prop="taskCode" label="任务编号" min-width="180" />
            <el-table-column
              prop="taskName"
              label="任务名称"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column
              prop="description"
              label="问题描述"
              min-width="240"
              show-overflow-tooltip
            />
            <el-table-column label="严重级别" width="100"
              ><template #default="{ row }">{{
                severityLabel(row.severityCode)
              }}</template></el-table-column
            >
            <el-table-column label="问题状态" width="120"
              ><template #default="{ row }"
                ><el-tag
                  size="small"
                  :type="row.statusCode === 'CLOSURE_PENDING' ? 'warning' : 'primary'"
                  >{{ issueStatusLabel(row.statusCode) }}</el-tag
                ></template
              ></el-table-column
            >
            <el-table-column label="计划完成时间" min-width="165"
              ><template #default="{ row }">{{
                row.dueAt ? formatDate(row.dueAt) : '未设置'
              }}</template></el-table-column
            >
            <el-table-column label="操作" width="90" :fixed="smallScreen ? false : 'right'"
              ><template #default="{ row }"
                ><el-button link type="primary" @click="openIssue(row)">{{
                  row.statusCode === 'CLOSED' ? '查看' : '办理'
                }}</el-button></template
              ></el-table-column
            >
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import type { TagProps } from 'element-plus'
import * as InspectionApi from '@/api/pms/inspection/tasks'
import type { TaskSummary, WireLong } from '@/api/pms/inspection/tasks'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import { getStrDictOptions } from '@/utils/dict'
import '../shared/inspection.css'

defineOptions({ name: 'PmsInspectionWorkbench' })
type WorkbenchData = Awaited<ReturnType<typeof InspectionApi.getTaskWorkbench>>
type IssueSummary = WorkbenchData['myIssues'][number]
const router = useRouter()
const smallScreen = useMediaQuery('(max-width: 767px)')
const canQuery = computed(() => checkPermi(['pms:inspection-task:query']))
const canCreate = computed(() => checkPermi(['pms:inspection-task:create']))
const canReadRules = computed(() => checkPermi(['pms:inspection-rule:query']))
const canReadIssues = computed(() => checkPermi(['pms:service-issue:query']))
const canReadWorkbench = computed(() => canQuery.value || canReadIssues.value)
const data = ref<WorkbenchData | null>(null)
const loading = ref(false)
const error = ref('')
let sequence = 0
const countGroups = [
  {
    label: '待办巡检任务',
    codes: ['PREPARING', 'PRECHECK_PENDING'],
    description: '待准备、待预检',
    color: '#1677ff'
  },
  {
    label: '已完成巡检',
    codes: ['CLOSED', 'ARCHIVED'],
    description: '已闭环、已归档',
    color: '#52c41a'
  },
  {
    label: '执行中',
    codes: ['EXECUTING', 'REPORT_PENDING', 'ANNOTATION_PENDING'],
    description: '巡检执行、报告生成与标注',
    color: '#faad14'
  },
  {
    label: '待闭环',
    codes: ['TRACKING'],
    description: '问题待办跟踪中',
    color: '#ff4d4f',
    filter: 'TRACKING'
  }
]
const taskSections = computed(() => [
  {
    key: 'pending',
    title: '巡检任务待办',
    icon: 'ep:tickets',
    rows: data.value?.pendingTasks || [],
    empty: '暂无待办巡检任务'
  },
  {
    key: 'created',
    title: '自建巡检任务',
    icon: 'ep:edit-pen',
    rows: data.value?.createdTasks || [],
    empty: '暂无本人创建的巡检任务'
  }
])
const actionLabel = (status: string) =>
  ['CLOSED', 'ARCHIVED', 'CANCELLED'].includes(status)
    ? '查看'
    : status === 'PREPARING' || status === 'PRECHECK_PENDING'
      ? '办理'
      : '继续'
const countTasks = (codes: string[]) =>
  data.value?.taskCounts.reduce(
    (sum, item) => (codes.includes(item.statusCode) ? sum + item.count : sum),
    0
  ) ?? 0
const taskStatuses: Record<string, string> = {
  PREPARING: '待准备',
  PRECHECK_PENDING: '待预检',
  EXECUTING: '巡检中',
  REPORT_PENDING: '待报告',
  ANNOTATION_PENDING: '待标注',
  TRACKING: '待办跟踪中',
  CLOSED: '已闭环',
  ARCHIVED: '已归档',
  CANCELLED: '已取消'
}
const issueStatuses: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  CLOSURE_PENDING: '待确认闭环',
  CLOSED: '已闭环'
}
const taskStatusLabel = (code: string) => taskStatuses[code] || code
const issueStatusLabel = (code: string) => issueStatuses[code] || code
const taskStatusType = (code: string): TagProps['type'] => {
  if (code === 'CLOSED' || code === 'ARCHIVED') return 'success'
  if (code === 'CANCELLED') return 'info'
  if (code === 'TRACKING' || code === 'ANNOTATION_PENDING') return 'warning'
  return 'primary'
}
const severities = computed(() => getStrDictOptions('pms_inspection_rule_severity'))
const severityLabel = (code: string) =>
  severities.value.find((item) => item.value === code)?.label || code || '未提供'
const emptyText = (loadedText: string) =>
  error.value ? '加载失败，请重新加载' : data.value ? loadedText : '正在加载'
const openTaskList = (statusCode?: string) =>
  router.push({ path: '/pms/inspection/tasks', query: statusCode ? { statusCode } : {} })
const openCreate = () => router.push({ path: '/pms/inspection/tasks', query: { create: '1' } })
const openTask = (id: WireLong) =>
  router.push(`/pms/inspection/tasks/${encodeURIComponent(String(id))}`)
const openIssue = (issue: IssueSummary) =>
  router.push({
    path: `/pms/inspection/tasks/${encodeURIComponent(String(issue.taskId))}`,
    query: { issueId: String(issue.id) }
  })

const loadWorkbench = async () => {
  if (!canReadWorkbench.value) return
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await InspectionApi.getTaskWorkbench()
    if (current !== sequence) return
    if (
      !result ||
      !Array.isArray(result.taskCounts) ||
      !Array.isArray(result.pendingTasks) ||
      !Array.isArray(result.createdTasks) ||
      !Array.isArray(result.myIssues)
    )
      throw new Error('工作台返回内容不完整')
    data.value = result
  } catch {
    if (current !== sequence) return
    data.value = null
    error.value = '巡检工作台加载失败，任务统计和待办尚未获取，请重新加载。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
onMounted(() => {
  void loadWorkbench()
})
onActivated(() => {
  if (!loading.value) void loadWorkbench()
})
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
.inspection-workbench {
  min-width: 0;
  background: #f5f7fa;
  font-size: 13px;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.page-heading h1 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a2332;
}
.page-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.page-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.load-error {
  margin-bottom: 16px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.summary-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 16px 20px;
  border: 0;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
  background: #fff;
  text-align: left;
  font: inherit;
  cursor: pointer;
}
.summary-card:hover {
  background: #f8faff;
}
.summary-card:focus-visible {
  outline: 2px solid #4a90e2;
  outline-offset: 2px;
}
.summary-count {
  font-size: 28px;
  line-height: 1.3;
  font-weight: 700;
}
.summary-label {
  color: #666;
  font-size: 12px;
}
.summary-description {
  font-size: 12px;
  color: #888;
  line-height: 1.5;
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
  gap: 12px;
  margin: 0;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f2f5;
}
.inspection-card__header h2,
.issue-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a2332;
}
.issue-count {
  margin-left: 8px;
  font-size: 12px;
  color: #888;
  font-weight: 400;
}
.issue-section :deep(.el-collapse) {
  border: 0;
}
.issue-section :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 50px;
  padding: 0 20px;
  border-bottom-color: #f0f2f5;
}
.issue-section :deep(.el-collapse-item__wrap) {
  border: 0;
}
.issue-section :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}
:deep(.el-table) {
  --el-table-header-bg-color: #fafbfc;
  --el-table-border-color: #f0f2f5;
  font-size: 13px;
}
:deep(.el-table th.el-table__cell) {
  font-size: 12px;
  color: #666;
}
:deep(.el-table .cell) {
  padding-left: 14px;
  padding-right: 14px;
}
@media (max-width: 767px) {
  .page-heading {
    flex-wrap: wrap;
  }
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
}
@media (max-width: 359px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
