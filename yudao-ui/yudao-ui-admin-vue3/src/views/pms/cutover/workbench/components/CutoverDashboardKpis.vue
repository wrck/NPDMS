<template>
  <section class="dashboard-kpis" aria-label="割接任务统计" :aria-busy="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-else class="kpi-grid">
      <button v-for="item in cards" :key="item.key" type="button" :class="['kpi-card', 'kpi-' + item.key, { selected: selected === item.key }]"
        :data-testid="'kpi-' + item.key" :aria-pressed="selected === item.key" :disabled="loading && !data" @click="emit('select', item.key)">
        <strong>{{ loading && !data ? '—' : item.value }}</strong><span>{{ item.label }}</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { CutoverDashboardKpiData } from '@/api/pms/cutover/workbench'

const props = defineProps<{
  data: CutoverDashboardKpiData | null
  loading: boolean
  error: string | null
  selected?: string
}>()

const emit = defineEmits<{ select: [key: string] }>()
const cards = computed(() => [
  { key: 'todo', label: '待办割接任务', value: String(props.data?.todoCount ?? ''), description: '当前可执行的 P2～P6 任务' },
  { key: 'archived', label: '已完成割接', value: String(props.data?.archivedCount ?? ''), description: '已完成关闭的割接任务' },
  { key: 'approving', label: '审批中', value: String(props.data?.approvingCount ?? ''), description: '正在 P5 分级审批' },
  { key: 'rejected', label: '驳回待修改', value: String(props.data?.rejectedPendingModificationCount ?? ''), description: '等待修订后重新提交' }
])
</script>

<style scoped>
.dashboard-kpis { margin-bottom: 16px; }
.kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.kpi-card { min-width: 0; padding: 16px 20px; border: 1px solid transparent; border-radius: 8px; background: #fff; box-shadow: 0 1px 3px rgb(0 0 0 / 4%); text-align: left; font: inherit; cursor: pointer; }
.kpi-card strong { display: block; margin-bottom: 2px; font-size: 28px; font-weight: 600; line-height: 1.5; font-variant-numeric: tabular-nums; }
.kpi-card span { color: #888; font-size: 12px; }
.kpi-todo strong { color: #1677ff; }
.kpi-archived strong { color: #52c41a; }
.kpi-approving strong { color: #ff4d4f; }
.kpi-rejected strong { color: #faad14; }
.kpi-card.selected { border-color: #91caff; background: #fafdff; }
@media (width <= 767px) { .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } .kpi-card { padding: 12px 14px; } }
</style>
