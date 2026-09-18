<template>
  <Dialog v-model="visible" :title="form.id ? '编辑设备' : '新增设备'" width="640px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="序列号" prop="sn">
        <el-input v-model="form.sn" :disabled="!!form.id" />
      </el-form-item>
      <el-form-item label="设备名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="设备型号"><el-input v-model="form.productModel" /></el-form-item>
      <el-form-item label="所属客户">
        <PmsEntitySelect
          v-model="form.customerId"
          :api="CustomerApi.getCustomerPage"
          :label-field="['code', 'name']"
          value-field="id"
          query-field="name"
          placeholder="请选择客户"
        />
      </el-form-item>
      <el-form-item label="所属项目">
        <PmsEntitySelect
          v-model="form.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          :disabled="projectLocked"
        />
      </el-form-item>
      <el-alert type="info" :closable="false" show-icon class="mb-16px">
        设备当前位置由安装完成动作生效，此处仅维护设备档案。
      </el-alert>
      <el-form-item label="保修开始日期">
        <el-date-picker
          v-model="form.warrantyStartDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="保修结束日期">
        <el-date-picker
          v-model="form.warrantyEndDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { DeviceArchiveSaveReqVO, DeviceArchiveVO } from '@/api/pms/asset/device/archive'
import * as ProjectApi from '@/api/pms/project/projects'
import * as CustomerApi from '@/api/pms/project/customer'

const props = defineProps<{ lockedProjectId?: number | string }>()
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive<DeviceArchiveSaveReqVO>({
  sn: '',
  name: ''
})
const rules = {
  sn: [{ required: true, message: '请输入序列号' }],
  name: [{ required: true, message: '请输入设备名称' }]
}
const projectLocked = computed(() => props.lockedProjectId != null && props.lockedProjectId !== '')
const lockedProjectNumber = computed(() =>
  projectLocked.value ? Number(props.lockedProjectId) : undefined
)

const open = (row?: DeviceArchiveVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      sn: '',
      name: '',
      productModel: '',
      customerId: undefined,
      projectId: lockedProjectNumber.value,
      warrantyStartDate: undefined,
      warrantyEndDate: undefined,
      remark: ''
    },
    row || {},
    lockedProjectNumber.value != null ? { projectId: lockedProjectNumber.value } : {}
  )
  visible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id
      ? await DeviceArchiveApi.updateDeviceArchive(form.id, form)
      : await DeviceArchiveApi.createDeviceArchive(form)
    message.success('保存成功')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
