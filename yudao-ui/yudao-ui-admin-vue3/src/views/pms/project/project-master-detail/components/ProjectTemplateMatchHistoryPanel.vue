<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>模板匹配决策历史</h3>
        <span>按发生时间倒序展示首次创建、来源修正和人工调整的不可变业务记录。</span>
      </div>
    </div>

    <el-form :model="query" class="query-form" label-position="top">
      <el-form-item label="触发类型">
        <el-select v-model="query.triggerType" clearable placeholder="全部类型">
          <el-option label="首次创建" value="INITIAL_CREATE" />
          <el-option label="来源修正" value="SOURCE_CORRECTION" />
          <el-option label="人工调整" value="MANUAL_ADJUSTMENT" />
        </el-select>
      </el-form-item>
      <el-form-item label="匹配结果">
        <el-select v-model="query.matchResult" clearable placeholder="全部结果">
          <el-option label="唯一命中" value="UNIQUE" />
          <el-option label="无匹配" value="NO_MATCH" />
          <el-option label="多匹配" value="MULTIPLE_MATCHES" />
        </el-select>
      </el-form-item>
      <el-form-item label="影响结论">
        <el-select v-model="query.impactResult" clearable placeholder="全部结论">
          <el-option label="无影响" value="NO_IMPACT" />
          <el-option label="模板变化" value="TEMPLATE_CHANGED" />
          <el-option label="待识别" value="UNRESOLVED" />
        </el-select>
      </el-form-item>
      <el-form-item label="发生时间">
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </el-form-item>
      <el-form-item class="query-actions">
        <el-button type="primary" @click="search"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="reset"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap :aria-busy="loading">
    <div class="table-scroll desktop-list">
      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        size="small"
        border
        empty-text="暂无模板匹配历史"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="snapshot-grid">
              <section>
                <strong>调整前属性</strong>
                <pre>{{ row.beforeAttributeSnapshot || '首次创建，无前值' }}</pre>
              </section>
              <section>
                <strong>判定属性</strong>
                <pre>{{ row.attributeSnapshot }}</pre>
              </section>
              <section>
                <strong>候选摘要</strong>
                <pre>{{ row.candidateDigest }}</pre>
              </section>
              <section>
                <strong>来源与关联</strong>
                <p>{{ sourceSummary(row) }}</p>
                <p>traceId：{{ row.traceId || '-' }}；auditLogId：{{ row.auditLogId || '-' }}</p>
              </section>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="触发" min-width="110">
          <template #default="{ row }">{{
            triggerLabel[row.triggerType] || row.triggerType
          }}</template>
        </el-table-column>
        <el-table-column label="匹配结果" min-width="110">
          <template #default="{ row }"
            ><el-tag size="small">{{ row.matchResult }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="影响结论" min-width="130">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.impactResult }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模板修订" min-width="120">
          <template #default="{ row }">#{{ row.matchedTemplateRevisionId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="changeReason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作者" min-width="100">
          <template #default="{ row }">#{{ row.operatorId }}</template>
        </el-table-column>
        <el-table-column
          prop="operationId"
          label="operationId"
          min-width="260"
          show-overflow-tooltip
        />
        <el-table-column label="发生时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div v-loading="loading" class="mobile-list" aria-live="polite">
      <article v-for="row in rows" :key="row.id" class="history-card">
        <div class="card-heading">
          <strong>{{ triggerLabel[row.triggerType] || row.triggerType }}</strong>
          <el-tag size="small">{{ row.matchResult }}</el-tag>
        </div>
        <el-descriptions :column="1" size="small">
          <el-descriptions-item label="影响">{{ row.impactResult }}</el-descriptions-item>
          <el-descriptions-item label="模板修订">
            #{{ row.matchedTemplateRevisionId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="原因">{{ row.changeReason }}</el-descriptions-item>
          <el-descriptions-item label="操作标识">{{ row.operationId }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">
            {{ formatDateTime(row.occurredAt) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-collapse>
          <el-collapse-item title="查看完整匹配证据">
            <strong>调整前快照</strong>
            <pre>{{ row.beforeAttributeSnapshot || '首次创建，无前值' }}</pre>
            <strong>判定后快照</strong>
            <pre>{{ row.attributeSnapshot }}</pre>
            <strong>候选摘要</strong>
            <pre>{{ row.candidateDigest }}</pre>
            <p>操作者：{{ row.operatorId }}</p>
            <p>traceId：{{ row.traceId || '-' }}</p>
            <p>auditLogId：{{ row.auditLogId || '-' }}</p>
          </el-collapse-item>
        </el-collapse>
      </article>
      <el-empty v-if="!loading && !rows.length" description="暂无模板匹配历史" />
    </div>

    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import type {
  ProjectTemplateMatchHistoryPageParams,
  ProjectTemplateMatchHistoryVO
} from '@/api/pms/project/projects'

defineOptions({ name: 'ProjectTemplateMatchHistoryPanel' })

const props = defineProps<{ projectId: number }>()
const triggerLabel: Record<string, string> = {
  INITIAL_CREATE: '首次创建',
  SOURCE_CORRECTION: '来源修正',
  MANUAL_ADJUSTMENT: '人工调整'
}
const loading = ref(false)
const rows = ref<ProjectTemplateMatchHistoryVO[]>([])
const total = ref(0)
const timeRange = ref<[string, string] | []>([])
const query = reactive<ProjectTemplateMatchHistoryPageParams>({
  pageNo: 1,
  pageSize: 10,
  orderBy: 'occurredAt',
  ascending: false
})

const formatDateTime = (value?: string | null) => (value ? formatDate(value) : '-')
const sourceSummary = (row: ProjectTemplateMatchHistoryVO) =>
  row.sourceSystem ? `${row.sourceSystem} / ${row.sourceVersion || '-'}` : '人工输入'

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectsApi.getProjectTemplateMatchHistoryPage(props.projectId, {
      ...query,
      occurredAtBegin: timeRange.value[0],
      occurredAtEnd: timeRange.value[1]
    })
    rows.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.pageNo = 1
  load()
}

const reset = () => {
  Object.assign(query, {
    pageNo: 1,
    pageSize: query.pageSize,
    triggerType: undefined,
    matchResult: undefined,
    impactResult: undefined,
    orderBy: 'occurredAt',
    ascending: false
  })
  timeRange.value = []
  load()
}

watch(() => props.projectId, load, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading,
.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-heading {
  margin-bottom: 16px;
}

.panel-heading h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.panel-heading span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.query-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(140px, 1fr)) minmax(280px, 2fr) auto;
  gap: 0 12px;
}

.query-actions {
  align-self: end;
}

.query-form :deep(.el-select),
.query-form :deep(.el-date-editor) {
  width: 100%;
}

.table-scroll {
  max-width: 100%;
  overflow-x: auto;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
}

.snapshot-grid section,
.history-card {
  padding: 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

pre {
  margin: 8px 0 0;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.mobile-list {
  display: none;
}

@media (width <= 1199px) {
  .query-form {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (width <= 767px) {
  .query-form,
  .snapshot-grid {
    grid-template-columns: 1fr;
  }

  .desktop-list {
    display: none;
  }

  .mobile-list {
    display: grid;
    gap: 12px;
  }

  .history-card {
    min-width: 0;
  }
}
</style>
