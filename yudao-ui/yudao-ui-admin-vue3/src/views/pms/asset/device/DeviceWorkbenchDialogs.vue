<template>
  <DeviceArchiveFormDialog
    ref="formDialog"
    :locked-project-id="props.projectId"
    @success="refreshAll"
    @closed="clearDetail"
  >
    <template #actions>
      <div v-if="detail" class="detail-actions">
        <el-button
          v-hasPermi="['pms:device:status-change']"
          :disabled="!detail.archive"
          @click="statusChangeDialog?.open(detail.archive!)"
          >状态变更</el-button
        >
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
        <el-tab-pane label="发货事实" name="shipments"
          ><DeviceShipmentPanel :device-id="detail.summary.deviceId"
        /></el-tab-pane>
        <el-tab-pane label="出厂信息" name="factory"
          ><DeviceFactoryPanel :slice="detail.factory" :device-id="detail.summary.deviceId"
        /></el-tab-pane>
        <el-tab-pane label="主附SN关系" name="contractRelationships" lazy>
          <DeviceContractRelationshipPanel :device-id="detail.summary.deviceId" />
        </el-tab-pane>
        <el-tab-pane label="官网信息" name="official"
          ><DeviceOfficialInfoPanel :slice="detail.official"
        /></el-tab-pane>
        <el-tab-pane label="在网版本" name="network"
          ><DeviceNetworkVersionPanel
            :slice="detail.networkVersion"
            :device-id="detail.summary.deviceId"
            :project-id="detail.summary.projectId"
            @updated="refreshSoftwareVersion"
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
  <DeviceArchiveStatusChangeDialog ref="statusChangeDialog" @success="refreshAll" />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as DeviceApi from '@/api/pms/asset/device'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { DeviceDetailVO, DeviceListVO, DeviceSummaryVO } from '@/api/pms/asset/device'
import type { DeviceArchiveVO } from '@/api/pms/asset/device/archive'
import DeviceShipmentPanel from './components/DeviceShipmentPanel.vue'
import DeviceContractRelationshipPanel from './components/DeviceContractRelationshipPanel.vue'
import DeviceArchiveStatusChangeDialog from './components/DeviceArchiveStatusChangeDialog.vue'
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

const message = useMessage()
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
const statusChangeDialog = ref<InstanceType<typeof DeviceArchiveStatusChangeDialog>>()

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
const refreshSoftwareVersion = async () => {
  if (!detail.value) return
  const deviceId = detail.value.summary.deviceId
  const request = ++detailRequest
  const data = await DeviceApi.getDevice(deviceId)
  if (request !== detailRequest || detail.value?.summary.deviceId !== deviceId) return
  detail.value = { ...data, archive: detail.value.archive }
  emit('success')
}
const refreshAll = async () => {
  clearDetail()
  emit('success')
}
const openStatusChange = async (row: DeviceListVO) => {
  const archive = await DeviceArchiveApi.getDeviceArchiveRecord(row.deviceId).catch(() => undefined)
  if (!archive) return message.error('设备档案不存在')
  statusChangeDialog.value?.open(archive)
}
const remove = async (row: DeviceListVO) => {
  await message.delConfirm()
  await DeviceArchiveApi.deleteDeviceArchive(row.deviceId)
  message.success('删除成功')
  if (detail.value?.summary.deviceId === row.deviceId) clearDetail()
  emit('success')
}
defineExpose({ openDetail, clearDetail, openStatusChange, remove })
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
