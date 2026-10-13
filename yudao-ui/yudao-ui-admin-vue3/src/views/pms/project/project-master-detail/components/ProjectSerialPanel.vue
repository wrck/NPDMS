<!-- 项目详情 · 序列号详情面板（DemoV2 1.1.1）：18 列序列号明细表，
     行数据取 /pms/asset/devices/page（按项目过滤），安装地址取设备当前完整地址投影，设备位置快照为兼容显示，
     在网版本、维保统计由设备分页一次返回，当前安装地址直接取设备当前投影。
     出厂版本（MES）与官网版本/受影响技术公告（KNO）数据源未接入，如实以「—」展示。 -->
<template>
  <ContentWrap :body-style="{ padding: '20px' }">
    <div class="panel-header">
      <span class="panel-title"><Icon icon="ep:cpu" /> 序列号详情</span>
      <span class="serial-total num">共 {{ total }} 台</span>
    </div>
    <el-form inline @submit.prevent="search">
      <el-form-item label="序列号"><el-input v-model="sn" clearable placeholder="查询设备序列号" @keyup.enter="search" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="loading" @click="search">查询</el-button></el-form-item>
    </el-form>
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" class="serial-alert" />
    <el-alert
      class="serial-alert"
      type="info"
      :closable="false"
      title="出厂版本、官网版本及技术公告尚未同步，暂以「—」展示。"
    />
    <el-table
      v-loading="loading"
      :data="rows"
      empty-text="本项目暂无符合查询条件的已登记设备"
      data-testid="serial-device-table"
    >
      <el-table-column prop="sn" label="序列号" width="150" fixed="left" show-overflow-tooltip />
      <el-table-column label="产品编码" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="产品名称" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productName || '—' }}</template>
      </el-table-column>
      <el-table-column label="安装地址" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.location || '—' }}</template>
      </el-table-column>
      <el-table-column label="出厂软件版本" min-width="110" align="center">
        <template #default>{{ NOT_INTEGRATED }}</template>
      </el-table-column>
      <el-table-column label="出厂conboot版本" min-width="120" align="center">
        <template #default>{{ NOT_INTEGRATED }}</template>
      </el-table-column>
      <el-table-column label="出厂cpld版本" min-width="110" align="center">
        <template #default>{{ NOT_INTEGRATED }}</template>
      </el-table-column>
      <el-table-column label="官网版本" min-width="100" align="center">
        <template #default>{{ NOT_INTEGRATED }}</template>
      </el-table-column>
      <el-table-column prop="conpType" label="在网软件版本分支" min-width="120" align="center">
        <template #default="{ row }">{{ row.conpType || '—' }}</template>
      </el-table-column>
      <el-table-column prop="conpVersion" label="在网软件版本" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.conpVersion || '—' }}</template>
      </el-table-column>
      <el-table-column label="在网conboot版本" min-width="120" align="center">
        <template #default="{ row }">{{ row.bootVersion || '—' }}</template>
      </el-table-column>
      <el-table-column label="在网cpld版本" min-width="110" align="center">
        <template #default="{ row }">{{ row.cpldVersion || '—' }}</template>
      </el-table-column>
      <el-table-column label="受影响技术公告" min-width="120" align="center">
        <template #default>{{ NOT_INTEGRATED }}</template>
      </el-table-column>
      <el-table-column label="维保时长" width="90" align="center">
        <template #default="{ row }">
          {{ row.warrantyMonths != null ? `${row.warrantyMonths}个月` : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="warrantyStartDate" label="维保开始时间" width="110">
        <template #default="{ row }">{{ row.warrantyStartDate || '—' }}</template>
      </el-table-column>
      <el-table-column prop="warrantyEndDate" label="维保结束时间" width="110">
        <template #default="{ row }">{{ row.warrantyEndDate || '—' }}</template>
      </el-table-column>
      <el-table-column label="续保次数" width="90" align="center">
        <template #default="{ row }">{{ row.extendedCount ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="配置Log" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :data-testid="`serial-config-log-${row.sn}`"
            @click="emit('open-config-log', row.deviceId, row.sn)"
          >
            配置Log
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="pageNo"
      v-model:limit="pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { locationSnapshotText } from '@/utils/locationSnapshot'
import { onBeforeUnmount, ref, watch } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'

defineOptions({ name: 'ProjectSerialPanel' })

const props = withDefaults(defineProps<{ projectId: number; active?: boolean }>(), { active: true })
const emit = defineEmits<{ (e: 'open-config-log', deviceId: number, serialNumber: string): void }>()

const NOT_INTEGRATED = '—'

interface SerialRow {
  deviceId: number
  sn: string
  productCode?: string
  productName?: string
  conpVersion?: string
  conpType?: string
  warrantyStartDate?: string
  warrantyEndDate?: string
  warrantyMonths?: number
  extendedCount?: number
  bootVersion?: string
  cpldVersion?: string
  location?: string
}

const loading = ref(false)
const rows = ref<SerialRow[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const sn = ref('')
const errorText = ref('')
let loadSequence = 0
const load = async () => {
  const sequence = ++loadSequence
  const projectId = props.projectId
  rows.value = []
  // 翻页期间保留总数，避免分页组件把当前页裁回第一页并触发额外请求。
  errorText.value = ''
  if (!projectId || !props.active) { loading.value = false; return }
  loading.value = true
  try {
    const page = await DeviceApi.getDevicePage({
      projectId,
      sn: sn.value.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    if (sequence !== loadSequence) return
    total.value = Number(page.total || 0)
    const list: SerialRow[] = (page.list || []).map((d) => ({
      deviceId: d.deviceId,
      sn: d.sn,
      productCode: d.productCode,
      productName: d.productName,
      conpVersion: d.conpVersion,
      conpType: d.conpType,
      warrantyStartDate: d.warrantyStartDate,
      warrantyEndDate: d.warrantyEndDate,
      bootVersion: d.bootVersion,
      cpldVersion: d.cpldVersion,
      warrantyMonths: d.warrantyMonths,
      extendedCount: d.extendedCount,
      location: locationSnapshotText(d.locationSnapshot)
    }))
    rows.value = list
  } catch {
    if (sequence === loadSequence) errorText.value = '序列号加载失败，请重新查询。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

// 未同步字段保持「—」，安装地址查询失败时仅展示已有可读快照。
const search = () => { pageNo.value = 1; void load() }
watch(() => props.projectId, () => { total.value = 0; sn.value = ''; search() }, { immediate: true })
watch(() => props.active, active => { if (active) void load() })
onBeforeUnmount(() => { ++loadSequence })
</script>

<style lang="scss" scoped>
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.serial-total {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.serial-alert {
  margin-bottom: 10px;
}
</style>
