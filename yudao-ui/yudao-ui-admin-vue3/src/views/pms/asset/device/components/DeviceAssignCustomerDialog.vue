<template>
  <Dialog v-model="visible" title="调整客户归属" width="520px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="当前客户">{{ summary?.customerId || '--' }}</el-form-item>
      <el-form-item label="目标客户"
        ><el-input-number v-model="form.customerId" :min="1"
      /></el-form-item>
      <el-form-item label="关系类型"><el-input v-model="form.relationshipType" /></el-form-item>
      <el-form-item label="变更原因"
        ><el-input v-model="form.reason" type="textarea"
      /></el-form-item>
      <el-form-item label="归属版本">{{ summary?.customerAssignmentVersion }}</el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="loading" @click="submit">确认</el-button></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as DeviceApi from '@/api/pms/asset/device'
import type { DeviceSummaryVO } from '@/api/pms/asset/device'
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const summary = ref<DeviceSummaryVO>()
const form = reactive({
  customerId: undefined as number | undefined,
  relationshipType: 'DIRECT',
  reason: ''
})
const open = (value: DeviceSummaryVO) => {
  summary.value = value
  form.customerId = value.customerId
  form.relationshipType = 'DIRECT'
  form.reason = ''
  visible.value = true
}
const submit = async () => {
  if (!summary.value || !form.customerId || !form.relationshipType.trim() || !form.reason.trim())
    return message.warning('请填写客户、关系类型和变更原因')
  loading.value = true
  try {
    await DeviceApi.assignCustomer(
      summary.value.deviceId,
      { customerId: form.customerId, relationshipType: form.relationshipType, reason: form.reason },
      summary.value.customerAssignmentVersion,
      crypto.randomUUID()
    )
    message.success('客户归属已更新')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
