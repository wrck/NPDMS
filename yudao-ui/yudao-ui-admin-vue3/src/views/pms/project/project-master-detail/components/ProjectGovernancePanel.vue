<template>
  <ContentWrap>
    <div class="panel-header">
      <div>
        <div class="panel-title"><Icon icon="ep:warning-filled" />异常治理</div>
        <div class="panel-subtitle">服务端守卫与权限是动作提交的唯一判断依据</div>
      </div>
      <div class="panel-actions">
        <el-button
          v-if="project.lifecycleStatus === 'ACTIVE'"
          v-hasPermi="['pms:project:rollback']"
          @click="openAction('ROLLBACK')"
        >
          回退至 S0
        </el-button>
        <el-button
          v-if="project.lifecycleStatus === 'ACTIVE'"
          v-hasPermi="['pms:project:close']"
          type="danger"
          @click="openAction('EXCEPTION_CLOSE')"
        >
          异常关闭
        </el-button>
        <el-button
          v-if="project.lifecycleStatus === 'EXCEPTION_CLOSED'"
          v-hasPermi="['pms:project:reopen']"
          type="primary"
          @click="openAction('REOPEN')"
        >
          受控重开
        </el-button>
        <el-button :loading="historyLoading" @click="loadHistory">
          <Icon icon="ep:refresh" />刷新
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="project.lifecycleStatus === 'EXCEPTION_CLOSED'"
      type="warning"
      title="项目已异常关闭，当前业务事实只读；受控重开不会自动恢复成员关系或外域任务。"
      :closable="false"
      show-icon
      class="status-alert"
    />
    <el-descriptions :column="descriptionColumns" border size="small">
      <el-descriptions-item label="生命周期">
        <el-tag :type="lifecycleTagType">{{ lifecycleLabel }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="当前阶段">{{
        project.currentStage || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="指派状态">{{
        project.assignmentStatus || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="项目版本">{{ project.version ?? '-' }}</el-descriptions-item>
    </el-descriptions>

    <div class="history-header">
      <span class="history-title">治理动作历史</span>
      <span class="history-total">共 {{ historyTotal }} 条</span>
    </div>
    <div v-loading="historyLoading">
      <div class="desktop-list table-scroll">
        <el-table :data="history" border size="small" empty-text="暂无治理动作历史">
          <el-table-column prop="snapshotNo" label="快照" width="72" />
          <el-table-column label="动作" width="104">
            <template #default="{ row }">{{ actionLabel(row.operationType) }}</template>
          </el-table-column>
          <el-table-column label="状态变化" min-width="220">
            <template #default="{ row }">
              {{ stateSummary(row) }}
            </template>
          </el-table-column>
          <el-table-column prop="reasonCode" label="原因编码" min-width="120" />
          <el-table-column
            prop="reasonDetail"
            label="原因说明"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column prop="operatorUserId" label="操作者" width="92" />
          <el-table-column label="操作时间" width="168">
            <template #default="{ row }">{{ formatDateTime(row.operatedAt) }}</template>
          </el-table-column>
          <el-table-column label="详情" width="72" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="showHistory(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="mobile-list">
        <el-card v-for="row in history" :key="row.id" shadow="never" class="history-card">
          <div class="card-title">
            <strong>{{ actionLabel(row.operationType) }}</strong>
            <span>{{ formatDateTime(row.operatedAt) }}</span>
          </div>
          <div class="card-line">{{ stateSummary(row) }}</div>
          <div class="card-line">原因：{{ row.reasonCode }} / {{ row.reasonDetail }}</div>
          <el-button link type="primary" @click="showHistory(row)">查看完整记录</el-button>
        </el-card>
        <el-empty v-if="!history.length" description="暂无治理动作历史" />
      </div>
      <Pagination
        v-model:page="historyQuery.pageNo"
        v-model:limit="historyQuery.pageSize"
        :total="historyTotal"
        @pagination="loadHistory"
      />
    </div>
  </ContentWrap>

  <Dialog
    v-model="actionVisible"
    :title="`${actionLabel(currentAction)}项目`"
    width="min(760px, calc(100vw - 24px))"
    max-height="70vh"
    scroll
  >
    <div v-loading="guardLoading">
      <template v-if="guard">
        <el-alert
          :type="guard.allowed ? 'success' : 'error'"
          :title="
            guard.allowed
              ? '守卫检查通过，可填写并提交动作'
              : `守卫阻断：共 ${guard.blockerTotal} 项`
          "
          :closable="false"
          show-icon
          class="guard-alert"
        />
        <el-descriptions :column="descriptionColumns" border size="small">
          <el-descriptions-item label="检查项目版本">{{
            guard.projectVersion
          }}</el-descriptions-item>
          <el-descriptions-item label="完整树版本">{{ guard.treeVersion }}</el-descriptions-item>
          <el-descriptions-item label="检查时间">{{
            formatDateTime(guard.checkedAt)
          }}</el-descriptions-item>
          <el-descriptions-item label="提供方事实"
            >{{ guard.providerFacts.length }} 个</el-descriptions-item
          >
        </el-descriptions>

        <div v-if="guard.blockers.length" class="blockers">
          <el-table :data="guard.blockers" border size="small">
            <el-table-column prop="provider" label="提供方" width="120" />
            <el-table-column prop="objectType" label="对象类型" width="110" />
            <el-table-column prop="objectId" label="编号" min-width="100" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="summary" label="处理提示" min-width="180" />
          </el-table>
          <Pagination
            v-model:page="guardQuery.pageNo"
            v-model:limit="guardQuery.pageSize"
            :total="guard.blockerTotal"
            @pagination="loadGuard"
          />
        </div>

        <el-form
          v-if="guard.allowed"
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="action-form"
        >
          <el-form-item label="原因编码" prop="reasonCode">
            <el-select v-model="form.reasonCode" placeholder="请选择已配置原因" filterable>
              <el-option
                v-for="item in reasonOptions"
                :key="item.value"
                :label="item.label"
                :value="String(item.value)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="原因说明" prop="reasonDetail">
            <el-input
              v-model="form.reasonDetail"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <el-form-item
            v-if="currentAction === 'ROLLBACK'"
            label="重新指派要求"
            prop="reassignmentRequirement"
          >
            <el-input
              v-model="form.reassignmentRequirement"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <template v-if="currentAction === 'EXCEPTION_CLOSE'">
            <el-form-item label="业务依据" prop="businessBasis">
              <el-input
                v-model="form.businessBasis"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="遗留事项">
              <div class="legacy-list">
                <div v-for="(item, index) in form.legacyItems" :key="index" class="legacy-item">
                  <el-input v-model="item.type" placeholder="类型" />
                  <el-input v-model="item.summary" placeholder="事项摘要" />
                  <el-input v-model="item.owner" placeholder="责任人" />
                  <el-input v-model="item.status" placeholder="状态" />
                  <el-button type="danger" link @click="removeLegacyItem(index)">删除</el-button>
                </div>
                <el-button @click="addLegacyItem"><Icon icon="ep:plus" />添加遗留事项</el-button>
                <span v-if="!form.legacyItems.length" class="empty-hint">已明确无遗留事项</span>
              </div>
            </el-form-item>
          </template>
          <el-form-item
            v-if="currentAction === 'REOPEN'"
            label="异常关闭快照"
            prop="exceptionCloseSnapshotId"
          >
            <el-select
              v-model="form.exceptionCloseSnapshotId"
              placeholder="请选择最近有效异常关闭快照"
            >
              <el-option
                v-for="item in exceptionCloseSnapshots"
                :key="item.id"
                :label="`#${item.snapshotNo} · ${formatDateTime(item.operatedAt)} · ${item.reasonCode}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </template>
    </div>
    <template #footer>
      <el-button @click="actionVisible = false">取消</el-button>
      <el-button v-if="guard?.allowed" type="primary" :loading="submitting" @click="submitAction">
        确认{{ actionLabel(currentAction) }}
      </el-button>
    </template>
  </Dialog>

  <Dialog v-model="historyVisible" title="治理动作历史详情" width="min(760px, calc(100vw - 24px))">
    <el-descriptions v-if="selectedHistory" :column="descriptionColumns" border>
      <el-descriptions-item label="动作">{{
        actionLabel(selectedHistory.operationType)
      }}</el-descriptions-item>
      <el-descriptions-item label="快照号">{{ selectedHistory.snapshotNo }}</el-descriptions-item>
      <el-descriptions-item label="阶段变化"
        >{{ selectedHistory.beforeStage }} → {{ selectedHistory.afterStage }}</el-descriptions-item
      >
      <el-descriptions-item label="生命周期"
        >{{ selectedHistory.beforeLifecycleStatus }} →
        {{ selectedHistory.afterLifecycleStatus }}</el-descriptions-item
      >
      <el-descriptions-item label="指派状态"
        >{{ selectedHistory.beforeAssignmentStatus }} →
        {{ selectedHistory.afterAssignmentStatus }}</el-descriptions-item
      >
      <el-descriptions-item label="完整树版本">{{
        selectedHistory.treeVersion ?? '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="原因编码">{{ selectedHistory.reasonCode }}</el-descriptions-item>
      <el-descriptions-item label="操作者">{{
        selectedHistory.operatorUserId
      }}</el-descriptions-item>
      <el-descriptions-item label="原因说明" :span="descriptionColumns">{{
        selectedHistory.reasonDetail
      }}</el-descriptions-item>
      <el-descriptions-item
        v-if="selectedHistory.reassignmentRequirement"
        label="重新指派要求"
        :span="descriptionColumns"
        >{{ selectedHistory.reassignmentRequirement }}</el-descriptions-item
      >
      <el-descriptions-item
        v-if="selectedHistory.businessBasis"
        label="业务依据"
        :span="descriptionColumns"
        >{{ selectedHistory.businessBasis }}</el-descriptions-item
      >
      <el-descriptions-item label="遗留事项" :span="descriptionColumns">{{
        legacySummary(selectedHistory.legacyItemsJson)
      }}</el-descriptions-item>
      <el-descriptions-item label="operationId" :span="descriptionColumns">{{
        selectedHistory.operationId
      }}</el-descriptions-item>
      <el-descriptions-item label="操作时间" :span="descriptionColumns">{{
        formatDateTime(selectedHistory.operatedAt)
      }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectsApi from '@/api/pms/project/projects'
import type {
  ProjectGovernanceAction,
  ProjectGovernanceGuardVO,
  ProjectGovernanceHistoryVO,
  ProjectGovernanceLegacyItem,
  ProjectMasterVO
} from '@/api/pms/project/projects'

defineOptions({ name: 'ProjectGovernancePanel' })

const props = defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ updated: [] }>()
const message = useMessage()
const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))
const reasonOptions = computed(() => getStrDictOptions(DICT_TYPE.PMS_PROJECT_GOVERNANCE_REASON))
const lifecycleLabel = computed(
  () =>
    ({ ACTIVE: '进行中', NORMAL_CLOSED: '正常闭环', EXCEPTION_CLOSED: '异常关闭' })[
      props.project.lifecycleStatus || ''
    ] ||
    props.project.lifecycleStatus ||
    '未知'
)
const lifecycleTagType = computed(() =>
  props.project.lifecycleStatus === 'EXCEPTION_CLOSED'
    ? 'warning'
    : props.project.lifecycleStatus === 'NORMAL_CLOSED'
      ? 'success'
      : 'primary'
)

const historyLoading = ref(false)
const history = ref<ProjectGovernanceHistoryVO[]>([])
const historyTotal = ref(0)
const historyQuery = reactive({ pageNo: 1, pageSize: 10 })
const historyVisible = ref(false)
const selectedHistory = ref<ProjectGovernanceHistoryVO>()

const loadHistory = async () => {
  if (!props.project.id) return
  historyLoading.value = true
  try {
    const data = await ProjectsApi.getProjectGovernanceHistory(props.project.id, historyQuery)
    history.value = data.list
    historyTotal.value = data.total
  } finally {
    historyLoading.value = false
  }
}

const actionVisible = ref(false)
const currentAction = ref<ProjectGovernanceAction>('ROLLBACK')
const guardLoading = ref(false)
const submitting = ref(false)
const guard = ref<ProjectGovernanceGuardVO>()
const guardQuery = reactive({ pageNo: 1, pageSize: 10 })
const formRef = ref()
const form = reactive({
  reasonCode: '',
  reasonDetail: '',
  reassignmentRequirement: '',
  businessBasis: '',
  legacyItems: [] as ProjectGovernanceLegacyItem[],
  exceptionCloseSnapshotId: undefined as number | undefined
})
const idempotencyKey = ref('')
const rules = computed(() => ({
  reasonCode: [{ required: true, message: '请选择原因编码' }],
  reasonDetail: [{ required: true, message: '请输入原因说明' }],
  reassignmentRequirement:
    currentAction.value === 'ROLLBACK' ? [{ required: true, message: '请输入重新指派要求' }] : [],
  businessBasis:
    currentAction.value === 'EXCEPTION_CLOSE'
      ? [{ required: true, message: '请输入业务依据' }]
      : [],
  exceptionCloseSnapshotId:
    currentAction.value === 'REOPEN' ? [{ required: true, message: '请选择异常关闭快照' }] : []
}))
const exceptionCloseSnapshots = computed(() =>
  history.value.filter((item) => item.operationType === 'EXCEPTION_CLOSE')
)

const resetForm = () => {
  Object.assign(form, {
    reasonCode: '',
    reasonDetail: '',
    reassignmentRequirement: '',
    businessBasis: '',
    legacyItems: [],
    exceptionCloseSnapshotId: undefined
  })
  idempotencyKey.value = ''
}
const openAction = async (action: ProjectGovernanceAction) => {
  currentAction.value = action
  guardQuery.pageNo = 1
  guard.value = undefined
  resetForm()
  actionVisible.value = true
  await loadGuard()
  if (action === 'REOPEN') {
    if (!history.value.length) await loadHistory()
    form.exceptionCloseSnapshotId = exceptionCloseSnapshots.value[0]?.id
  }
}
const loadGuard = async () => {
  if (!props.project.id) return
  guardLoading.value = true
  try {
    guard.value = await ProjectsApi.getProjectGovernanceGuard(
      props.project.id,
      currentAction.value,
      guardQuery.pageNo,
      guardQuery.pageSize
    )
  } finally {
    guardLoading.value = false
  }
}
const addLegacyItem = () => form.legacyItems.push({ type: '', summary: '', owner: '', status: '' })
const removeLegacyItem = (index: number) => form.legacyItems.splice(index, 1)

const submitAction = async () => {
  if (!props.project.id || !guard.value?.allowed || !guard.value.guardToken) return
  await formRef.value?.validate()
  if (form.legacyItems.some((item) => !item.type || !item.summary || !item.owner || !item.status)) {
    message.warning('请完整填写每一项遗留事项')
    return
  }
  idempotencyKey.value ||= crypto.randomUUID()
  submitting.value = true
  try {
    if (currentAction.value === 'ROLLBACK') {
      await ProjectsApi.rollbackProject(
        props.project.id,
        {
          guardToken: guard.value.guardToken,
          reasonCode: form.reasonCode,
          reasonDetail: form.reasonDetail,
          reassignmentRequirement: form.reassignmentRequirement
        },
        guard.value.projectVersion,
        idempotencyKey.value
      )
    } else if (currentAction.value === 'EXCEPTION_CLOSE') {
      await ProjectsApi.exceptionCloseProject(
        props.project.id,
        {
          guardToken: guard.value.guardToken,
          reasonCode: form.reasonCode,
          reasonDetail: form.reasonDetail,
          businessBasis: form.businessBasis,
          legacyItems: form.legacyItems
        },
        guard.value.projectVersion,
        idempotencyKey.value
      )
    } else {
      await ProjectsApi.reopenProject(
        props.project.id,
        {
          reasonCode: form.reasonCode,
          reasonDetail: form.reasonDetail,
          exceptionCloseSnapshotId: form.exceptionCloseSnapshotId!
        },
        guard.value.projectVersion,
        idempotencyKey.value
      )
    }
    message.success(`${actionLabel(currentAction.value)}成功`)
    actionVisible.value = false
    await loadHistory()
    emit('updated')
  } finally {
    submitting.value = false
  }
}

const actionLabel = (action: ProjectGovernanceAction) =>
  ({ ROLLBACK: '回退', EXCEPTION_CLOSE: '异常关闭', REOPEN: '受控重开' })[action]
const stateSummary = (row: ProjectGovernanceHistoryVO) =>
  `${row.beforeLifecycleStatus}/${row.beforeStage}/${row.beforeAssignmentStatus} → ${row.afterLifecycleStatus}/${row.afterStage}/${row.afterAssignmentStatus}`
const formatDateTime = (value?: string) => (value ? formatDate(value) : '-')
const legacySummary = (value?: string | null) => {
  if (!value) return '无遗留事项'
  try {
    const items = JSON.parse(value) as ProjectGovernanceLegacyItem[]
    return items.length
      ? items
          .map((item) => `${item.type}：${item.summary}（${item.owner}/${item.status}）`)
          .join('；')
      : '无遗留事项'
  } catch {
    return '遗留事项数据不可解析'
  }
}
const showHistory = (row: ProjectGovernanceHistoryVO) => {
  selectedHistory.value = row
  historyVisible.value = true
}

watch(
  () => props.project.id,
  () => {
    historyQuery.pageNo = 1
    loadHistory()
  }
)
watch(
  form,
  () => {
    if (!submitting.value) idempotencyKey.value = ''
  },
  { deep: true }
)
onMounted(loadHistory)
</script>

<style scoped lang="scss">
.panel-header,
.panel-actions,
.card-title {
  display: flex;
  align-items: center;
}

.panel-header {
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-title,
.history-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
}

.panel-subtitle,
.history-total,
.empty-hint,
.card-title span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.panel-actions {
  flex-wrap: wrap;
  gap: 8px;
}

.status-alert,
.guard-alert {
  margin-bottom: 12px;
}

.history-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 20px 0 10px;
}

.table-scroll {
  overflow-x: auto;
}

.mobile-list {
  display: none;
}

.history-card {
  margin-bottom: 10px;
}

.card-title {
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.card-line {
  margin-bottom: 6px;
  overflow-wrap: anywhere;
  color: var(--el-text-color-regular);
}

.blockers,
.action-form {
  margin-top: 16px;
}

.legacy-list {
  width: 100%;
}

.legacy-item {
  display: grid;
  grid-template-columns: 1fr 2fr 1fr 1fr auto;
  gap: 8px;
  margin-bottom: 8px;
}

.empty-hint {
  margin-left: 10px;
}

@media (width <= 1199px) {
  .desktop-list {
    display: none;
  }

  .mobile-list {
    display: block;
  }
}

@media (width <= 767px) {
  .panel-header,
  .panel-actions,
  .legacy-item {
    align-items: stretch;
  }

  .panel-header {
    flex-direction: column;
  }

  .panel-actions {
    display: grid;
    grid-template-columns: 1fr;
    width: 100%;
  }

  .legacy-item {
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
  }
}
</style>
