<template>
  <section v-loading="loading" class="duration-approval">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-else-if="change">
      <h3>工期变更审批内容</h3>
      <dl class="duration-facts">
        <div><dt>变更编号</dt><dd>#{{ change.changeId }}</dd></div>
        <div><dt>当前状态</dt><dd><el-tag :type="statusType(change.status)" size="small">{{ statusLabel(change.status) }}</el-tag></dd></div>
        <div><dt>提交时间</dt><dd>{{ formatTime(change.submittedAt) }}</dd></div>
        <div><dt>变更依据版本</dt><dd>V{{ change.candidateRevision?.revisionNo ?? '-' }}</dd></div>
      </dl>
      <h4>拟生效工期</h4>
      <dl class="duration-facts">
        <div>
          <dt>工期口径</dt>
          <dd>{{ basisLabel(change.candidateRevision?.calculationBasis) }}</dd>
        </div>
        <div v-if="change.candidateRevision?.startDate">
          <dt>开始日期</dt><dd>{{ change.candidateRevision.startDate }}</dd>
        </div>
        <div v-if="change.candidateRevision?.endDate">
          <dt>结束日期</dt><dd>{{ change.candidateRevision.endDate }}</dd>
        </div>
        <div v-if="change.candidateRevision?.durationDays">
          <dt>总工期</dt><dd>{{ change.candidateRevision.durationDays }} 天</dd>
        </div>
      </dl>
      <h4>变更原因</h4>
      <p class="duration-reason">{{ change.reasonDetail || '无' }}</p>
      <template v-if="change.customerEvidenceFileId && change.customerEvidenceReferenceKey">
        <h4>变更依据附件</h4>
        <PmsFileReferenceList
          owner-context="SOL"
          object-type="CONSTRUCTION_PLAN_CHANGE"
          :object-id="String(change.changeId)"
          purpose-code="CUSTOMER_DELAY_EVIDENCE"
          :reference-key="change.customerEvidenceReferenceKey"
          :artifact-id="change.customerEvidenceFileId"
          :version-no="change.customerEvidenceFileVersion"
          :editable="false"
        />
      </template>
      <template v-if="change.approvalOpinion">
        <h4>审批意见</h4>
        <p class="duration-reason">{{ change.approvalOpinion }}</p>
      </template>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { formatDate } from '@/utils/formatTime'
import {
  getChangeById,
  type ConstructionPlanChangeVO,
  type DurationCalculationBasis,
  type DurationChangeStatus
} from '@/api/pms/engineering/construction-plan'

// BPM businessKey is the change identity; the approver reads it without knowing the plan id.
// Long change ids exceed Number.MAX_SAFE_INTEGER, so keep the raw string and validate as digits.
const props = defineProps<{ id: string | number }>()
const change = ref<ConstructionPlanChangeVO>()
const loading = ref(false)
const error = ref('')
let sequence = 0
watch(() => props.id, async (id) => {
  const current = ++sequence
  change.value = undefined
  error.value = ''
  const changeId = String(id ?? '')
  if (!/^\d+$/.test(changeId) || changeId === '0') { error.value = '变更编号无效'; return }
  loading.value = true
  try {
    const value = await getChangeById(changeId)
    if (current === sequence) change.value = value
  } catch {
    if (current === sequence) error.value = '无法读取本次审批的变更内容，请确认项目权限后重试'
  } finally {
    if (current === sequence) loading.value = false
  }
}, { immediate: true })

const formatTime = (value?: string) => (value ? formatDate(value) : '-')
const basisLabel = (value?: DurationCalculationBasis) =>
  value === 'DATE_RANGE' ? '起止日期口径' : '起点 + 天数口径'
const statusLabel = (status: DurationChangeStatus) => ({
  DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回', WITHDRAWN: '已撤回'
})[status]
const statusType = (status: DurationChangeStatus) => ({
  DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'success',
  REJECTED: 'danger', WITHDRAWN: 'info'
})[status] as 'info' | 'warning' | 'success' | 'danger'
</script>

<style scoped>
.duration-approval { min-width: 0; }
.duration-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 16px; }
.duration-facts dt { color: var(--el-text-color-secondary); }
.duration-facts dd { margin: 8px 0 0; }
.duration-reason { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
