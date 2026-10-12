<template>
  <section class="source-history" v-loading="loading">
    <h3>来源出厂软件版本</h3>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link type="primary" @click="load">重新加载</el-button>
    </el-alert>
    <el-table v-else-if="rows.length" :data="rows" row-key="id">
      <el-table-column prop="values.deviceSn" label="来源序列号" min-width="110" />
      <el-table-column prop="values.conpVersion" label="CONP" min-width="110" />
      <el-table-column prop="values.bootVersion" label="BOOT" min-width="110" />
      <el-table-column prop="values.cpldVersion" label="CPLD" min-width="110" />
      <el-table-column prop="values.pcbVersion" label="PCB" min-width="110" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看原字段</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!loading" :image-size="70" description="暂无来源业务记录" />
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
      title="来源业务记录原字段"
      size="min(860px, 95vw)"
      destroy-on-close
    >
      <DeviceReadOnlyFields v-if="selected" title="来源出厂软件版本" :fields="detailFields" />
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import {
  getDeviceSourceFactoryVersions,
  DeviceSourceFactoryVersion
} from '@/api/pms/asset/device'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
const props = defineProps<{ deviceId: number }>()
const rows = ref<{ id: number | string; projectId: number | string; values: DeviceSourceFactoryVersion }[]>([])
const selected = ref<{ id: number | string; projectId: number | string; values: DeviceSourceFactoryVersion }>()
const detailVisible = ref(false)
const openDetail = (row: (typeof rows.value)[number]) => {
  selected.value = row
  detailVisible.value = true
}
const loading = ref(false)
const error = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
let requestVersion = 0
const fieldDefinitions = [
  { key: 'deviceSn', label: '来源设备序列号' },
  { key: 'conpVersion', label: 'CONP' },
  { key: 'bootVersion', label: 'BOOT' },
  { key: 'cpldVersion', label: 'CPLD' },
  { key: 'pcbVersion', label: 'PCB' }
] as const
const detailFields = computed(() => {
  if (!selected.value) return []
  const values = selected.value.values
  return fieldDefinitions.map(({ key, label }) => ({
    label,
    value: values[key]
  }))
})
const load = async () => {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const page = await getDeviceSourceFactoryVersions(props.deviceId, {
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
      error.value = '来源业务记录加载失败'
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
  font-size: 15px;
}

.source-history :deep(.el-pagination) {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
