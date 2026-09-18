<template>
  <Dialog v-model="visible" title="设备状态变更" width="520px">
    <el-form ref="statusFormRef" :model="statusForm" :rules="statusRules" label-width="120px">
      <el-form-item label="设备">
        <span>{{ current?.sn }} {{ current?.name }}</span>
        <dict-tag
          v-if="current?.status"
          :type="DICT_TYPE.PMS_DEVICE_STATUS"
          :value="current.status"
          class="ml-8px"
        />
      </el-form-item>
      <el-form-item label="动作" prop="action">
        <el-select v-model="statusForm.action" class="!w-220px" @change="onActionChange">
          <el-option value="DEPLOY" label="DEPLOY 部署" />
          <el-option value="REPORT_FAULT" label="REPORT_FAULT 故障上报" />
          <el-option value="START_REPAIR" label="START_REPAIR 开始维修" />
          <el-option value="COMPLETE_REPAIR" label="COMPLETE_REPAIR 完成维修" />
          <el-option value="SCRAP" label="SCRAP 报废" />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="statusForm.action === 'COMPLETE_REPAIR'"
        label="目标状态"
        prop="targetStatus"
      >
        <el-select v-model="statusForm.targetStatus" class="!w-220px">
          <el-option value="IN_STOCK" label="IN_STOCK 在库" />
          <el-option value="IN_USE" label="IN_USE 在用" />
        </el-select>
      </el-form-item>
      <el-form-item label="变更描述"
        ><el-input v-model="statusForm.changeDescription" type="textarea"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE } from '@/utils/dict'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type {
  DeviceArchiveStatusChangeReqVO,
  DeviceArchiveVO
} from '@/api/pms/asset/device/archive'

const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false)
const saving = ref(false)
const statusFormRef = ref()
const current = ref<DeviceArchiveVO>()
const statusForm = reactive<DeviceArchiveStatusChangeReqVO>({
  id: 0,
  action: 'DEPLOY',
  targetStatus: undefined,
  changeDescription: ''
})
const statusRules = {
  id: [{ required: true, message: '请输入设备编号' }],
  action: [{ required: true, message: '请选择动作' }],
  targetStatus: [{ required: true, message: '请选择目标状态' }]
}

const open = (row: DeviceArchiveVO) => {
  current.value = row
  Object.assign(statusForm, {
    id: row.id,
    action: 'DEPLOY',
    targetStatus: undefined,
    changeDescription: ''
  })
  visible.value = true
}
const onActionChange = () => {
  if (statusForm.action !== 'COMPLETE_REPAIR') {
    statusForm.targetStatus = undefined
  }
}
const submit = async () => {
  await statusFormRef.value.validate()
  saving.value = true
  try {
    await DeviceArchiveApi.changeDeviceArchiveStatus(statusForm.id!, statusForm)
    message.success('状态变更成功')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
