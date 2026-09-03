<template>
  <section class="dashboard-kpis" aria-labelledby="cutover-dashboard-kpi-title">
    <div class="kpi-heading">
      <div>
        <h2 id="cutover-dashboard-kpi-title">割接任务概览</h2>
        <p>仅统计当前账号已授权项目与实际可执行动作。</p>
      </div>
      <span v-if="data" class="generated-at">更新于 {{ formatWireDateTime(data.generatedAt) }}</span>
    </div>

    <div v-if="loading && !data" class="kpi-status" role="status" aria-live="polite">
      正在加载割接任务概览…
    </div>
    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-else-if="data" class="kpi-grid">
      <article v-for="item in cards" :key="item.key" class="kpi-card" :data-testid="`kpi-${item.key}`">
        <span class="kpi-label">{{ item.label }}</span>
        <strong class="kpi-value">{{ item.value }}</strong>
        <span class="kpi-description">{{ item.description }}</span>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { CutoverDashboardKpiData } from '@/api/pms/cutover/cutover-task'
import { formatWireDateTime } from '../cutoverTaskInteraction'

const props = defineProps<{
  data: CutoverDashboardKpiData | null
  loading: boolean
  error: string | null
}>()

const cards = computed(() => [
  { key: 'todo', label: '待办', value: String(props.data?.todoCount ?? ''), description: '当前可执行的 P2～P6 任务' },
  { key: 'archived', label: '已归档', value: String(props.data?.archivedCount ?? ''), description: '已完成关闭的割接任务' },
  { key: 'approving', label: '审批中', value: String(props.data?.approvingCount ?? ''), description: '正在 P5 分级审批' },
  { key: 'rejected', label: '驳回待修改', value: String(props.data?.rejectedPendingModificationCount ?? ''), description: '等待修订后重新提交' }
])
</script>

<style scoped>
.dashboard-kpis {
  margin-top: 20px;
}

.kpi-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.kpi-heading h2 {
  margin: 0;
  font-size: 16px;
}

.kpi-heading p,
.generated-at,
.kpi-description {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.kpi-heading p {
  margin: 4px 0 0;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}

.kpi-label {
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.kpi-value {
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.kpi-status {
  min-height: 104px;
  display: grid;
  place-items: center;
  color: var(--el-text-color-secondary);
  border: 1px dashed var(--el-border-color);
}

@media (width <= 1023px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 479px) {
  .kpi-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
