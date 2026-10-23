<template>
  <ContentWrap>
    <el-form
      :model="query"
      :label-position="isMobile ? 'top' : 'right'"
      label-width="68px"
      class="query-form"
      @submit.prevent
    >
      <el-form-item label="项目" prop="projectName">
        <el-input
          v-model="query.projectName"
          placeholder="输入项目名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户" prop="customerName">
        <el-input
          v-model="query.customerName"
          placeholder="输入客户名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="办事处" prop="departmentName">
        <el-input
          v-model="query.departmentName"
          placeholder="输入办事处名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="设备SN" prop="sn">
        <el-input
          v-model="query.sn"
          placeholder="输入完整设备SN"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" placeholder="全部状态" clearable>
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DEVICE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <div class="query-actions">
        <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button
          link
          type="primary"
          :aria-expanded="moreFilters"
          @click="moreFilters = !moreFilters"
          >{{ moreFilters ? '收起筛选' : '更多筛选' }}</el-button
        >
      </div>
      <template v-if="moreFilters">
        <el-form-item label="设备名称" prop="name"
          ><el-input
            v-model="query.name"
            placeholder="输入设备名称"
            clearable
            @keyup.enter="handleQuery"
        /></el-form-item>
        <el-form-item label="产品编码" prop="productCode"
          ><el-input
            v-model="query.productCode"
            placeholder="输入产品编码"
            clearable
            @keyup.enter="handleQuery"
        /></el-form-item>
      </template>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="device-list-header">
      <div
        ><span class="device-list-title">设备清单</span
        ><span class="device-list-count">共 {{ total }} 台</span></div
      >
      <el-button :loading="loading" @click="load"><Icon icon="ep:refresh" />刷新</el-button>
    </div>
    <el-table
      v-loading="loading"
      :data="rows"
      row-key="deviceId"
      highlight-current-row
      empty-text="没有匹配的设备，请调整筛选条件"
    >
      <el-table-column
        prop="sn"
        label="设备SN"
        min-width="210"
        :fixed="isMobile ? false : 'left'"
        show-overflow-tooltip
      />
      <el-table-column prop="name" label="设备名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <dict-tag v-if="row.status" :type="DICT_TYPE.PMS_DEVICE_STATUS" :value="row.status" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="projectName" label="项目" min-width="220">
        <template #default="{ row }">
          <router-link
            v-if="row.projectId && (row.projectName || row.projectCode)"
            :to="{
              path: '/pms/project-management/project-master-detail',
              query: { projectId: row.projectId }
            }"
            class="device-project-link"
            @click.stop
          >
            <span class="device-project-name" :title="row.projectName">{{
              row.projectName || '-'
            }}</span>
            <span class="device-project-code" :title="row.projectCode">{{
              row.projectCode || '-'
            }}</span>
          </router-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="customerName" label="客户" min-width="180" show-overflow-tooltip
        ><template #default="{ row }">{{ row.customerName || '-' }}</template></el-table-column
      >
      <el-table-column label="办事处" min-width="140" show-overflow-tooltip
        ><template #default="{ row }">{{
          row.organization?.departmentName || '-'
        }}</template></el-table-column
      >
      <el-table-column prop="productCode" label="产品编码" min-width="120" />
      <el-table-column prop="productModel" label="产品型号" min-width="140" />
      <el-table-column
        prop="shipmentTime"
        label="最新发货"
        min-width="170"
        :formatter="dateFormatter"
      />
      <el-table-column prop="contractNo" label="合同号" min-width="130" />
      <el-table-column
        prop="locationSnapshot"
        label="位置快照"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{
          locationSnapshotText(row.locationSnapshot) || '-'
        }}</template>
      </el-table-column>
      <el-table-column
        prop="warrantyEndDate"
        label="保修截止"
        min-width="120"
        :formatter="dateFormatter2"
      />
      <el-table-column label="所属公司" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.organization?.companyName || '待补齐' }}</template>
      </el-table-column>
      <el-table-column label="归属来源" width="100">
        <template #default="{ row }">{{
          row.organization?.source === 'PROJECT'
            ? '项目'
            : row.organization?.source === 'CONTRACT'
              ? '合同'
              : '待补齐'
        }}</template>
      </el-table-column>
      <el-table-column prop="warrantyStatus" label="维保状态" min-width="100" />
      <el-table-column
        prop="conpVersion"
        label="当前软件版本"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column label="操作" width="240" :fixed="isMobile ? false : 'right'">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click.stop="openDetail(row)"
            :loading="openingDeviceId === row.deviceId"
            >详情</el-button
          >
          <el-button link type="warning" @click.stop="openStatusChange(row)" v-hasPermi="['pms:device:status-change']">状态变更</el-button>
          <el-button link type="danger" @click.stop="remove(row)" v-hasPermi="['pms:device:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>

  <component
    :is="dialogsComponent"
    v-if="dialogsComponent"
    ref="dialogs"
    :project-id="props.projectId"
    @success="load"
  />
</template>
<script setup lang="ts">
import { dateFormatter, dateFormatter2 } from '@/utils/formatTime'
import { locationSnapshotText } from '@/utils/locationSnapshot'
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import type { Component } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type DeviceWorkbenchDialogs from './DeviceWorkbenchDialogs.vue'
import { useRoute } from 'vue-router'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceListVO, DevicePageReqVO } from '@/api/pms/asset/device'

defineOptions({ name: 'PmsAssetDeviceWorkbench' })
const props = defineProps<{ projectId?: number | string }>()
const route = useRoute()
const initialProjectId = () => {
  const value = props.projectId ?? route.query.projectId
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) return value
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}
const loading = ref(false)
const isMobile = useMediaQuery('(max-width: 767px)')
const moreFilters = ref(false)
let listRequest = 0
const rows = ref<DeviceListVO[]>([])
const total = ref(0)
const query = reactive<DevicePageReqVO>({ pageNo: 1, pageSize: 10, projectId: initialProjectId() })
const dialogsComponent = shallowRef<Component>()
const dialogs = ref<InstanceType<typeof DeviceWorkbenchDialogs>>()
const openingDeviceId = ref<number>()
let dialogRequest = 0
const ensureDialogs = async () => {
  if (!dialogsComponent.value)
    dialogsComponent.value = (await import('./DeviceWorkbenchDialogs.vue')).default
  await nextTick()
  return dialogs.value
}
const openStatusChange = async (row: DeviceListVO) => (await ensureDialogs())?.openStatusChange(row)
const remove = async (row: DeviceListVO) => (await ensureDialogs())?.remove(row)
const clearDetail = () => {
  dialogRequest++
  dialogs.value?.clearDetail()
}
const openDetail = async (row: DeviceListVO) => {
  const request = ++dialogRequest
  openingDeviceId.value = row.deviceId
  try {
    const target = await ensureDialogs()
    if (request !== dialogRequest || !target) return
    await target.openDetail(row)
  } finally {
    if (openingDeviceId.value === row.deviceId) openingDeviceId.value = undefined
  }
}

const load = async () => {
  const request = ++listRequest
  loading.value = true
  try {
    const data = await DeviceApi.getDevicePage({ ...query })
    if (request !== listRequest) return
    rows.value = data.list
    total.value = data.total
  } finally {
    if (request === listRequest) loading.value = false
  }
}
const handleQuery = () => {
  query.pageNo = 1
  load()
}
const resetQuery = () => {
  query.name = undefined
  query.status = undefined
  query.sn = undefined
  query.productCode = undefined
  query.projectId = initialProjectId()
  query.customerId = undefined
  query.projectName = undefined
  query.customerName = undefined
  query.departmentName = undefined
  handleQuery()
}
onMounted(load)
onBeforeUnmount(() => {
  listRequest++
  clearDetail()
})
watch(
  () => props.projectId ?? route.query.projectId,
  () => {
    query.projectId = initialProjectId()
    clearDetail()
    handleQuery()
  }
)
</script>
<style scoped>
.device-project-link {
  display: block;
  color: var(--el-color-primary);
  text-decoration: none;
}
.device-project-link:hover .device-project-name {
  text-decoration: underline;
}
.device-project-name,
.device-project-code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 22px;
}
.device-project-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.query-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 20px;
}
.query-form :deep(.el-form-item) {
  margin: 0;
  min-width: 0;
}
.query-form :deep(.el-select) {
  width: 100%;
}
.query-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.query-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.device-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.device-list-title {
  white-space: nowrap;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.device-list-count {
  white-space: nowrap;
  margin-left: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
@media (width <= 1100px) {
  .query-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (width <= 767px) {
  .query-form {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
  .query-actions {
    padding-left: 0;
  }
  .device-list-header {
    flex-wrap: wrap;
  }
  .device-list-header > div {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 4px 8px;
  }
  .device-list-count {
    margin-left: 0;
  }
}
</style>
