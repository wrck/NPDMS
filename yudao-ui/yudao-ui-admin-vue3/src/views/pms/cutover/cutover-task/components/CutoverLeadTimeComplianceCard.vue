<template>
  <section
    class="lead-time-card"
    :class="compliance.lateSubmission ? 'is-late' : 'is-compliant'"
    aria-labelledby="lead-time-heading"
    data-testid="lead-time-card"
  >
    <header>
      <div>
        <h3 id="lead-time-heading">P5 提前时间参考</h3>
        <p>按 Asia/Shanghai 自然日口径，以方案提交时冻结的规则判断。</p>
      </div>
      <strong role="status" data-testid="lead-time-status">
        {{ compliance.lateSubmission ? '迟交：是' : '迟交：否' }}
      </strong>
    </header>
    <dl>
      <div>
        <dt>计划操作时间</dt>
        <dd data-testid="lead-time-scheduled">{{
          formatWireDateTime(compliance.scheduledTime)
        }}</dd>
      </div>
      <div>
        <dt>方案提交时间</dt>
        <dd data-testid="lead-time-submitted">{{
          formatWireDateTime(compliance.planSubmittedAt)
        }}</dd>
      </div>
      <div>
        <dt>要求提前</dt>
        <dd>{{ compliance.requiredDays }} 个自然日</dd>
      </div>
      <div>
        <dt>实际提前</dt>
        <dd>{{ compliance.actualNaturalDays }} 个自然日</dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import type { CutoverLeadTimeCompliance } from '@/api/pms/cutover/cutover-task'
import { formatWireDateTime } from '../cutoverTaskInteraction'

defineProps<{ compliance: CutoverLeadTimeCompliance }>()
</script>

<style scoped>
.lead-time-card {
  display: grid;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-left-width: 4px;
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
}

.lead-time-card.is-late {
  border-left-color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.lead-time-card.is-compliant {
  border-left-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.lead-time-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.lead-time-card h3,
.lead-time-card p,
.lead-time-card dl,
.lead-time-card dd {
  margin: 0;
}

.lead-time-card p,
.lead-time-card dt {
  color: var(--el-text-color-secondary);
}

.lead-time-card p {
  margin-top: 4px;
}

.lead-time-card dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.lead-time-card dl > div {
  min-width: 0;
}

.lead-time-card dd {
  margin-top: 4px;
  overflow-wrap: anywhere;
  font-weight: 600;
}

@media (width <= 767px) {
  .lead-time-card header {
    flex-direction: column;
  }

  .lead-time-card dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 359px) {
  .lead-time-card dl {
    grid-template-columns: 1fr;
  }
}
</style>
