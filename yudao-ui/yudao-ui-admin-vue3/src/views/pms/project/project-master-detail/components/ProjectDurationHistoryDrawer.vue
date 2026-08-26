<template>
  <el-drawer v-model="visible" title="项目工期历史" :size="drawerSize" destroy-on-close>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="工期版本" name="revisions">
        <el-timeline v-if="revisions.length">
          <el-timeline-item v-for="item in revisions" :key="item.revisionId" :timestamp="formatTime(item.createdAt)">
            <div class="history-title">
              <strong>版本 {{ item.revisionNo }}</strong>
              <el-tag v-if="item.current" type="success" size="small">当前生效</el-tag>
            </div>
            <div class="history-facts">
              <span>{{ basisLabel(item.calculationBasis) }}</span>
              <span>{{ item.startDate }} 至 {{ item.endDate }}</span>
              <span>{{ item.durationDays }} 个自然日</span>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无工期版本" />
        <el-button v-if="revisionHasMore" class="load-more" :loading="loading" @click="loadRevisions(false)">加载更多</el-button>
      </el-tab-pane>
      <el-tab-pane label="变更记录" name="changes">
        <div v-if="changes.length" class="change-list">
          <article v-for="item in changes" :key="item.changeId" class="change-card">
            <div class="history-title">
              <strong>变更 #{{ item.changeId }}</strong>
              <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
            </div>
            <div class="history-facts">
              <span>候选版本 {{ item.candidateRevision.revisionNo }}</span>
              <span>{{ item.candidateRevision.startDate }} 至 {{ item.candidateRevision.endDate }}</span>
              <span>{{ item.candidateRevision.durationDays }} 个自然日</span>
              <span>原因：{{ item.reasonDetail || item.reasonType }}</span>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无变更记录" />
        <el-button v-if="changeHasMore" class="load-more" :loading="loading" @click="loadChanges(false)">加载更多</el-button>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { formatDate } from '@/utils/formatTime'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import type {
  ConstructionPlanChangeVO,
  ConstructionPlanRevisionVO,
  DurationCalculationBasis,
  DurationChangeStatus
} from '@/api/pms/engineering/construction-plan'

const narrow = useMediaQuery('(max-width: 767px)')
const drawerSize = computed(() => (narrow.value ? '100%' : '680px'))
const visible = ref(false)
const loading = ref(false)
const activeTab = ref('revisions')
const planId = ref<number>()
const revisions = ref<ConstructionPlanRevisionVO[]>([])
const changes = ref<ConstructionPlanChangeVO[]>([])
const revisionCursor = ref<string>()
const changeCursor = ref<string>()
const revisionHasMore = ref(false)
const changeHasMore = ref(false)

const formatTime = (value?: string) => (value ? formatDate(value) : '-')
const basisLabel = (value: DurationCalculationBasis) =>
  value === 'DATE_RANGE' ? '起止日期口径' : '起点 + 天数口径'
const statusLabel = (status: DurationChangeStatus) => ({
  DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回', WITHDRAWN: '已撤回'
})[status]
const statusType = (status: DurationChangeStatus) => ({
  DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'success',
  REJECTED: 'danger', WITHDRAWN: 'info'
})[status] as 'info' | 'warning' | 'success' | 'danger'

const loadRevisions = async (reset = true) => {
  if (!planId.value) return
  loading.value = true
  try {
    const page = await DurationApi.getRevisions(planId.value, {
      cursor: reset ? undefined : revisionCursor.value, pageSize: 20
    })
    revisions.value = reset ? page.items : [...revisions.value, ...page.items]
    revisionCursor.value = page.nextCursor
    revisionHasMore.value = page.hasMore
  } finally { loading.value = false }
}
const loadChanges = async (reset = true) => {
  if (!planId.value) return
  loading.value = true
  try {
    const page = await DurationApi.getChanges(planId.value, {
      cursor: reset ? undefined : changeCursor.value, pageSize: 20
    })
    changes.value = reset ? page.items : [...changes.value, ...page.items]
    changeCursor.value = page.nextCursor
    changeHasMore.value = page.hasMore
  } finally { loading.value = false }
}
const open = async (value: number) => {
  planId.value = value
  visible.value = true
  activeTab.value = 'revisions'
  await Promise.all([loadRevisions(), loadChanges()])
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.history-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-facts {
  display: grid;
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  gap: 4px;
}

.change-list {
  display: grid;
  gap: 10px;
}

.change-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.load-more {
  width: 100%;
  margin-top: 12px;
}
</style>
