<template>
  <section v-loading="loading" class="approval-panel" aria-labelledby="approval-heading">
    <div class="panel-heading">
      <div
        ><h2 id="approval-heading">P5 分级审批</h2
        ><p>审批依据来自方案提交时冻结的 CUT 快照。</p></div
      >
      <el-button data-testid="refresh-approval" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="errorText" :title="errorText" type="error" show-icon :closable="false" />
    <template v-else-if="view?.viewMode === 'FULL'">
      <el-descriptions :column="columns" border>
        <el-descriptions-item label="审批等级">{{ view.grade }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">{{
          statusLabels[view.status]
        }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{
          view.sourceSnapshot.project.projectName
        }}</el-descriptions-item>
        <el-descriptions-item label="计划时间">{{
          formatWireDateTime(view.sourceSnapshot.collectionAnalysis.scheduledTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="割接类型">{{
          view.sourceSnapshot.collectionAnalysis.cutoverType
        }}</el-descriptions-item>
        <el-descriptions-item label="组网模式">{{
          view.sourceSnapshot.collectionAnalysis.networkMode || '未配置'
        }}</el-descriptions-item>
        <el-descriptions-item label="方案版本"
          >第 {{ view.planRevisionNo }} 版</el-descriptions-item
        >
        <el-descriptions-item label="人工等级">{{
          view.sourceSnapshot.assessment.manualGrade
        }}</el-descriptions-item>
      </el-descriptions>
      <ol class="approval-route" aria-label="审批路径">
        <li
          v-for="node in view.nodes"
          :key="String(node.nodeId)"
          :class="`is-${node.status.toLowerCase()}`"
        >
          <div
            ><strong>{{ node.nodeNo }}. {{ nodeLabels[node.nodeCode] }}</strong
            ><el-tag size="small">{{ nodeStatusLabels[node.status] }}</el-tag></div
          >
          <small v-if="node.decisionAt">{{ formatWireDateTime(node.decisionAt) }}</small>
          <p v-if="node.feedback">{{ node.feedback }}</p>
          <ul v-if="node.reviewItems.length"
            ><li v-for="item in node.reviewItems" :key="item.itemCode">
              {{ reviewLabels[item.itemCode] }}：{{
                item.decision === 'YES' ? '合理' : `不合理（${item.unreasonableReason}）`
              }}
            </li></ul
          >
        </li>
      </ol>
      <div class="frozen-evidence">
        <h3>冻结审批依据</h3>
        <p
          >风险检查 {{ view.sourceSnapshot.riskItems.length }} 项，业务调研
          {{ view.sourceSnapshot.businessSurveyItems.length }} 项。</p
        >
        <CutoverPlanEditor
          v-if="writableContent"
          :model-value="writableContent"
          :source-snapshot="view.sourceSnapshot.plan.sourceSnapshot"
          :task-id="view.taskId"
          :editable="false"
        />
      </div>
      <CutoverApprovalDecisionForm
        v-if="currentNode && hasDecisionAction"
        :node-code="currentNode.nodeCode"
        :allowed-actions="view.allowedActions"
        :busy="writing"
        @decide="decide"
      />
    </template>
    <template v-else-if="view?.viewMode === 'FINAL_RESULT_ONLY'">
      <el-result
        :icon="view.status === 'APPROVED' ? 'success' : 'warning'"
        :title="view.status === 'APPROVED' ? '审批已通过' : '审批已驳回'"
        :sub-title="view.rejectionReason || `决定时间：${formatWireDateTime(view.decisionAt)}`"
      />
    </template>
    <CutoverApprovalReassignmentPanel
      v-else-if="view?.viewMode === 'REASSIGNMENT_ONLY'"
      :view="view"
      :busy="writing"
      @reassign="reassign"
    />
    <el-empty v-else-if="!loading" description="暂无审批数据" />
  </section>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverApi from '@/api/pms/cutover/cutover-task'
import type {
  CutoverApprovalDecisionRequest,
  CutoverApprovalNodeCode,
  CutoverApprovalReviewItem,
  CutoverApprovalView,
  WritableCutoverPlanContent
} from '@/api/pms/cutover/cutover-task'
import {
  createCutoverApprovalWriteCoordinator,
  formatWireDateTime
} from '../cutoverTaskInteraction'
import CutoverApprovalDecisionForm from './CutoverApprovalDecisionForm.vue'
import CutoverApprovalReassignmentPanel from './CutoverApprovalReassignmentPanel.vue'
import CutoverPlanEditor from './CutoverPlanEditor.vue'

const props = defineProps<{ taskId: CutoverApi.WireLong }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const { width } = useWindowSize()
const loading = ref(false)
const writing = ref(false)
const errorText = ref('')
const view = ref<CutoverApprovalView | null>(null)
const coordinator = createCutoverApprovalWriteCoordinator()
const columns = computed(() => (width.value < 768 ? 1 : 2))
const currentNode = computed(() => {
  const current = view.value
  return current?.viewMode === 'FULL'
    ? current.nodes.find((node) => node.nodeNo === current.currentNodeNo) || null
    : null
})
const hasDecisionAction = computed(
  () =>
    view.value?.viewMode === 'FULL' &&
    view.value.allowedActions.some((action) => action === 'APPROVE' || action === 'REJECT')
)
const writableContent = computed(() => {
  if (view.value?.viewMode !== 'FULL') return null
  const content = view.value.sourceSnapshot.plan.content
  return content.editMode === 'LEGACY_READ_ONLY' ? null : (content as WritableCutoverPlanContent)
})
const statusLabels = {
  PENDING: '审批中',
  PAUSED_SOURCE_INVALIDATED: '来源失效暂停',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}
const nodeStatusLabels = {
  WAITING: '等待',
  PENDING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消'
}
const nodeLabels: Record<CutoverApprovalNodeCode, string> = {
  INITIATOR: '发起人',
  SERVICE_MANAGER: '服务经理',
  SECOND_LINE: '二线审批',
  RND: '研发审批'
}
const reviewLabels: Record<CutoverApprovalReviewItem['itemCode'], string> = {
  PREPARATION: '准备工作',
  BUSINESS_TEST: '业务测试',
  EXECUTION: '割接操作',
  ROLLBACK: '回退步骤',
  OTHER: '其他事项'
}

const fetchApproval = async () => {
  view.value = await CutoverApi.getCutoverApproval(props.taskId)
  errorText.value = ''
}
const load = async () => {
  loading.value = true
  errorText.value = ''
  try {
    await fetchApproval()
  } catch {
    errorText.value = '审批数据暂时无法加载，请稍后重试'
  } finally {
    loading.value = false
  }
}
const refreshAll = async () => {
  await fetchApproval()
  emit('changed')
}
const decide = async (request: CutoverApprovalDecisionRequest) => {
  if (view.value?.viewMode !== 'FULL') return
  writing.value = true
  const current = view.value
  try {
    const outcome = await coordinator.run(
      `DECIDE:${current.approvalInstanceId}:${request.action}`,
      (key) =>
        request.action === 'APPROVE'
          ? CutoverApi.approveCutoverApproval(
              props.taskId,
              current.approvalVersion,
              current.taskVersion,
              request,
              key
            )
          : CutoverApi.rejectCutoverApproval(
              props.taskId,
              current.approvalVersion,
              current.taskVersion,
              request,
              key
            ),
      refreshAll
    )
    if (outcome.writeCalled)
      message.success(outcome.refreshSucceeded ? '审批决定已提交' : '审批已提交，页面刷新待重试')
  } finally {
    writing.value = false
  }
}
const reassign = async (request: {
  nodeNo: number
  newApproverUserId: CutoverApi.WireLong
  reason: string
}) => {
  if (view.value?.viewMode !== 'REASSIGNMENT_ONLY') return
  writing.value = true
  const current = view.value
  try {
    const outcome = await coordinator.run(
      `REASSIGN:${current.approvalInstanceId}:${request.nodeNo}`,
      (key) =>
        CutoverApi.reassignCutoverApproval(props.taskId, current.approvalVersion, request, key),
      refreshAll
    )
    if (outcome.writeCalled)
      message.success(outcome.refreshSucceeded ? '审批节点已改派' : '改派已提交，页面刷新待重试')
  } finally {
    writing.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.approval-panel {
  display: grid;
  gap: 18px;
  min-height: 240px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel-heading h2,
.frozen-evidence h3 {
  margin: 0;
}

.panel-heading p,
.frozen-evidence p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.approval-route {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 0;
  list-style: none;
}

.approval-route > li {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.approval-route > li > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.approval-route small {
  color: var(--el-text-color-secondary);
}

.approval-route ul {
  padding-left: 18px;
}

.approval-route .is-pending {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.frozen-evidence {
  display: grid;
  gap: 14px;
}

@media (width <= 1023px) {
  .approval-route {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 767px) {
  .panel-heading {
    flex-direction: column;
  }

  .approval-route {
    grid-template-columns: 1fr;
  }
}
</style>
