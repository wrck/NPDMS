<template>
  <el-drawer v-model="visible" :size="narrow ? '100%' : '720px'" title="需求分析完成历史">
    <el-skeleton v-if="loading && !rows.length" :rows="5" animated />
    <el-empty v-else-if="!rows.length" description="暂无已完成版本" />
    <div v-else class="history-list">
      <article v-for="row in rows" :key="row.preparationId" class="history-row">
        <div>
          <div class="history-title">
            <strong>业务版本 V{{ row.businessVersion }}</strong>
            <el-tag v-if="row.currentEffective" type="success" size="small">当前有效</el-tag>
            <el-tag v-else type="info" size="small">历史完成版</el-tag>
          </div>
          <p>
            内容版本 {{ row.contentVersion }} · 完成人 {{ row.completedBy || '-' }} ·
            {{ formatDateTime(row.completedAt) }}
          </p>
        </div>
        <div class="history-actions">
          <el-button link type="primary" @click="emit('view', row.preparationId)">查看</el-button>
          <el-button
            v-if="canCompare(row.preparationId)"
            link
            @click="emit('compare', currentPreparationId!, row.preparationId)"
          >
            与当前查看版对比
          </el-button>
        </div>
      </article>
      <el-button v-if="hasMore" :loading="loading" class="load-more" @click="loadMore">
        加载更多
      </el-button>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { formatDate } from '@/utils/formatTime'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import type { RequirementAnalysisVersionSummaryVO } from '@/api/pms/engineering/requirement-analysis'

const emit = defineEmits<{
  view: [preparationId: number]
  compare: [preparationId: number, targetPreparationId: number]
}>()
const narrow = useMediaQuery('(max-width: 767px)')
const visible = ref(false)
const loading = ref(false)
const projectId = ref<number>()
const currentPreparationId = ref<number>()
const currentStatus = ref<'DRAFT' | 'COMPLETED'>()
const currentSourcePreparationId = ref<number>()
const rows = ref<RequirementAnalysisVersionSummaryVO[]>([])
const cursor = ref<string>()
const hasMore = ref(false)

const formatDateTime = (value?: string) => (value ? formatDate(value) : '-')
const loadMore = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const page = await RequirementAnalysisApi.getHistory(projectId.value, {
      cursor: cursor.value,
      pageSize: 20
    })
    rows.value.push(...page.items)
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } finally {
    loading.value = false
  }
}
const canCompare = (targetPreparationId: number) =>
  Boolean(
    currentPreparationId.value &&
    currentPreparationId.value !== targetPreparationId &&
    (currentStatus.value === 'COMPLETED' ||
      currentSourcePreparationId.value === targetPreparationId)
  )
const open = async (
  value: number,
  selectedPreparationId?: number,
  selectedStatus?: 'DRAFT' | 'COMPLETED',
  sourcePreparationId?: number
) => {
  projectId.value = value
  currentPreparationId.value = selectedPreparationId
  currentStatus.value = selectedStatus
  currentSourcePreparationId.value = sourcePreparationId
  rows.value = []
  cursor.value = undefined
  hasMore.value = false
  visible.value = true
  await loadMore()
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.history-list {
  display: grid;
  gap: 10px;
}

.history-row,
.history-title,
.history-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-row {
  justify-content: space-between;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.history-row p {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.load-more {
  width: 100%;
}

@media (width <= 767px) {
  .history-row {
    align-items: stretch;
    flex-direction: column;
  }

  .history-actions {
    justify-content: flex-end;
  }
}
</style>
