<template>
  <Dialog v-model="dialogVisible" title="分配客户">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="负责人" prop="ownerUserId">
        <UserSelect v-model="formData.ownerUserId" class="w-1/1" :clearable="false" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import UserSelect from '@/views/system/user/components/UserSelect.vue'
import * as CustomerApi from '@/api/crm/customer'

const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formData = ref({
  id: undefined as number | undefined,
  ownerUserId: undefined as number | undefined
})
const formRules = reactive({
  ownerUserId: [{ required: true, message: '负责人不能为空', trigger: 'blur' }]
})
const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (id: number) => {
  dialogVisible.value = true
  resetForm()
  formData.value.id = id
  // 获得用户列表

}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    await CustomerApi.distributeCustomer([formData.value.id!], formData.value.ownerUserId!)
    message.success('分配客户成功')
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    ownerUserId: undefined
  }
  formRef.value?.resetFields()
}
</script>
