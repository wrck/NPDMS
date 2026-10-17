<template>
  <Dialog v-model="visible" title="维护项目客户信息" width="600px">
    <el-descriptions :column="1" border class="mb-16px">
      <el-descriptions-item label="客户名称">{{ customer?.name }}</el-descriptions-item>
      <el-descriptions-item label="客户编号">{{ customer?.code }}</el-descriptions-item>
    </el-descriptions>
    <el-alert title="此处维护客户共享的平台备注，客户名称、编码及 CRM 资料保持只读。" type="info" :closable="false" class="mb-16px" />
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" class="mb-16px" />
    <el-form label-position="top">
      <el-form-item label="平台备注">
        <el-input v-model="remark" type="textarea" :rows="5" aria-label="平台备注" :disabled="saving" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false" :disabled="saving">取消</el-button>
      <el-button type="primary" @click="save" :loading="saving">保存</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import * as CustomerApi from '@/api/pms/customer'
import { generateUUID } from '@/utils'
import { useMessage } from '@/hooks/web/useMessage'
const props = defineProps<{ projectId: number | string }>()
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false), saving = ref(false)
const customer = ref<CustomerApi.CustomerRespVO>()
const remark = ref(''), operationKey = ref(''), saveError = ref('')
watch(remark, () => { operationKey.value = generateUUID() })
const open = async () => {
  try {
    const data = await CustomerApi.getProjectCustomerOverview(props.projectId)
    if (!data) { message.error('当前项目尚未关联客户'); return }
    saveError.value = ''
    customer.value = data
    remark.value = data.remark || ''
    operationKey.value = generateUUID()
    visible.value = true
  } catch { message.error('客户维护资料加载失败，请重试。') }
}
const save = async () => {
  if (saving.value || !customer.value) return
  saveError.value = ''
  saving.value = true
  try {
    await CustomerApi.updateProjectCustomerExtension(props.projectId, { remark: remark.value }, customer.value.version, operationKey.value)
    message.success('客户备注已保存')
    visible.value = false
    emit('success')
  } catch { saveError.value = '客户备注保存失败，请检查权限或刷新后重试。' }
  finally { saving.value = false }
}
defineExpose({ open })
</script>
