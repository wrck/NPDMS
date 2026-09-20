<!-- 项目详情 · 序列号详情面板（DemoV2 1.1.1）：18 列序列号明细表，
     行数据取 /pms/asset/devices/page（按项目过滤），安装位置取设备档案位置快照按 sn 关联，
     在网 conboot/cpld 与维保时长/续保次数按当前页逐台补齐（设备详情 + 维保记录接口）。
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
      stripe
      border
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
      <el-table-column label="安装位置" min-width="140" show-overflow-tooltip>
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
            @click="emit('open-config-log', row.deviceId)"
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
import { onBeforeUnmount, ref, watch } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'

defineOptions({ name: 'ProjectSerialPanel' })

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ (e: 'open-config-log', deviceId: number): void }>()

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
  total.value = 0
  errorText.value = ''
  if (!projectId) return
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
      warrantyEndDate: d.warrantyEndDate
    }))
    // 只读取当前页的设备，避免截断项目第 200 台之后的安装位置。
    await enrich(list, projectId)
    if (sequence !== loadSequence) return
    rows.value = list
  } catch {
    if (sequence === loadSequence) errorText.value = '序列号加载失败，请重新查询。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

// 在网 conboot/cpld 版本与维保时长/续保次数不在列表投影内，按当前页逐台补齐；
// 详情切片未同步（NOT_AVAILABLE）时对应列保持「—」，不臆造取值
const enrich = async (list: SerialRow[], projectId: number) => {
  await Promise.allSettled(
    list.map(async (row) => {
      const [detail, warranty, archive] = await Promise.allSettled([
        DeviceApi.getDevice(row.deviceId),
        DeviceApi.getWarrantyRecords(row.deviceId, { pageNo: 1, pageSize: 100 }),
        DeviceArchiveApi.getDeviceArchivePage({ projectId, sn: row.sn, pageNo: 1, pageSize: 100 })
      ])
      if (archive.status === 'fulfilled') {
        row.location = archive.value.list?.find((item: DeviceArchiveApi.DeviceArchiveVO) => item.sn === row.sn)?.locationSnapshot
      }
      if (detail.status === 'fulfilled' && detail.value) {
        const slice = detail.value.networkVersion
        const data = (slice?.data || {}) as Record<string, any>
        if (slice && slice.syncStatus !== 'NOT_AVAILABLE') {
          row.bootVersion = data.bootVersion || ''
          row.cpldVersion = data.cpldVersion || ''
        }
      }
      if (warranty.status === 'fulfilled' && warranty.value) {
        row.warrantyMonths = warranty.value.current?.warrantyMonths
        const records = [...(warranty.value.records?.list || [])]
        const recordTotal = Number(warranty.value.records?.total || 0)
        for (let nextPage = 2; records.length < recordTotal; nextPage++) {
          const next = await DeviceApi.getWarrantyRecords(row.deviceId, { pageNo: nextPage, pageSize: 100 })
          if (!next.records?.list?.length) break
          records.push(...next.records.list)
        }
        row.extendedCount = records.length >= recordTotal ? records.filter((r) => r.extended).length : undefined
      }
    })
  )
}

const search = () => { pageNo.value = 1; void load() }
watch(() => props.projectId, () => { sn.value = ''; search() }, { immediate: true })
onBeforeUnmount(() => { ++loadSequence })
</script>

<style lang="scss" scoped>
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}
.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
.serial-total {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.serial-alert {
  margin-bottom: 10px;
}
</style>
