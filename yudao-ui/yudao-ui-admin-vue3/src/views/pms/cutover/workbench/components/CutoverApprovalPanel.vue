<template>
  <section v-loading="loading" class="approval-panel" aria-labelledby="approval-heading">
    <el-alert title="审批页面引用方案提交时的固定版本。审批人根据项目、调研、风险、等级及方案进行五项合理性评审。" type="info" :closable="false" show-icon />
    <el-alert v-if="errorText" :title="errorText" type="error" show-icon :closable="false" />
    <template v-else-if="view?.viewMode === 'FULL'">
      <section class="cut-card">
        <header class="cut-card__header"><h3 id="approval-heading"><Icon icon="ep:location" />项目信息</h3><small>引用割接等级确认时的项目信息</small></header>
        <div class="cut-card__body cut-form-grid">
          <div class="cut-form-field"><label>项目名称</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.project.projectName }}</span></div>
          <div class="cut-form-field"><label>项目编号</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.project.projectCode }}</span></div>
          <div class="cut-form-field"><label>设备序列号</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.plan.sourceSnapshot.devices.map(device => device.serialNumber).join('、') }}</span></div>
          <div class="cut-form-field"><label>办事处</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.project.departmentName || '—' }}</span></div>
        </div>
      </section>
      <section class="cut-card">
        <header class="cut-card__header"><h3><Icon icon="ep:data-analysis" />采集信息分析</h3><small>引用调研及风险考察</small></header>
        <div class="cut-card__body">
          <div class="cut-form-grid">
            <div class="cut-form-field"><label>割接类型</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.collectionAnalysis.cutoverType }}</span></div>
            <div class="cut-form-field"><label>高可靠性组网模式</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.collectionAnalysis.networkMode || '未配置' }}</span></div>
            <div class="cut-form-field"><label>设备类型</label><span class="cut-form-value cut-form-value--plain">{{ deviceTypeSummary || '—' }}</span></div>
            <div class="cut-form-field"><label>割接操作时间</label><span class="cut-form-value cut-form-value--plain">{{ formatWireDateTime(view.sourceSnapshot.collectionAnalysis.scheduledTime) }}</span></div>
          </div>
          <CutoverLeadTimeComplianceCard v-if="view.leadTimeCompliance" :compliance="view.leadTimeCompliance" />
          <p v-if="view.grade === 'D'" class="cut-help simple-flow-note">D级简易割接跳过调研及风险考察，本次审批直接引用简易方案。</p>
          <template v-for="group in frozenGroups" :key="group.label">
            <h4 class="cut-section-title">{{ group.label }}</h4>
            <el-table :data="group.items" empty-text="本次割接无此类检查记录">
              <el-table-column prop="itemName" label="调研／风险项" min-width="260" />
              <el-table-column label="结果" min-width="460"><template #default="{ row }"><CutoverFrozenAnswer :snapshot="row.answerSnapshot" /><PmsFileVersionReferenceList v-if="manualFile(row)" owner-context="CUT" object-type="CUTOVER_CHECKLIST_ITEM" :object-id="String(row.checklistItemId)" purpose-code="MANUAL_EVIDENCE" :reference-key="manualFile(row)!.referenceKey" :artifact-id="manualFile(row)!.artifactId" :version-no="manualFile(row)!.versionNo" /></template></el-table-column>
              <el-table-column label="事实说明" min-width="180"><template #default="{ row }">{{ row.factDescription || '—' }}</template></el-table-column>
            </el-table>
          </template>
        </div>
      </section>
      <section class="cut-card">
        <header class="cut-card__header"><h3><Icon icon="ep:data-analysis" />割接等级</h3><small>引用割接等级确认</small></header>
        <div class="cut-card__body cut-form-grid grade-facts">
          <div class="cut-form-field"><label>业务重要等级</label><span class="cut-form-value cut-form-value--plain">{{ levelLabels[view.sourceSnapshot.assessment.businessImportanceLevel] || view.sourceSnapshot.assessment.businessImportanceLevel }}</span></div>
          <div class="cut-form-field"><label>操作复杂度等级</label><span class="cut-form-value cut-form-value--plain">{{ levelLabels[view.sourceSnapshot.assessment.operationComplexityLevel] || view.sourceSnapshot.assessment.operationComplexityLevel }}</span></div>
          <div class="cut-form-field"><label>隐患风险等级</label><span class="cut-form-value cut-form-value--plain">{{ levelLabels[view.sourceSnapshot.assessment.hiddenRiskLevel] || view.sourceSnapshot.assessment.hiddenRiskLevel }}</span></div>
          <div class="cut-form-field"><label>备件是否申请</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.assessment.sparePartApplied ? '是' : '否' }}</span></div>
          <div class="cut-form-field"><label>客户服务等级</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.assessment.customerServiceLevelCode === 'NOT_CONFIGURED' ? '未配置' : view.sourceSnapshot.assessment.customerServiceLevelCode }}</span></div>
          <div class="cut-form-field"><label>人工确定割接等级</label><span class="grade-badge">{{ view.sourceSnapshot.assessment.manualGrade }}级</span></div>
        </div>
      </section>
      <section class="cut-card">
        <header class="cut-card__header"><h3><Icon icon="ep:document" />割接方案</h3><small>第 {{ view.planRevisionNo }} 版</small></header>
        <div class="cut-card__body"><CutoverPlanEditor v-if="writableContent" :model-value="writableContent" :source-snapshot="view.sourceSnapshot.plan.sourceSnapshot" :task-id="view.taskId" :editable="false" /></div>
      </section>
      <section v-if="currentNode && hasDecisionAction" class="cut-card">
        <header class="cut-card__header"><h3><Icon icon="ep:finished" />割接评审</h3><small>当前节点：{{ nodeLabels[currentNode.nodeCode] }}<template v-if="currentNode.candidateRoleCode"> · {{ roleLabels[currentNode.candidateRoleCode] }}角色审批</template></small></header>
        <div class="cut-card__body"><CutoverApprovalDecisionForm :key="String(view.approvalInstanceId) + ':' + currentNode.nodeNo"
          :identity="String(view.approvalInstanceId) + ':' + currentNode.nodeNo" :node-code="currentNode.nodeCode"
          :allowed-actions="props.readonly ? [] : view.allowedActions" :busy="writing" @decide="decide" /></div>
      </section>
      <section class="cut-card">
        <header class="cut-card__header"><h3><Icon icon="ep:clock" />审批记录</h3><div><el-tag size="small" :type="view.status === 'APPROVED' ? 'success' : view.status === 'REJECTED' ? 'danger' : 'warning'">{{ statusLabels[view.status] }}</el-tag><el-button link data-testid="refresh-approval" @click="load">刷新</el-button></div></header>
        <el-table :data="view.nodes" row-key="nodeId">
          <el-table-column label="审批节点" width="180"><template #default="{ row }">{{ row.nodeNo }}. {{ nodeLabels[row.nodeCode] }}</template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }">{{ nodeStatusLabels[row.status] }}</template></el-table-column>
          <el-table-column label="审批人／角色" min-width="165"><template #default="{ row }"><span>{{ approvalParticipant(row) }}</span></template></el-table-column>
          <el-table-column label="五项评审及反馈" min-width="350"><template #default="{ row }"><span>{{ row.feedback || '—' }}</span><ul v-if="row.reviewItems.length" class="review-history"><li v-for="item in row.reviewItems" :key="item.itemCode">{{ reviewLabels[item.itemCode] }}：{{ item.decision === 'YES' ? '合理' : '不合理（' + item.unreasonableReason + '）' }}</li></ul><p v-if="row.assessmentReview?.reason" class="review-history">等级复核：{{ row.assessmentReview.reason }}</p></template></el-table-column>
          <el-table-column label="审批时间" min-width="165"><template #default="{ row }">{{ formatWireDateTime(row.decisionAt) }}</template></el-table-column>
        </el-table>
      </section>
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
      :key="`${view.approvalInstanceId}:${view.approvalVersion}`"
      :view="view"
      :busy="writing"
      @reassign="reassign"
    />
    <el-empty v-else-if="!loading" description="暂无审批数据" />
  </section>
</template>

<script setup lang="ts">
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverApi from '@/api/pms/cutover/workbench'
import { getSimpleUserList } from '@/api/system/user'
import type {
  CutoverApprovalChecklistResultSnapshot,
  CutoverApprovalDecisionRequest,
  CutoverApprovalNodeCode,
  CutoverApprovalNode,
  CutoverApprovalReviewItem,
  CutoverApprovalView,
  WritableCutoverPlanContent
} from '@/api/pms/cutover/workbench'
import {
  createCutoverApprovalWriteCoordinator,
  formatWireDateTime
} from '../cutoverTaskInteraction'
import CutoverApprovalDecisionForm from './CutoverApprovalDecisionForm.vue'
import CutoverLeadTimeComplianceCard from './CutoverLeadTimeComplianceCard.vue'
import CutoverApprovalReassignmentPanel from './CutoverApprovalReassignmentPanel.vue'
import CutoverPlanEditor from './CutoverPlanEditor.vue'
import CutoverFrozenAnswer from './CutoverFrozenAnswer.vue'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'

const props = defineProps<{ taskId?: CutoverApi.WireLong; approvalId?: CutoverApi.WireLong; readonly?: boolean; completeAfterDecision?: boolean }>()
const emit = defineEmits<{ changed: []; decided: [] }>()
const message = useMessage()
const loading = ref(false)
const writing = ref(false)
const errorText = ref('')
const view = ref<CutoverApprovalView | null>(null)
const userNames = ref<Record<string, string>>({})
const coordinator = createCutoverApprovalWriteCoordinator()
const levelLabels: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高' }
const deviceTypeSummary = computed(() => view.value?.viewMode === 'FULL' ? [...new Set(view.value.sourceSnapshot.plan.sourceSnapshot.devices.map(device => device.deviceTypeCode).filter(Boolean))].join('、') : '')
const frozenGroups = computed(() => {
  const current = view.value
  return current?.viewMode === 'FULL' ? [
    { label: '风险考察项', items: current.sourceSnapshot.riskItems },
    { label: '业务调研项', items: current.sourceSnapshot.businessSurveyItems }
  ].filter(group => group.items.length || current.grade !== 'D') : []
})
const manualFile = (item: CutoverApprovalChecklistResultSnapshot) => {
  if (item.resultSourceCode !== 'MANUAL') return null
  try { const data = JSON.parse(item.answerSnapshot); return data.file || data } catch { return null }
}
const currentNode = computed(() => {
  const current = view.value
  return current?.viewMode === 'FULL'
    ? current.nodes.find((node) => node.nodeNo === current.currentNodeNo) || null
    : null
})
const hasDecisionAction = computed(
  () =>
    !props.readonly && view.value?.viewMode === 'FULL' &&
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
const roleLabels = { probSupporter: '技术支持人员', probRD: '研发人员' }
const approvalParticipant = (node: CutoverApprovalNode) => {
  const role = node.candidateRoleCode ? roleLabels[node.candidateRoleCode] : null
  if (node.currentApproverUserId != null) {
    const person = userNames.value[String(node.currentApproverUserId)] || `用户 ${node.currentApproverUserId}`
    return role ? `${person}（${role}）` : person
  }
  return role ? `${role} · 角色共享待办` : '待确认'
}
const reviewLabels: Record<CutoverApprovalReviewItem['itemCode'], string> = {
  PREPARATION: '准备工作',
  BUSINESS_TEST: '业务测试',
  EXECUTION: '割接操作',
  ROLLBACK: '回退步骤',
  OTHER: '其他事项'
}

const requireTaskId = () => {
  if (props.taskId) return props.taskId
  if (view.value && 'taskId' in view.value) return view.value.taskId
  throw new Error('当前审批不提供任务操作')
}
const fetchApproval = async () => {
  try {
    view.value = props.approvalId ? await CutoverApi.getCutoverApprovalInstance(props.approvalId) : await CutoverApi.getCutoverApproval(requireTaskId())
    errorText.value = ''
  } catch (error) {
    view.value = null
    errorText.value = '当前审批数据暂时无法读取，请返回待办列表刷新'
    throw error
  }
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
const refreshAfterDecision = async (confirmed: boolean, complete: boolean) => {
  if (confirmed && complete) {
    view.value = null
    emit('decided')
    return
  }
  await refreshAll()
}
const decide = async (request: CutoverApprovalDecisionRequest) => {
  if (view.value?.viewMode !== 'FULL') return
  writing.value = true
  const current = view.value
  let confirmed = false
  const complete = !!props.completeAfterDecision || current.nodes.some(node => node.nodeNo === current.currentNodeNo && !!node.candidateRoleCode)
  try {
    const outcome = await coordinator.run(
      `DECIDE:${current.approvalInstanceId}:${request.action}`,
      async (key) => {
        const result = await (request.action === 'APPROVE'
          ? CutoverApi.approveCutoverApproval(
              requireTaskId(),
              current.approvalVersion,
              current.taskVersion,
              request,
              key
            )
          : CutoverApi.rejectCutoverApproval(
              requireTaskId(),
              current.approvalVersion,
              current.taskVersion,
              request,
              key
            ))
        confirmed = true
        return result
      },
      () => refreshAfterDecision(confirmed, complete)
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
        CutoverApi.reassignCutoverApproval(requireTaskId(), current.approvalVersion, request, key),
      refreshAll
    )
    if (outcome.writeCalled)
      message.success(outcome.refreshSucceeded ? '审批节点已改派' : '改派已提交，页面刷新待重试')
  } finally {
    writing.value = false
  }
}
watch(
  () => [props.taskId, props.approvalId],
  async () => {
    view.value = null
    errorText.value = ''
    coordinator.reset()
    await nextTick()
    await load()
  }
)
onMounted(async () => {
  await load()
  if (view.value?.viewMode !== 'FULL') return
  try {
    const users = await getSimpleUserList()
    userNames.value = Object.fromEntries(users.map(user => [String(user.id), user.nickname]))
  } catch {
    userNames.value = {}
  }
})
</script>

<style scoped>
.approval-panel { min-width: 0; }
.grade-facts { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.grade-badge { display: inline-block; padding: 3px 12px; border-radius: 4px; background: #e6f4ff; color: #1677ff; font-size: 15px; font-weight: 600; }
.review-history { margin: 8px 0 0; padding-left: 16px; color: #888; font-size: 12px; }
.simple-flow-note { margin-top: 16px; }
@media (width <= 767px) { .grade-facts { grid-template-columns: 1fr; } }
</style>
