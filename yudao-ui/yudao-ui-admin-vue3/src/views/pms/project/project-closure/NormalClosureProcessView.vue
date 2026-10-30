<template>
  <ContentWrap aria-label="正常闭环申请详情">
    <el-alert
      title="本页只读。审批请使用 BPM 页面原有操作；流程待办不自动授予项目查看权限。"
      type="info"
      :closable="false"
    />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-skeleton v-if="loading" :rows="5" animated />
    <template v-if="detail">
      <h3>正常闭环申请</h3>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="真实闭环来源阶段">{{
          detail.application.fromStage
        }}</el-descriptions-item>
        <el-descriptions-item label="闭环类型">{{
          detail.application.closureType === 'NORMAL' ? '正常闭环' : detail.application.closureType
        }}</el-descriptions-item>
        <el-descriptions-item label="申请状态">{{
          statusLabel(detail.application.status)
        }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{
          userName(detail.application.applicantUserId)
        }}</el-descriptions-item>
        <el-descriptions-item label="服务经理">{{
          userName(detail.application.serviceManagerUserId)
        }}</el-descriptions-item>
        <el-descriptions-item label="材料审核人">{{
          userName(detail.application.reviewerUserId)
        }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{
          formatDate(detail.application.submittedAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="决定时间">{{
          detail.application.decidedAt ? formatDate(detail.application.decidedAt) : '尚无决定'
        }}</el-descriptions-item>
      </el-descriptions>
      <h3>申请冻结的条件校验</h3>
      <el-descriptions v-if="detail.snapshot" :column="1" border>
        <el-descriptions-item label="校验结果">{{
          detail.snapshot.passed ? '通过' : '未通过'
        }}</el-descriptions-item>
        <el-descriptions-item label="校验人">{{
          userName(detail.snapshot.checkedBy)
        }}</el-descriptions-item>
        <el-descriptions-item label="校验时间">{{
          formatDate(detail.snapshot.checkedAt)
        }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="displayChecks" empty-text="暂无可展示的校验项" border>
        <el-table-column prop="label" label="条件" min-width="180" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">{{ row.passed ? '满足' : '未满足' }}</template>
        </el-table-column>
        <el-table-column prop="displayReason" label="说明" min-width="180" />
      </el-table>
      <h3>历史审核记录</h3>
      <el-table
        :data="displayReviews"
        empty-text="暂无已归档审核记录；当前待办请查看 BPM 流程记录"
        border
      >
        <el-table-column prop="nodeName" label="审批节点" min-width="160" />
        <el-table-column prop="reviewerName" label="实际处理人" min-width="140" />
        <el-table-column label="处理结果" width="120"
          ><template #default="{ row }">{{
            { APPROVE: '同意', REJECT: '驳回' }[row.outcome] || row.outcome
          }}</template></el-table-column
        >
        <el-table-column prop="reason" label="审核意见" min-width="180" />
        <el-table-column label="处理时间" min-width="180"
          ><template #default="{ row }">{{ formatDate(row.reviewedAt) }}</template></el-table-column
        >
      </el-table>
      <details class="technical-evidence">
        <summary>技术详情（原始标识与冻结证据）</summary>
        <pre class="process-evidence">{{ JSON.stringify(technicalEvidence, null, 2) }}</pre>
      </details>
    </template>
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue'
import { approvalPeopleKey } from '@/views/bpm/processInstance/detail/approvalPeople'
import {
  closureConditionLabel,
  closureNodeLabel,
  closureReasonLabel
} from './normalClosurePresentation'
import { formatDate } from '@/utils/formatTime'
import {
  getNormalClosureApplication,
  type ApplicationDetail,
  type ClosureCheck
} from '@/api/pms/acceptance/normal-closure'

const props = defineProps<{ id: string }>()
const detail = ref<ApplicationDetail>()
const checks = ref<ClosureCheck[]>([])
const loading = ref(false)
const error = ref('')
const people = inject(approvalPeopleKey, undefined)
const names = computed(() => {
  const current = people?.value
  return current &&
    detail.value &&
    current.processInstanceId === detail.value.application.processInstanceId &&
    current.businessKey === detail.value.application.businessKey
    ? current.names
    : {}
})
const userName = (id: unknown) => names.value[String(id)] || '姓名未获取'
const displayChecks = computed(() =>
  checks.value.map((item) => ({
    ...item,
    label: closureConditionLabel(item.code),
    displayReason: closureReasonLabel(item.reason)
  }))
)
const displayReviews = computed(() =>
  (detail.value?.reviews || []).map((item) => ({
    ...item,
    nodeName: closureNodeLabel(item.taskDefinitionKey),
    reviewerName: userName(item.reviewerUserId)
  }))
)
const technicalEvidence = computed(() => {
  const value = detail.value
  if (!value) return undefined
  const application = value.application
  return {
    projectId: value.projectId,
    application: {
      id: application.id,
      projectId: application.projectId,
      snapshotId: application.snapshotId,
      applicantUserId: application.applicantUserId,
      serviceManagerUserId: application.serviceManagerUserId,
      reviewerUserId: application.reviewerUserId,
      processInstanceId: application.processInstanceId,
      processDefinitionId: application.processDefinitionId,
      processDefinitionKey: application.processDefinitionKey,
      businessKey: application.businessKey,
      processEvidence: application.processEvidence
    },
    snapshot: value.snapshot && {
      id: value.snapshot.id,
      checkedBy: value.snapshot.checkedBy,
      sourceDigest: value.snapshot.sourceDigest,
      checks: checks.value
    },
    reviews: value.reviews.map(
      ({ id, taskId, taskDefinitionKey, reviewerUserId, outcome, reason, reviewedAt }) => ({
        id,
        taskId,
        taskDefinitionKey,
        reviewerUserId,
        outcome,
        reason,
        reviewedAt
      })
    )
  }
})
let generation = 0
const statusLabel = (status: string) =>
  ({ IN_REVIEW: '审批中', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已取消' })[status] ||
  status

watch(
  () => props.id,
  async (businessKey) => {
    const epoch = ++generation
    detail.value = undefined
    checks.value = []
    error.value = ''
    loading.value = false
    // Keep the decimal string intact, including IDs beyond JavaScript's safe integer range.
    const applicationId =
      typeof businessKey === 'string'
        ? /^PROJECT_NORMAL_CLOSURE:([1-9][0-9]{0,18})$/.exec(businessKey)?.[1]
        : undefined
    if (!applicationId || (applicationId.length === 19 && applicationId > '9223372036854775807')) {
      error.value = '无效的正常闭环业务 Key，无法读取申请。'
      return
    }
    loading.value = true
    try {
      const value = await getNormalClosureApplication(applicationId)
      if (epoch !== generation) return
      if (
        !value ||
        String(value.application?.id) !== applicationId ||
        value.application.businessKey !== businessKey ||
        String(value.projectId) !== String(value.application.projectId)
      ) {
        throw new Error('CLOSURE_APPLICATION_IDENTITY_MISMATCH')
      }
      const evidence: unknown = value.snapshot?.evidence ? JSON.parse(value.snapshot.evidence) : []
      if (!Array.isArray(evidence)) throw new Error('CLOSURE_CHECK_EVIDENCE_INVALID')
      checks.value = evidence
      detail.value = value
    } catch {
      if (epoch === generation)
        error.value = '无法读取闭环申请，请确认项目查看与闭环查询权限，或稍后重试。'
    } finally {
      if (epoch === generation) loading.value = false
    }
  },
  { immediate: true, flush: 'sync' }
)
onBeforeUnmount(() => {
  ++generation
})
</script>
<style scoped>
.technical-evidence {
  margin-top: 16px;
}
.technical-evidence summary {
  cursor: pointer;
}
.process-evidence {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  padding: 12px;
  background: var(--el-fill-color-light);
}
</style>
