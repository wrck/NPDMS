<template>
  <el-drawer v-model="visible" :title="customer ? '编辑客户' : '创建客户'" size="680px">
    <el-form :model="form" label-width="120px">
      <el-divider content-position="left">CRM 权威字段</el-divider>
      <el-form-item label="客户编码"
        ><el-input v-model="form.code" :disabled="!!customer"
      /></el-form-item>
      <el-form-item label="客户名称"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="来源类型"
        ><el-select v-model="form.sourceType" :disabled="!!customer"
          ><el-option label="CRM 同步" value="CRM_SYNC" /><el-option
            label="平台创建"
            value="PLATFORM_CREATED" /><el-option
            label="平台临时"
            value="PLATFORM_TEMPORARY" /></el-select
      ></el-form-item>
      <el-form-item label="办事处编码"><el-input v-model="form.departmentCode" /></el-form-item>
      <el-form-item label="市场部编码"><el-input v-model="form.marketCode" /></el-form-item>
      <el-form-item label="系统部编码"><el-input v-model="form.systemCode" /></el-form-item>
      <el-form-item label="拓展部编码"><el-input v-model="form.expendCode" /></el-form-item>
      <el-form-item label="子行业编码"><el-input v-model="form.industryCode" /></el-form-item>
      <el-divider content-position="left">平台维护字段</el-divider>
      <el-form-item label="客户简称"><el-input v-model="form.shortName" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template
    >
  </el-drawer>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import * as CustomerApi from '@/api/pms/customer'
import type {
  CustomerCreateReqVO,
  CustomerDetailRespVO,
  CustomerUpdateReqVO
} from '@/api/pms/customer'
const emit = defineEmits<{ success: [] }>()
const visible = ref(false)
const saving = ref(false)
const customer = ref<CustomerDetailRespVO>()
const form = reactive<CustomerCreateReqVO & { changedFields: string[] }>({
  code: '',
  name: '',
  sourceType: 'PLATFORM_CREATED',
  reconciliationPending: false,
  departmentCode: '',
  marketCode: '',
  systemCode: '',
  expendCode: '',
  industryCode: '',
  changedFields: []
})
const open = (value?: CustomerDetailRespVO) => {
  customer.value = value
  Object.assign(
    form,
    {
      code: '',
      name: '',
      shortName: '',
      remark: '',
      sourceType: 'PLATFORM_CREATED',
      reconciliationPending: false,
      departmentCode: '',
      marketCode: '',
      systemCode: '',
      expendCode: '',
      industryCode: '',
      changedFields: []
    },
    value || {}
  )
  visible.value = true
}
const submit = async () => {
  saving.value = true
  try {
    if (customer.value) {
      const fields = [
        'name',
        'shortName',
        'remark',
        'departmentCode',
        'marketCode',
        'systemCode',
        'expendCode',
        'industryCode'
      ] as const
      const data: CustomerUpdateReqVO = {
        changedFields: fields.filter((field) => form[field] !== customer.value?.[field])
      }
      data.changedFields.forEach((field) => {
        data[field as keyof CustomerUpdateReqVO] = form[field as keyof typeof form] as never
      })
      await CustomerApi.updateCustomer(
        customer.value.id,
        data,
        customer.value.version,
        crypto.randomUUID()
      )
    } else {
      await CustomerApi.createCustomer(form, crypto.randomUUID())
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
