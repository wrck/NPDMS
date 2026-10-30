<script setup lang="ts">
import { statusLabel, formatLocalTime } from '@/management/presentation'
import ManagementStatus from '@/components/management/ManagementStatus.vue'
import '@/styles/management.css'
import { onMounted, reactive, ref } from 'vue'
import AppShell from '@/components/AppShell.vue'
import RequestState from '@/components/management/RequestState.vue'
import { managementApi } from '@/api/management'
import { parserApi } from '@/api/parser-management'
import { createRequest } from '@/management/use-request'
import type { CollectionFilters, CollectionSummary, Page, RuntimeStatus } from '@/types/management'
const { data, loading, error, run } = createRequest<Awaited<ReturnType<typeof managementApi.overview>>>()
const recent = createRequest<Page<CollectionSummary>>()
const runtime = createRequest<RuntimeStatus>()
const filters = reactive({ namespace: '', project: '', from: '', to: '' })
const applied = ref<CollectionFilters>({}), validation = ref('')
function load() {
  const query: CollectionFilters = {}
  validation.value = ''
  for (const key of ['namespace', 'project'] as const) if (filters[key].trim()) query[key] = filters[key].trim()
  for (const key of ['from', 'to'] as const) if (filters[key]) {
    const date = new Date(filters[key])
    if (!Number.isFinite(date.getTime())) { validation.value = '请输入有效时间。'; return }
    query[key] = date.toISOString()
  }
  if (query.from && query.to && query.from > query.to) { validation.value = '开始时间不能晚于结束时间。'; return }
  applied.value = query
  void run(signal => managementApi.overview(query, signal))
  void loadRecent()
}
const loadRecent = () => recent.run(signal => managementApi.collections({ ...applied.value, page: 0, size: 5 }, signal))
const loadRuntime = () => runtime.run(parserApi.runtime)
function recordsQuery(status: string) { return { namespace: applied.value.namespace, project: applied.value.project, from: applied.value.from, to: applied.value.to, ...(status === 'UNKNOWN' ? {} : { status }) } }
onMounted(() => { load(); void loadRuntime() })
</script>
<template>
  <AppShell
    class="management-page"
    element-layout
    title="设备运维总览"
    subtitle="仅统计授权范围内数据"
  >
    <div class="overview">
      <el-card shadow="never">
        <template #header>
          <h2>授权范围运行概况</h2>
        </template>
        <el-form
          inline
          @submit.prevent="load"
        >
          <el-form-item label="命名空间">
            <el-input
              v-model="filters.namespace"
              aria-label="总览 命名空间"
            />
          </el-form-item>
          <el-form-item label="项目">
            <el-input
              v-model="filters.project"
              aria-label="总览项目"
            />
          </el-form-item>
          <el-form-item label="开始时间">
            <el-input
              v-model="filters.from"
              type="datetime-local"
              aria-label="总览开始时间"
            />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-input
              v-model="filters.to"
              type="datetime-local"
              aria-label="总览结束时间"
            />
          </el-form-item>
          <el-button
            native-type="submit"
            type="primary"
            :loading="loading"
          >
            查询统计
          </el-button>
        </el-form>
        <el-alert
          v-if="validation"
          :title="validation"
          type="warning"
          :closable="false"
        />
        <RequestState
          :loading="loading"
          :error="error"
          @retry="load"
        >
          <div
            v-if="data"
            class="metrics"
          >
            <el-statistic
              title="采集总数"
              :value="data.total"
            />
            <RouterLink
              v-for="(count, status) in data.byStatus"
              :key="status"
              :to="{ path: '/records', query: recordsQuery(String(status)) }"
            >
              <el-statistic
                :title="statusLabel(String(status))"
                :value="count"
              />
            </RouterLink>
          </div>
          <el-empty
            v-if="data?.total === 0"
            description="当前授权范围暂无采集记录"
          />
        </RequestState>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <h2>近期采集 · 当前筛选范围前 5 条</h2>
        </template>
        <RequestState
          :loading="recent.loading.value"
          :error="recent.error.value"
          :empty="recent.data.value?.items.length === 0"
          @retry="loadRecent"
        >
          <el-table :data="recent.data.value?.items">
            <el-table-column label="采集记录">
              <template #default="{ row }">
                <RouterLink :to="{ path: `/records/${encodeURIComponent(row.collectionId)}`, query: { namespace: row.namespace, mode: row.projectKey ? 'project' : 'generic', projectKey: row.projectKey || undefined } }">
                  {{ row.collectionId }}
                </RouterLink>
              </template>
            </el-table-column><el-table-column label="状态">
              <template #default="{ row }">
                <ManagementStatus :status="row.status" />
              </template>
            </el-table-column><el-table-column label="创建时间（本地）">
              <template #default="{ row }">
                {{ formatLocalTime(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
        </RequestState>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <h2>解析等待摘要 · 运行环境观测范围（不受采集筛选影响）</h2>
        </template>
        <RequestState
          :loading="runtime.loading.value"
          :error="runtime.error.value"
          @retry="loadRuntime"
        >
          <div
            v-if="runtime.data.value"
            class="metrics"
          >
            <el-statistic
              v-for="(count, reason) in runtime.data.value.waitingCounts"
              :key="reason"
              :title="String(reason)"
              :value="count"
            /><el-empty
              v-if="!Object.keys(runtime.data.value.waitingCounts).length"
              description="暂无等待任务"
            />
          </div>
        </RequestState>
      </el-card>
    </div>
  </AppShell>
</template>
