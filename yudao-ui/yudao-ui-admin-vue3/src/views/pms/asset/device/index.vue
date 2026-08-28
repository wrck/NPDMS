<template>
  <ContentWrap>
    <el-form :model="query" inline class="-mb-15px">
      <el-form-item label="设备SN"
        ><el-input v-model="query.sn" clearable @keyup.enter="handleQuery"
      /></el-form-item>
      <el-form-item label="产品编码"
        ><el-input v-model="query.productCode" clearable @keyup.enter="handleQuery"
      /></el-form-item>
      <el-form-item label="项目ID"
        ><el-input-number v-model="query.projectId" :min="1" controls-position="right"
      /></el-form-item>
      <el-form-item label="客户ID"
        ><el-input-number v-model="query.customerId" :min="1" controls-position="right"
      /></el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="rows" @row-click="openDetail">
      <el-table-column prop="sn" label="设备SN" min-width="160" fixed="left" />
      <el-table-column prop="productCode" label="产品编码" min-width="120" />
      <el-table-column prop="productModel" label="产品型号" min-width="140" />
      <el-table-column prop="shipmentTime" label="最新发货" min-width="170" />
      <el-table-column prop="packageNo" label="装箱单号" min-width="130" />
      <el-table-column prop="contractNo" label="合同号" min-width="130" />
      <el-table-column prop="projectId" label="当前项目" min-width="100" />
      <el-table-column prop="customerId" label="当前客户" min-width="100" />
      <el-table-column prop="warrantyStatus" label="维保状态" min-width="100" />
      <el-table-column prop="conpVersion" label="当前CONP" min-width="180" show-overflow-tooltip />
      <el-table-column prop="syncStatus" label="来源状态" min-width="120" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }"
          ><el-button link @click.stop="openDetail(row)">详情</el-button></template
        >
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>

  <ContentWrap v-if="detail" v-loading="detailLoading" class="device-detail">
    <div class="detail-heading">
      <div>
        <h3>{{ detail.summary.sn }}</h3>
        <span>{{
          detail.summary.productName ||
          detail.summary.productModel ||
          detail.summary.productCode ||
          '未维护产品信息'
        }}</span>
      </div>
      <div class="detail-actions">
        <el-button @click="assignmentHistoryDrawer?.open(detail.summary.deviceId)"
          >项目历史</el-button
        >
        <el-button @click="customerRelationshipDrawer?.open(detail.summary.deviceId)"
          >客户关系</el-button
        >
        <el-button @click="assemblyTreeDrawer?.open(detail.summary.deviceId)">装配树</el-button>
        <el-button
          v-hasPermi="['pms:device:assign']"
          @click="assignProjectDialog?.open(detail.summary)"
          >调整项目</el-button
        >
        <el-button
          v-hasPermi="['pms:device:assign']"
          @click="assignCustomerDialog?.open(detail.summary)"
          >调整客户</el-button
        >
      </div>
    </div>
    <DeviceSummaryPanel :summary="detail.summary" />
    <el-tabs v-model="activeTab" class="device-tabs">
      <el-tab-pane label="出厂信息" name="factory"
        ><DeviceFactoryPanel :slice="detail.factory"
      /></el-tab-pane>
      <el-tab-pane label="官网信息" name="official"
        ><DeviceOfficialInfoPanel :slice="detail.official"
      /></el-tab-pane>
      <el-tab-pane label="在网版本" name="network"
        ><DeviceNetworkVersionPanel :slice="detail.networkVersion"
      /></el-tab-pane>
      <el-tab-pane label="技术公告" name="notice"
        ><DeviceTechnicalNoticePanel :slice="detail.technicalNotice"
      /></el-tab-pane>
      <el-tab-pane label="维保信息" name="warranty"
        ><DeviceWarrantyPanel :device-id="detail.summary.deviceId" :slice="detail.warranty"
      /></el-tab-pane>
      <el-tab-pane label="配置Log" name="configuration"
        ><DeviceConfigurationLogPanel
          :device-id="detail.summary.deviceId"
          :slice="detail.configurationLog"
      /></el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <DeviceAssignmentHistoryDrawer ref="assignmentHistoryDrawer" />
  <DeviceCustomerRelationshipDrawer ref="customerRelationshipDrawer" />
  <DeviceAssemblyTreeDrawer ref="assemblyTreeDrawer" />
  <DeviceAssignProjectDialog ref="assignProjectDialog" @success="refreshDetail" />
  <DeviceAssignCustomerDialog ref="assignCustomerDialog" @success="refreshDetail" />
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceDetailVO, DeviceListVO, DevicePageReqVO } from '@/api/pms/asset/device'
import DeviceSummaryPanel from './components/DeviceSummaryPanel.vue'
import DeviceFactoryPanel from './components/DeviceFactoryPanel.vue'
import DeviceOfficialInfoPanel from './components/DeviceOfficialInfoPanel.vue'
import DeviceNetworkVersionPanel from './components/DeviceNetworkVersionPanel.vue'
import DeviceTechnicalNoticePanel from './components/DeviceTechnicalNoticePanel.vue'
import DeviceWarrantyPanel from './components/DeviceWarrantyPanel.vue'
import DeviceConfigurationLogPanel from './components/DeviceConfigurationLogPanel.vue'
import DeviceAssignmentHistoryDrawer from './components/DeviceAssignmentHistoryDrawer.vue'
import DeviceCustomerRelationshipDrawer from './components/DeviceCustomerRelationshipDrawer.vue'
import DeviceAssemblyTreeDrawer from './components/DeviceAssemblyTreeDrawer.vue'
import DeviceAssignProjectDialog from './components/DeviceAssignProjectDialog.vue'
import DeviceAssignCustomerDialog from './components/DeviceAssignCustomerDialog.vue'

defineOptions({ name: 'PmsAssetDeviceWorkbench' })
const loading = ref(false)
const detailLoading = ref(false)
const rows = ref<DeviceListVO[]>([])
const total = ref(0)
const detail = ref<DeviceDetailVO>()
const activeTab = ref('factory')
const query = reactive<DevicePageReqVO>({ pageNo: 1, pageSize: 10 })
const assignmentHistoryDrawer = ref<InstanceType<typeof DeviceAssignmentHistoryDrawer>>()
const customerRelationshipDrawer = ref<InstanceType<typeof DeviceCustomerRelationshipDrawer>>()
const assemblyTreeDrawer = ref<InstanceType<typeof DeviceAssemblyTreeDrawer>>()
const assignProjectDialog = ref<InstanceType<typeof DeviceAssignProjectDialog>>()
const assignCustomerDialog = ref<InstanceType<typeof DeviceAssignCustomerDialog>>()

const load = async () => {
  loading.value = true
  try {
    const data = await DeviceApi.getDevicePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  query.pageNo = 1
  load()
}
const resetQuery = () => {
  query.sn = undefined
  query.productCode = undefined
  query.projectId = undefined
  query.customerId = undefined
  handleQuery()
}
const openDetail = async (row: DeviceListVO) => {
  detailLoading.value = true
  try {
    detail.value = await DeviceApi.getDevice(row.deviceId)
    activeTab.value = 'factory'
  } finally {
    detailLoading.value = false
  }
}
const refreshDetail = async () => {
  if (!detail.value) return
  const currentId = detail.value.summary.deviceId
  detail.value = await DeviceApi.getDevice(currentId)
  await load()
}
onMounted(load)
</script>
<style scoped>
.device-detail {
  overflow: hidden;
}

.detail-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.detail-heading h3 {
  margin: 0 0 6px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.device-tabs {
  margin-top: 16px;
}

@media (width <= 767px) {
  .detail-heading {
    flex-direction: column;
  }

  .detail-actions {
    justify-content: flex-start;
  }
}
</style>
