<template>
  <section
    class="source-history"
    v-loading="loading"
    data-testid="installation-source-history"
    :aria-busy="loading"
  >
    <h3>安装记录</h3>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link type="primary" @click="load">重新加载</el-button>
    </el-alert>
    <el-table v-else-if="rows.length" :data="rows" row-key="id">
      <el-table-column label="安装编码" min-width="150">
        <template #default="{ row }">{{ row.historicalInstallation?.code ?? '—' }}</template>
      </el-table-column>
      <el-table-column
        prop="installLocation"
        label="安装地址"
        min-width="240"
        show-overflow-tooltip
      />
      <el-table-column label="安装记录" min-width="280">
        <template #default="{ row }">
          <div
            v-if="row.historicalInstallation"
            data-testid="historical-installation-summary"
            :data-installation-id="row.historicalInstallation.id"
          >
            <div data-testid="historical-installation-status">{{
              statusText(row.historicalInstallation.status)
            }}</div>
            <div data-testid="historical-installation-effective-time">
              生效时间：{{
                formatSourceDate(row.historicalInstallation.effectiveFrom, 'effectiveFrom') ?? '—'
              }}
            </div>
            <div
              >结束时间：{{
                formatSourceDate(row.historicalInstallation.effectiveTo, 'effectiveTo') ?? '—'
              }}</div
            >
            <div data-testid="historical-installation-current">{{
              row.historicalInstallation.currentEquipmentId != null ? '当前安装' : '历史安装'
            }}</div>
          </div>
          <span v-else>安装记录未关联</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }"
          ><el-button link type="primary" @click="openDetail(row)">查看详情</el-button></template
        >
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!loading" :image-size="70" description="暂无安装记录" />
    <el-pagination
      v-if="total > 0"
      v-model:current-page="pageNo"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @size-change="changePageSize"
      @current-change="load"
    />
    <el-drawer
      v-model="detailVisible"
      title="安装记录详情"
      size="min(860px, 95vw)"
      destroy-on-close
    >
      <DeviceReadOnlyFields v-if="selected" title="安装记录" :fields="detailFields" />
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import {
  getInstallationSourceRecords,
  type InstallationSourceRecord
} from '@/api/pms/engineering/installation-source-history'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
import { formatSourceDate } from '@/utils/formatSourceDate'
const props = defineProps<{ deviceId: number }>()
const rows = ref<InstallationSourceRecord[]>([])
const selected = ref<InstallationSourceRecord>()
const detailVisible = ref(false)
const openDetail = (row: InstallationSourceRecord) => {
  selected.value = row
  detailVisible.value = true
}
const installationStatuses: Record<number, string> = {
  0: '待安装',
  1: '进行中',
  2: '已安装',
  3: '异常'
}
const statusText = (status?: number) => installationStatuses[status ?? -1] ?? '未记录状态'
const loading = ref(false)
const error = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
let requestVersion = 0
const detailFields = computed(() => {
  const row = selected.value
  if (!row) return []
  const history = row.historicalInstallation
  return [
    { label: '安装编码', value: history?.code },
    { label: '设备序列号', value: row.sourceDeviceSn },
    { label: '安装地址', value: row.installLocation, multiline: true },
    { label: '安装状态', value: statusText(history?.status) },
    { label: '生效时间', value: formatSourceDate(history?.effectiveFrom, 'effectiveFrom') },
    { label: '结束时间', value: formatSourceDate(history?.effectiveTo, 'effectiveTo') },
    { label: '地址ID', value: row.addressId },
    { label: '地址版本', value: row.addressVersion },
    { label: '创建人', value: row.values.sourceCreatedBy },
    { label: '创建时间', value: formatSourceDate(row.values.sourceCreatedAt, 'createTime') },
    { label: '更新人', value: row.values.sourceUpdatedBy },
    { label: '更新时间', value: formatSourceDate(row.values.sourceUpdatedAt, 'updateTime') }
  ]
})
const load = async () => {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const page = await getInstallationSourceRecords({
      equipmentId: props.deviceId,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    if (version !== requestVersion) return
    rows.value = page.list || []
    total.value = page.total || 0
  } catch {
    if (version === requestVersion) {
      rows.value = []
      total.value = 0
      error.value = '安装记录加载失败'
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
const changePageSize = () => {
  pageNo.value = 1
  load()
}
watch(
  () => props.deviceId,
  () => {
    pageNo.value = 1
    rows.value = []
    total.value = 0
    selected.value = undefined
    detailVisible.value = false
    load()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  requestVersion++
})
</script>
<style scoped>
.source-history {
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-extra-light);
}
.source-history h3 {
  margin: 0 0 12px;
  font-size: 16px;
}
.source-history :deep(.el-pagination) {
  margin-top: 12px;
}
</style>
