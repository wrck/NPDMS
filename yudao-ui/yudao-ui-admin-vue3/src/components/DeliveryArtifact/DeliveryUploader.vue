<template>
  <div v-hasPermi="['pms:delivery:operate']">
    <input ref="fileInput" type="file" :accept="accept" :disabled="busy || !!uploadedReferenceId" @change="selectFile" />
    <el-button type="primary" :loading="busy" :disabled="!file && !uploadedReferenceId" @click="submit">
      {{ uploadedReferenceId ? '重试登记交付件' : '上传并登记交付件' }}
    </el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { FileId } from '@/api/pms/platform/file'
import { type DeliveryMaterialVO, type DeliveryTypeVO } from '@/api/pms/platform/delivery'
import { createDeliveryUploadAttempt, uploadDeliveryFile, type DeliveryUploadAttempt } from './uploadDeliveryFile'

const props = defineProps<{
  ownerModule: string
  entityType: string
  entityId: string | number
  type: DeliveryTypeVO
  title?: string
}>()
const emit = defineEmits<{ completed: [material: DeliveryMaterialVO] }>()
const file = ref<File>()
const fileInput = ref<HTMLInputElement>()
const busy = ref(false)
const error = ref('')
const uploadedReferenceId = ref<FileId>()
const attempt = ref<DeliveryUploadAttempt>()
const accept = computed(() => {
  try { return (JSON.parse(props.type.allowedMediaJson) as string[]).map(value => value.includes('/') || value.startsWith('.') ? value : `.${value}`).join(',') }
  catch { return '' }
})
const selectFile = (event: Event) => {
  file.value = (event.target as HTMLInputElement).files?.[0]
  attempt.value = undefined
  error.value = ''
}
const submit = async () => {
  if (busy.value || (!file.value && !uploadedReferenceId.value)) return
  busy.value = true
  error.value = ''
  try {
    attempt.value ||= createDeliveryUploadAttempt({ ownerModule: props.ownerModule,
      entityType: props.entityType, entityId: props.entityId }, props.type, file.value!, props.title)
    const material = await uploadDeliveryFile(attempt.value)
    emit('completed', material)
    file.value = undefined
    if (fileInput.value) fileInput.value.value = ''
    uploadedReferenceId.value = undefined
    attempt.value = undefined
  } catch (failure: any) {
    uploadedReferenceId.value = attempt.value?.referenceId
    error.value = failure?.response?.data?.msg || failure?.message || '交付件上传登记失败'
    if (uploadedReferenceId.value) error.value += '；文件已保存，请重试登记交付件'
  } finally { busy.value = false }
}
defineExpose({ isBusy: () => busy.value, hasPendingFile: () => !!file.value || !!uploadedReferenceId.value })
</script>
