<template>
  <Dialog
    :model-value="modelValue"
    title="新增换货申请"
    width="min(960px, 95vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="
      (value) => {
        if (!saving) emit('update:modelValue', value)
      }
    "
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="saving">
      <el-form-item label="物料选择">
        <MaterialDevicePicker v-if="modelValue" :project-id="projectId" v-model="form.serials" />
      </el-form-item>
      <el-form-item label="换货原因" prop="reason">
        <Editor v-if="modelValue" v-model="form.reason" height="180px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存换货申请</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import MaterialDevicePicker from '@/views/pms/engineering/material-exch/MaterialDevicePicker.vue'
import {
  createMaterialExchange,
  type MaterialExchangeSerialVO
} from '@/api/pms/engineering/material-exch'
import { useMessage } from '@/hooks/web/useMessage'
const props = defineProps<{
  modelValue: boolean
  projectId: number
  saveSurvey: () => Promise<{ id: number | string; projectId: number; name: string }>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; created: [] }>()
const message = useMessage()
const formRef = ref()
const form = reactive({ serials: [] as MaterialExchangeSerialVO[], reason: '' })
const rules = { reason: [{ required: true, message: '请输入换货原因' }] }
const saving = ref(false)
const error = ref('')
const save = async () => {
  if (saving.value) return
  error.value = ''
  if (!form.serials.length) {
    error.value = '请选择物料并填写换货数量'
    return
  }
  if (!form.reason.replace(/<[^>]*>/g, '').trim()) {
    error.value = '请输入换货原因'
    return
  }
  await formRef.value.validate()
  const projectId = props.projectId
  saving.value = true
  try {
    const survey = await props.saveSurvey()
    if (
      String(survey.projectId) !== String(projectId) ||
      String(props.projectId) !== String(projectId)
    )
      throw new Error('工勘所属项目已变化，请重新打开换货申请')
    await createMaterialExchange({
      projectId,
      code: `ME-${Date.now()}`,
      name: `工勘换货：${survey.name}`,
      exchangeType: 'INCOMPATIBLE',
      serials: form.serials,
      quantity: form.serials.reduce((sum, row) => sum + (row.quantity ?? 1), 0),
      unit: '个',
      reason: form.reason,
      remark: `来源工勘 #${survey.id}`,
      applicantUserId: undefined!
    })
    message.success('换货申请创建成功')
    emit('created')
    emit('update:modelValue', false)
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '换货申请保存失败，请重试'
  } finally {
    saving.value = false
  }
}
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      form.serials = []
      form.reason = ''
      error.value = ''
    }
  }
)
</script>
