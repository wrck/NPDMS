<template>
  <el-drawer v-model="visible" title="项目工期历史" :size="drawerSize" destroy-on-close>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="工期版本" name="revisions">
        <el-table :data="revisions" data-testid="duration-history-revisions" empty-text="暂无工期版本">
          <el-table-column prop="revisionNo" label="版本" width="80" />
          <el-table-column label="状态" width="110"><template #default="{ row }">{{ row.current ? '当前生效' : '历史版本' }}</template></el-table-column>
          <el-table-column label="计算口径" min-width="140"><template #default="{ row }">{{ basisLabel(row.calculationBasis) }}</template></el-table-column>
          <el-table-column prop="startDate" label="开始日期" min-width="130" />
          <el-table-column prop="endDate" label="结束日期" min-width="130" />
          <el-table-column prop="durationDays" label="自然日天数" width="110" />
          <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        </el-table>
        <el-button v-if="revisionHasMore" class="load-more" :loading="loading" @click="loadRevisions(false)">加载更多</el-button>
      </el-tab-pane>
      <el-tab-pane label="变更记录" name="changes">
        <el-table :data="changes" data-testid="duration-history-changes" empty-text="暂无变更记录">
          <el-table-column prop="changeId" label="变更编号" min-width="160" />
          <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="candidateRevision.revisionNo" label="候选版本" width="100" />
          <el-table-column prop="candidateRevision.startDate" label="开始日期" min-width="130" />
          <el-table-column prop="candidateRevision.endDate" label="结束日期" min-width="130" />
          <el-table-column prop="candidateRevision.durationDays" label="自然日天数" width="110" />
          <el-table-column label="变更原因" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.reasonDetail || row.reasonType }}</template></el-table-column>
        </el-table>
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
.load-more {
  width: 100%;
  margin-top: 12px;
}
</style>
