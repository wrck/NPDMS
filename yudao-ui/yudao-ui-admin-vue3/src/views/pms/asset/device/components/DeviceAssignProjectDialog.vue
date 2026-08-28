<template>
  <Dialog v-model="visible" title="调整项目归属" width="520px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="当前项目">{{ summary?.projectId || '--' }}</el-form-item>
      <el-form-item label="目标项目"
        ><el-input-number v-model="form.projectId" :min="1"
      /></el-form-item>
      <el-form-item label="变更原因"
        ><el-input v-model="form.reason" type="textarea"
      /></el-form-item>
      <el-form-item label="归属版本">{{ summary?.projectAssignmentVersion }}</el-form-item>
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
const form = reactive({ projectId: undefined as number | undefined, reason: '' })
const open = (value: DeviceSummaryVO) => {
  summary.value = value
  form.projectId = value.projectId
  form.reason = ''
  visible.value = true
}
const submit = async () => {
  if (!summary.value || !form.projectId || !form.reason.trim())
    return message.warning('请填写目标项目和变更原因')
  loading.value = true
  try {
    await DeviceApi.assignProject(
      summary.value.deviceId,
      { projectId: form.projectId, reason: form.reason },
      summary.value.projectAssignmentVersion,
      crypto.randomUUID()
    )
    message.success('项目归属已更新')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
