<template>
  <DeviceArchiveFormDialog
    ref="formDialog"
    :locked-project-id="props.projectId"
    @success="refreshAll"
    @closed="clearDetail"
  >
    <template #actions>
      <div v-if="detail" class="detail-actions">
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
    </template>
    <template #detail>
      <template v-if="detail">
        <el-tab-pane label="归属摘要" name="summary">
          <DeviceSummaryPanel :summary="detail.summary" :organization="detail.organization" />
        </el-tab-pane>
        <el-tab-pane label="位置与变更轨迹" name="location" lazy>
          <DeviceLocationPanel :device-id="detail.summary.deviceId" />
        </el-tab-pane>
        <el-tab-pane label="出厂信息" name="factory"
          ><DeviceFactoryPanel :slice="detail.factory"
        /></el-tab-pane>
        <el-tab-pane label="官网信息" name="official"
          ><DeviceOfficialInfoPanel :slice="detail.official"
        /></el-tab-pane>
        <el-tab-pane label="在网版本" name="network"
          ><DeviceNetworkVersionPanel
            :slice="detail.networkVersion"
            :device-id="detail.summary.deviceId"
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
      </template>
    </template>
  </DeviceArchiveFormDialog>

  <DeviceAssignmentHistoryDrawer ref="assignmentHistoryDrawer" />
  <DeviceCustomerRelationshipDrawer ref="customerRelationshipDrawer" />
  <DeviceAssemblyTreeDrawer ref="assemblyTreeDrawer" />
  <DeviceAssignProjectDialog ref="assignProjectDialog" @success="refreshDetail" />
  <DeviceAssignCustomerDialog ref="assignCustomerDialog" @success="refreshDetail" />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { DeviceDetailVO, DeviceListVO, DeviceSummaryVO } from '@/api/pms/asset/device'
import type { DeviceArchiveVO } from '@/api/pms/asset/device/archive'
import DeviceLocationPanel from './components/DeviceLocationPanel.vue'
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
import DeviceArchiveFormDialog from './components/DeviceArchiveFormDialog.vue'

const props = defineProps<{ projectId?: number | string }>()
const emit = defineEmits<{ success: [] }>()
const detail = ref<DeviceDetailVO & { archive?: DeviceArchiveVO }>()
let detailRequest = 0
const clearDetail = () => {
  detailRequest++
  detail.value = undefined
}
const assignmentHistoryDrawer = ref<InstanceType<typeof DeviceAssignmentHistoryDrawer>>()
const customerRelationshipDrawer = ref<InstanceType<typeof DeviceCustomerRelationshipDrawer>>()
const assemblyTreeDrawer = ref<InstanceType<typeof DeviceAssemblyTreeDrawer>>()
const assignProjectDialog = ref<InstanceType<typeof DeviceAssignProjectDialog>>()
const assignCustomerDialog = ref<InstanceType<typeof DeviceAssignCustomerDialog>>()
const formDialog = ref<InstanceType<typeof DeviceArchiveFormDialog>>()

const openDetail = async (row: DeviceListVO | DeviceSummaryVO) => {
  const request = ++detailRequest
  const [data, archive] = await Promise.all([
    DeviceApi.getDevice(row.deviceId),
    DeviceArchiveApi.getDeviceArchiveRecord(row.deviceId)
  ])
  if (request !== detailRequest) return
  detail.value = { ...data, archive }
  formDialog.value?.open(archive)
}
const refreshDetail = async () => {
  if (!detail.value) return
  await openDetail(detail.value.summary)
}
const refreshAll = async () => {
  clearDetail()
  emit('success')
}
defineExpose({ openDetail, clearDetail })
</script>
<style scoped>
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.detail-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
@media (width <= 767px) {
  .detail-actions {
    justify-content: flex-start;
  }
}
</style>
