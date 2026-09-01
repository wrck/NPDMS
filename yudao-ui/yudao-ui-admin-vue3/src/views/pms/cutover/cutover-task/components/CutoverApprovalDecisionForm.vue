<template>
  <section class="decision-form" aria-labelledby="approval-decision-heading">
    <h3 id="approval-decision-heading">本节点审批意见</h3>
    <div class="review-list">
      <div v-for="item in reviewItems" :key="item.itemCode" class="review-row">
        <label :for="`review-${item.itemCode}`">{{ reviewLabels[item.itemCode] }}</label>
        <el-radio-group
          :id="`review-${item.itemCode}`"
          v-model="item.decision"
          :data-testid="`review-decision-${item.itemCode}`"
          :disabled="busy"
        >
          <el-radio-button value="YES">合理</el-radio-button>
          <el-radio-button value="NO">不合理</el-radio-button>
        </el-radio-group>
        <el-input
          v-if="item.decision === 'NO'"
          v-model="item.unreasonableReason"
          :data-testid="`review-reason-${item.itemCode}`"
          maxlength="1000"
          placeholder="请填写不合理原因"
          :disabled="busy"
        />
      </div>
    </div>
    <div v-if="nodeCode === 'SERVICE_MANAGER'" class="assessment-review">
      <label for="assessment-review">P2 等级复核</label>
      <el-radio-group id="assessment-review" v-model="assessmentDecision" :disabled="busy">
        <el-radio-button value="CONFIRMED">等级合理</el-radio-button>
        <el-radio-button value="NOT_REASONABLE">等级不合理</el-radio-button>
      </el-radio-group>
      <el-input
        v-if="assessmentDecision === 'NOT_REASONABLE'"
        v-model="assessmentReason"
        data-testid="assessment-review-reason"
        maxlength="1000"
        placeholder="请填写等级不合理原因"
        :disabled="busy"
      />
    </div>
    <el-input
      v-model="feedback"
      data-testid="approval-feedback"
      type="textarea"
      :rows="3"
      maxlength="1000"
      show-word-limit
      placeholder="请输入审批反馈"
      :disabled="busy"
    />
    <div class="decision-actions">
      <el-button
        v-if="allowedActions.includes('REJECT')"
        data-testid="reject-approval"
        :loading="busy"
        :disabled="!canReject"
        v-hasPermi="['pms:cutover-task:approve']"
        @click="submit('REJECT')"
        >驳回</el-button
      >
      <el-button
        v-if="allowedActions.includes('APPROVE')"
        data-testid="approve-approval"
        type="primary"
        :loading="busy"
        :disabled="!canApprove"
        v-hasPermi="['pms:cutover-task:approve']"
        @click="submit('APPROVE')"
        >通过</el-button
      >
    </div>
  </section>
</template>

<script setup lang="ts">
import type {
  CutoverApprovalAction,
  CutoverApprovalDecisionRequest,
  CutoverApprovalNodeCode,
  CutoverApprovalReviewItem
} from '@/api/pms/cutover/cutover-task'

const props = defineProps<{
  nodeCode: CutoverApprovalNodeCode
  allowedActions: CutoverApprovalAction[]
  busy: boolean
}>()
const emit = defineEmits<{ decide: [value: CutoverApprovalDecisionRequest] }>()
const reviewLabels: Record<CutoverApprovalReviewItem['itemCode'], string> = {
  PREPARATION: '准备工作',
  BUSINESS_TEST: '业务测试',
  EXECUTION: '割接操作',
  ROLLBACK: '回退步骤',
  OTHER: '其他事项'
}
const reviewItems = reactive<CutoverApprovalReviewItem[]>(
  Object.keys(reviewLabels).map((itemCode) => ({
    itemCode: itemCode as CutoverApprovalReviewItem['itemCode'],
    decision: 'YES',
    unreasonableReason: null
  }))
)
const assessmentDecision = ref<'CONFIRMED' | 'NOT_REASONABLE'>('CONFIRMED')
const assessmentReason = ref('')
const feedback = ref('')
const everyNoHasReason = computed(() =>
  reviewItems.every((item) => item.decision === 'YES' || Boolean(item.unreasonableReason?.trim()))
)
const everyReviewApproved = computed(() => reviewItems.every((item) => item.decision === 'YES'))
const assessmentReasonComplete = computed(
  () =>
    props.nodeCode !== 'SERVICE_MANAGER' ||
    assessmentDecision.value === 'CONFIRMED' ||
    Boolean(assessmentReason.value.trim())
)
const canApprove = computed(
  () =>
    Boolean(feedback.value.trim()) &&
    everyReviewApproved.value &&
    (props.nodeCode !== 'SERVICE_MANAGER' || assessmentDecision.value === 'CONFIRMED')
)
const canReject = computed(
  () =>
    Boolean(feedback.value.trim()) &&
    everyNoHasReason.value &&
    assessmentReasonComplete.value &&
    (!everyReviewApproved.value ||
      (props.nodeCode === 'SERVICE_MANAGER' && assessmentDecision.value === 'NOT_REASONABLE'))
)

const submit = (action: 'APPROVE' | 'REJECT') => {
  const items = reviewItems.map((item) => ({
    ...item,
    unreasonableReason: item.decision === 'NO' ? item.unreasonableReason?.trim() || null : null
  }))
  emit('decide', {
    action,
    reviewItems: items,
    assessmentReview:
      props.nodeCode === 'SERVICE_MANAGER'
        ? {
            decision: assessmentDecision.value,
            reason:
              assessmentDecision.value === 'NOT_REASONABLE'
                ? assessmentReason.value.trim() || null
                : null
          }
        : null,
    feedback: feedback.value.trim()
  })
}
</script>

<style scoped>
.decision-form {
  display: grid;
  gap: 16px;
  padding-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.decision-form h3 {
  margin: 0;
}

.review-list {
  display: grid;
  gap: 12px;
}

.review-row {
  display: grid;
  grid-template-columns: minmax(100px, 140px) auto minmax(180px, 1fr);
  gap: 12px;
  align-items: center;
}

.assessment-review {
  display: grid;
  grid-template-columns: minmax(100px, 140px) auto minmax(180px, 1fr);
  gap: 12px;
  align-items: center;
}

.decision-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (width <= 767px) {
  .review-row,
  .assessment-review {
    grid-template-columns: 1fr;
  }
}
</style>
