<template>
  <ContentWrap>
    <el-form :model="listQuery" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="所属项目">
        <PmsEntitySelect
          v-model="listQuery.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-220px"
          @change="load"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="listQuery.status" clearable placeholder="全部" class="!w-160px" @change="load">
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load" v-hasPermi="['pms:imp-stage-plan:query']">
          <Icon icon="ep:search" />查询
        </el-button>
        <el-button
          type="primary"
          plain
          @click="openCreate"
          v-hasPermi="['pms:imp-stage-plan:create']"
        >
          <Icon icon="ep:plus" />新建计划草稿
        </el-button>
        <el-button plain @click="openOverdue" v-hasPermi="['pms:imp-stage-plan:query']">
          <Icon icon="ep:alarm-clock" />超期统计
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="batches" empty-text="暂无阶段施工计划；选择项目后新建计划草稿">
      <el-table-column prop="id" label="批次" width="90" />
      <el-table-column prop="projectId" label="项目" width="200">
        <template #default="{ row }">
          {{ projectNameMap[row.projectId] ?? row.projectId }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="工期基线窗口" min-width="200">
        <template #default="{ row }">
          <span v-if="row.baselineStart && row.baselineEnd">
            {{ row.baselineStart }} ~ {{ row.baselineEnd }}
          </span>
          <span v-else class="text-gray-400">未设置工期基线</span>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="170" />
      <el-table-column prop="effectiveAt" label="生效时间" width="170" />
      <el-table-column prop="rejectReason" label="驳回原因" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">明细</el-button>
          <el-button
            v-if="row.status === 0 || row.status === 3"
            link
            type="primary"
            @click="openSubmit(row)"
            v-hasPermi="['pms:imp-stage-plan:submit']"
          >
            提交审核
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <!-- 阶段计划明细 -->
  <el-dialog v-model="detailVisible" :title="detailTitle" width="960px" @closed="detailBatch = null">
    <template v-if="detailBatch">
      <el-alert
        v-if="detailBatch.status === 3"
        type="warning"
        :closable="false"
        show-icon
        :title="`审批未通过：${detailBatch.rejectReason ?? '审批被驳回'}；可调整后重新提交`"
        class="mb-10px"
      />
      <el-alert
        v-if="detailBatch.status === 2"
        type="success"
        :closable="false"
        show-icon
        title="该批次已生效，计划时间已回写项目阶段计划"
        class="mb-10px"
      />
      <el-alert
        v-if="tightSchedule"
        type="warning"
        :closable="false"
        show-icon
        title="工期紧张：项目总工期不足 3 个月，请尽快落实计划并确认发货；CRM 发货提醒通道未接入，需线下跟进"
        class="mb-10px"
      />
      <el-descriptions :column="2" border size="small" class="mb-10px" title="项目基础信息（导入，不可调整）">
        <el-descriptions-item label="本版本工期窗口">
          {{ detailBatch.baselineStart || '未推算' }} 至 {{ detailBatch.baselineEnd || '未推算' }}
        </el-descriptions-item>
        <el-descriptions-item label="工期要求（项目结束时间）">
          {{ projectInfo?.projectEndDate || '未登记（由工勘工期要求带入）' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table :data="detailBatch.items" empty-text="暂无阶段明细">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="detailBatch.tasks?.filter(task => task.stageCode === row.phaseCode) || []" row-key="taskId" size="small">
              <el-table-column prop="name" label="阶段任务" min-width="180" />
              <el-table-column label="计划开始" width="170"><template #default="{ row: task }"><el-date-picker v-if="editable" v-model="task.planStart" type="date" value-format="YYYY-MM-DD" class="!w-140px" aria-label="任务计划开始" /><span v-else>{{ task.planStart || '未安排' }}</span></template></el-table-column>
              <el-table-column label="计划结束" width="170"><template #default="{ row: task }"><el-date-picker v-if="editable" v-model="task.planEnd" type="date" value-format="YYYY-MM-DD" class="!w-140px" aria-label="任务计划结束" /><span v-else>{{ task.planEnd || '未安排' }}</span></template></el-table-column>
              <el-table-column prop="acceptanceTime" label="计划验收时间" min-width="140" />
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="phaseName" label="项目阶段" min-width="140" />
        <el-table-column label="建议计划时间" min-width="200">
          <template #default="{ row }">
            <span v-if="row.suggestedStart && row.suggestedEnd">
              {{ row.suggestedStart }} ~ {{ row.suggestedEnd }}
            </span>
            <span v-else class="text-gray-400">—</span>
          </template>
        </el-table-column>
        <el-table-column label="计划开始时间" width="170">
          <template #default="{ row }">
            <el-date-picker
              v-if="editable"
              v-model="row.planStart"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="计划开始"
              class="!w-140px"
            />
            <span v-else>{{ row.planStart ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划结束时间" width="170">
          <template #default="{ row }">
            <el-date-picker
              v-if="editable"
              v-model="row.planEnd"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="计划结束"
              class="!w-140px"
            />
            <span v-else>{{ row.planEnd ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">
            <el-input v-if="editable" v-model="row.remark" placeholder="备注" />
            <span v-else>{{ row.remark ?? '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-form v-if="editable" label-position="top" class="mt-10px">
        <el-form-item label="调整原因" required><el-input v-model="detailBatch.remark" type="textarea" placeholder="说明阶段和任务日期的调整依据" /></el-form-item>
      </el-form>
      <div v-if="editable" class="mt-10px flex gap-10px">
        <el-button @click="handleAutoEstimate" :loading="acting">
          <Icon icon="ep:magic-stick" />自动推算
        </el-button>
        <el-button type="primary" plain @click="handleSaveItems" :loading="acting">
          <Icon icon="ep:check" />保存调整
        </el-button>
        <el-tooltip
          content="CRM 发货提醒通道未接入，无法发起提醒"
          placement="top"
        >
          <el-button disabled><Icon icon="ep:bell" />发起CRM发货提醒（未接入）</el-button>
        </el-tooltip>
        <span class="ml-auto self-center text-12px text-gray-400">
          提示：阶段间不得重叠{{ detailBatch.baselineStart ? '，且须落在工期基线窗口内' : '' }}
        </span>
      </div>
    </template>
  </el-dialog>

  <!-- 超期统计（PLN-03） -->
  <el-dialog v-model="overdueVisible" title="超期统计（仅生效计划版本）" width="820px">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="超期为生效计划中未完成阶段超过计划完成时间的计算结果，不允许人工修改；草稿与已驳回批次不参与判定"
      class="mb-10px"
    />
    <el-row :gutter="16" class="mb-10px">
      <el-col :span="12">
        <el-statistic title="超期项目数（按项目去重）" :value="overdueSummary.overdueProjectCount ?? 0" />
      </el-col>
      <el-col :span="12">
        <el-statistic title="超期阶段数" :value="overdueSummary.overdueStageCount ?? 0" />
      </el-col>
    </el-row>
    <el-table :data="overdueRows" empty-text="当前无超期阶段" max-height="420">
      <el-table-column label="项目" min-width="180">
        <template #default="{ row }">
          {{ projectNameMap[row.projectId] ?? row.projectId }}
        </template>
      </el-table-column>
      <el-table-column prop="phaseName" label="超期阶段" min-width="140" />
      <el-table-column prop="planEnd" label="计划完成时间" width="130" />
      <el-table-column label="超期天数" width="110">
        <template #default="{ row }">
          <span class="text-red-500 font-bold">{{ row.overdueDays }} 天</span>
        </template>
      </el-table-column>
      <el-table-column label="阶段状态" width="100">
        <template #default="{ row }">
          {{ row.phaseStatus === 1 ? '进行中' : '未开始' }}
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <!-- 提交审核 -->
  <el-dialog v-model="submitVisible" title="提交审核" width="480px">
    <el-form label-width="90px">
      <el-form-item label="审批人" required>
        <PmsEntitySelect
          v-model="submitForm.approverUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择服务经理审批人"
          class="!w-300px"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!submitForm.approverUserId" :loading="acting" @click="handleSubmit">
        发起审批
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
defineOptions({ name: 'PmsImpStagePlan' })

// 项目详情页嵌入模式：传入 project-id 固定项目并隐藏项目选择；独立路由页行为不变
const props = defineProps<{ projectId?: number }>()

import * as StagePlanApi from '@/api/pms/engineering/stage-plan'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'

const message = useMessage()

const listQuery = reactive<{ projectId?: number; status?: number; pageNo: number; pageSize: number }>({
  projectId: props.projectId,
  status: undefined,
  pageNo: 1,
  pageSize: 100
})
const loading = ref(false)
const acting = ref(false)
const batches = ref<StagePlanApi.StagePlanBatchVO[]>([])
const projectNameMap = ref<Record<number, string>>({})

const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 1, label: '审批中' },
  { value: 2, label: '已生效' },
  { value: 3, label: '已驳回' }
]
const statusLabel = (status?: number) =>
  statusOptions.find((opt) => opt.value === status)?.label ?? `未知(${status})`
const statusTagType = (status?: number) =>
  status === 2 ? 'success' : status === 3 ? 'danger' : status === 1 ? 'warning' : 'info'

const loadProjectNames = async () => {
  if (Object.keys(projectNameMap.value).length > 0) return
  const data = await ProjectApi.getProjectPage({ pageNo: 1, pageSize: 100 } as any)
  projectNameMap.value = Object.fromEntries(
    (data.list ?? []).map((p: any) => [p.id, p.projectName ?? p.name ?? p.id])
  )
}

const load = async () => {
  loading.value = true
  try {
    const data = await StagePlanApi.getStagePlanBatchPage(listQuery as any)
    batches.value = data.list ?? []
    await loadProjectNames()
  } finally {
    loading.value = false
  }
}

const openCreate = async () => {
  if (!listQuery.projectId) {
    message.warning('请先选择项目')
    return
  }
  await message.confirm('将按项目阶段事实生成阶段计划明细，是否继续？')
  acting.value = true
  try {
    await StagePlanApi.createStagePlanBatch(listQuery.projectId!)
    message.success('计划草稿已创建')
    await load()
  } finally {
    acting.value = false
  }
}

const detailVisible = ref(false)
const detailBatch = ref<StagePlanApi.StagePlanBatchVO | null>(null)
const projectInfo = ref<ProjectApi.ProjectMasterVO | null>(null)
const detailTitle = computed(() =>
  detailBatch.value ? `阶段施工计划明细 · ${projectNameMap.value[detailBatch.value.projectId ?? -1] ?? detailBatch.value.projectId}` : ''
)
const editable = computed(() => detailBatch.value?.status === 0 || detailBatch.value?.status === 3)
// PLN-02 工期紧张：生效计划版本的项目总工期不足 3 个月时页面提示（正式预警以生效计划版本为准）
const tightSchedule = computed(() => {
  const batch = detailBatch.value
  if (!batch || batch.status !== 2 || !batch.baselineStart || !batch.baselineEnd) return false
  const start = new Date(batch.baselineStart).getTime()
  const end = new Date(batch.baselineEnd).getTime()
  return end - start < 90 * 24 * 3600 * 1000
})

const openDetail = async (id: number) => {
  const data = await StagePlanApi.getStagePlanBatch(id)
  detailBatch.value = data
  detailVisible.value = true
  // Demo 3.1 第一步：工期要求读项目结束日期（工勘带入）；合同验收时间无 PMS 导入来源，界面如实标注
  if (data.projectId && projectInfo.value?.id !== data.projectId)
    projectInfo.value = await ProjectApi.getProject(data.projectId).catch(() => null)
}

const handleAutoEstimate = async () => {
  if (!detailBatch.value?.id) return
  acting.value = true
  try {
    detailBatch.value = await StagePlanApi.autoEstimateStagePlanBatch(detailBatch.value.id)
    message.success('已按工期基线占比推算计划时间')
    await load()
  } finally {
    acting.value = false
  }
}

const handleSaveItems = async () => {
  if (!detailBatch.value?.id) return
  if (!detailBatch.value.remark?.trim()) return message.warning('请填写调整原因')
  const items = (detailBatch.value.items ?? []).map((item) => ({
    id: item.id!,
    planStart: item.planStart!,
    planEnd: item.planEnd!,
    remark: item.remark
  }))
  acting.value = true
  try {
    detailBatch.value = await StagePlanApi.updateStagePlanItems({
      id: detailBatch.value.id,
      version: detailBatch.value.version!,
      remark: detailBatch.value.remark,
      tasks: detailBatch.value.tasks,
      items
    })
    message.success('调整已保存')
    await load()
  } finally {
    acting.value = false
  }
}

const overdueVisible = ref(false)
const overdueRows = ref<StagePlanApi.StagePlanOverdueRowVO[]>([])
const overdueSummary = ref<StagePlanApi.StagePlanOverdueSummaryVO>({})
const openOverdue = async () => {
  const [rows, summary] = await Promise.all([
    StagePlanApi.getOverdueStages(listQuery.projectId),
    StagePlanApi.getOverdueSummary(listQuery.projectId)
  ])
  overdueRows.value = rows
  overdueSummary.value = summary
  overdueVisible.value = true
}

const submitVisible = ref(false)
const submitForm = reactive<{ batchId?: number; approverUserId?: number }>({})
const openSubmit = (row: StagePlanApi.StagePlanBatchVO) => {
  submitForm.batchId = row.id
  submitForm.approverUserId = undefined
  submitVisible.value = true
}
const handleSubmit = async () => {
  if (!submitForm.batchId || !submitForm.approverUserId) return
  acting.value = true
  try {
    await StagePlanApi.submitStagePlanBatch(submitForm.batchId, submitForm.approverUserId)
    message.success('已发起审批')
    submitVisible.value = false
    detailVisible.value = false
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>
