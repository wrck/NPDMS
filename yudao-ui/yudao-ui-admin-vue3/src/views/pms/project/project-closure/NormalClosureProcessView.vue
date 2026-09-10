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
        <el-descriptions-item label="申请 ID">{{ detail.application.id }}</el-descriptions-item>
        <el-descriptions-item label="项目 ID">{{ detail.projectId }}</el-descriptions-item>
        <el-descriptions-item label="真实闭环来源阶段">{{
          detail.application.fromStage
        }}</el-descriptions-item>
        <el-descriptions-item label="闭环类型">{{
          detail.application.closureType
        }}</el-descriptions-item>
        <el-descriptions-item label="申请状态">{{
          statusLabel(detail.application.status)
        }}</el-descriptions-item>
        <el-descriptions-item label="申请人 ID">{{
          detail.application.applicantUserId
        }}</el-descriptions-item>
        <el-descriptions-item label="服务经理候选人 ID">{{
          detail.application.serviceManagerUserId
        }}</el-descriptions-item>
        <el-descriptions-item label="材料审核候选人 ID">{{
          detail.application.reviewerUserId
        }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{
          detail.application.submittedAt
        }}</el-descriptions-item>
        <el-descriptions-item label="决定时间">{{
          detail.application.decidedAt || '尚无决定'
        }}</el-descriptions-item>
        <el-descriptions-item label="实际流程实例 ID">{{
          detail.application.processInstanceId
        }}</el-descriptions-item>
        <el-descriptions-item label="实际流程定义 ID">{{
          detail.application.processDefinitionId
        }}</el-descriptions-item>
        <el-descriptions-item label="流程定义 Key">{{
          detail.application.processDefinitionKey
        }}</el-descriptions-item>
        <el-descriptions-item label="业务 Key">{{
          detail.application.businessKey
        }}</el-descriptions-item>
      </el-descriptions>
      <h3>申请冻结的条件校验</h3>
      <el-descriptions v-if="detail.snapshot" :column="1" border>
        <el-descriptions-item label="快照 ID">{{ detail.snapshot.id }}</el-descriptions-item>
        <el-descriptions-item label="校验结果">{{
          detail.snapshot.passed ? '通过' : '未通过'
        }}</el-descriptions-item>
        <el-descriptions-item label="校验人 ID">{{
          detail.snapshot.checkedBy
        }}</el-descriptions-item>
        <el-descriptions-item label="校验时间">{{
          detail.snapshot.checkedAt
        }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="checks" empty-text="暂无可展示的校验项" border>
        <el-table-column prop="code" label="条件" min-width="180" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">{{ row.passed ? '满足' : '未满足' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" />
        <el-table-column prop="subjectId" label="来源对象 ID" min-width="180" />
      </el-table>
      <h3>实际流程与候选人冻结证据</h3>
      <pre class="process-evidence">{{ detail.application.processEvidence || '暂无流程证据' }}</pre>
      <h3>历史审核记录</h3>
      <el-table
        :data="detail.reviews"
        empty-text="暂无已归档审核记录；当前待办请查看 BPM 流程记录"
        border
      >
        <el-table-column prop="taskDefinitionKey" label="实际节点 Key" min-width="180" />
        <el-table-column prop="taskId" label="任务 ID" min-width="180" />
        <el-table-column prop="reviewerUserId" label="实际处理人 ID" min-width="160" />
        <el-table-column prop="outcome" label="处理结果" width="120" />
        <el-table-column prop="reason" label="审核意见" min-width="180" />
        <el-table-column prop="reviewedAt" label="处理时间" min-width="180" />
      </el-table>
    </template>
  </ContentWrap>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import {
  getNormalClosureApplication,
  type ApplicationDetail,
  type ClosureCheck
} from '@/api/pms/project/normal-closure'

const props = defineProps<{ id: string }>()
const detail = ref<ApplicationDetail>()
const checks = ref<ClosureCheck[]>([])
const loading = ref(false)
const error = ref('')
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
.process-evidence {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  padding: 12px;
  background: var(--el-fill-color-light);
}
</style>
