<script setup lang="ts">
import { statusLabel, formatLocalTime } from '@/management/presentation'
import ManagementStatus from '@/components/management/ManagementStatus.vue'
import { onMounted, reactive, ref } from 'vue'
import { managementApi } from '@/api/management'
import RequestState from './RequestState.vue'
import { createRequest } from '@/management/use-request'
import type { CollectionFilters, CollectionSummary, Page } from '@/types/management'
const initial = new URLSearchParams(window.location.search)
const filters = reactive({ namespace: initial.get('namespace') || '', project: initial.get('project') || '', device: initial.get('device') || '', status: initial.get('status') || '', from: initial.get('from') || '', to: initial.get('to') || '' })
const page = ref(1), size = ref(20), validation = ref('')
const { data, loading, error, run } = createRequest<Page<CollectionSummary>>()
function load(reset = false) {
  if (reset) page.value = 1
  validation.value = ''
  const query: CollectionFilters = { page: page.value - 1, size: size.value }
  for (const key of ['namespace', 'project', 'device', 'status'] as const) if (filters[key].trim()) query[key] = filters[key].trim()
  for (const key of ['from', 'to'] as const) if (filters[key]) {
    const date = new Date(filters[key]); if (!Number.isFinite(date.getTime())) { validation.value = '请输入有效起止时间。'; return }; query[key] = date.toISOString()
  }
  if (query.from && query.to && query.from > query.to) { validation.value = '开始时间不能晚于结束时间。'; return }
  void run(signal => managementApi.collections(query, signal))
}
onMounted(() => load())
</script>
<template>
  <section>
    <el-form
      inline
      class="filters"
      @submit.prevent="load(true)"
    >
      <el-form-item label="命名空间">
        <el-input
          v-model="filters.namespace"
          clearable
          aria-label="命名空间 筛选"
        />
      </el-form-item><el-form-item label="项目">
        <el-input
          v-model="filters.project"
          clearable
          aria-label="项目筛选"
        />
      </el-form-item><el-form-item label="设备">
        <el-input
          v-model="filters.device"
          clearable
          aria-label="设备筛选"
        />
      </el-form-item><el-form-item label="状态">
        <el-select
          v-model="filters.status"
          clearable
          aria-label="状态筛选"
          style="width:170px"
        >
          <el-option
            v-for="state in ['QUEUED','CONNECTING','EXECUTING','PARSING','SUCCEEDED','PARTIAL_SUCCESS','FAILED','TIMED_OUT','CANCELLED']"
            :key="state"
            :value="state"
            :label="statusLabel(state)"
          />
        </el-select>
      </el-form-item><el-form-item label="开始时间">
        <el-input
          v-model="filters.from"
          type="datetime-local"
          aria-label="开始时间"
        />
      </el-form-item><el-form-item label="结束时间">
        <el-input
          v-model="filters.to"
          type="datetime-local"
          aria-label="结束时间"
        />
      </el-form-item><el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="loading"
        >
          查询
        </el-button>
      </el-form-item>
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
      :empty="data?.items.length === 0"
      empty-text="暂无采集记录"
      @retry="load()"
    >
      <el-table
        :data="data?.items"
        row-key="collectionId"
        stripe
      >
        <el-table-column
          label="采集 ID"
          min-width="190"
        >
          <template #default="{ row }">
            <RouterLink :to="{ path: `/records/${encodeURIComponent(row.collectionId)}`, query: { namespace: row.namespace, ...(row.projectKey ? { projectKey: row.projectKey, mode: 'project' } : { mode: 'generic' }) } }">
              {{ row.collectionId }}
            </RouterLink>
          </template>
        </el-table-column><el-table-column
          prop="namespace"
          label="命名空间"
        /><el-table-column
          prop="projectKey"
          label="项目"
        /><el-table-column label="状态">
          <template #default="{ row }">
            <ManagementStatus :status="row.status" />
          </template>
        </el-table-column><el-table-column
          prop="targetCount"
          label="目标数"
          width="90"
        /><el-table-column
          label="创建时间（本地）"
          min-width="170"
        >
          <template #default="{ row }">
            {{ formatLocalTime(row.createdAt) }}
          </template>
        </el-table-column><el-table-column label="脚本版本">
          <template #default="{ row }">
            {{ row.scriptKey || '未知' }} / {{ row.scriptVersion || '未知' }}
          </template>
        </el-table-column>
      </el-table>
    </RequestState>
    <el-pagination
      v-if="data"
      :current-page="page"
      :page-size="size"
      :total="data.total"
      :page-sizes="[20,50,100]"
      layout="total, sizes, prev, pager, next"
      @current-change="(value: number) => { page = value; load() }"
      @size-change="(value: number) => { size = value; load(true) }"
    />
  </section>
</template>
