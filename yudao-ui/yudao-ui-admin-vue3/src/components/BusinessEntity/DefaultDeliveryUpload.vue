<template>
  <div class="default-delivery-upload">
    <h3>交付件</h3>
    <el-tag :type="completed ? 'success' : 'info'">{{ completed ? '已有最新上传记录' : '尚无有效上传' }}</el-tag>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <input v-if="!readonly" ref="input" type="file" aria-label="上传交付件" :disabled="uploading" @change="selected" />
    <el-button v-if="pending && !readonly" :loading="uploading" @click="upload">重试上传</el-button>
    <DefaultDeliveryRecords ref="records" :project-id="projectId" :deliverable-type="deliverableType"
      :business-type="businessType" :business-entity-key="businessEntityKey" :readonly="readonly" @changed="changed" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import * as api from '@/api/pms/platform/businessmodel/delivery'
import type { DeliveryScope } from '@/api/pms/platform/businessmodel/delivery'
import DefaultDeliveryRecords from './DefaultDeliveryRecords.vue'
const props = defineProps<DeliveryScope & { readonly?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const input = ref<HTMLInputElement>(), records = ref<InstanceType<typeof DefaultDeliveryRecords>>()
const completed = ref(false), uploading = ref(false), error = ref('')
const pending = ref<{ scope: api.DeliveryScope; file: File; key: string }>()
let generation = 0
const scope = (): api.DeliveryScope => ({ projectId: props.projectId, businessType: props.businessType,
  businessEntityKey: props.businessEntityKey, deliverableType: props.deliverableType })
const check = async () => {
  const active = ++generation; completed.value = false; error.value = ''
  try { const result = await api.deliveryCompletion(scope()); if (active === generation) completed.value = result.completed }
  catch (failure: any) { if (active === generation) error.value = failure?.message || '完成判断失败' }
}
const changed = async () => { await check(); emit('changed') }
const selected = (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || props.readonly || uploading.value) return
  pending.value = { scope: scope(), file, key: crypto.randomUUID() }; upload()
}
const upload = async () => {
  if (!pending.value || props.readonly || uploading.value) return
  const attempt = pending.value, active = generation; uploading.value = true; error.value = ''
  try {
    await api.uploadDelivery(attempt.scope, attempt.file, attempt.key)
    if (active === generation && pending.value === attempt) {
      pending.value = undefined; if (input.value) input.value.value = ''; await records.value?.reload(); await changed()
    }
  } catch (failure: any) { if (active === generation) error.value = failure?.message || '上传失败，可重试' }
  finally { uploading.value = false }
}
watch(() => [props.projectId, props.businessType, props.businessEntityKey, props.deliverableType], () => {
  pending.value = undefined; if (input.value) input.value.value = ''; check()
}, { immediate: true })
defineExpose({ reload: changed, isUploading: () => uploading.value })
</script>
