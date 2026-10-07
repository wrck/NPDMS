<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <template v-if="context">
    <el-form inline><el-form-item label="交付件类型"><el-input v-model="type" aria-label="交付件类型" :disabled="uploader?.isUploading()" /></el-form-item></el-form>
    <DefaultDeliveryUpload v-if="type" ref="uploader" :key="`${context.businessType}:${context.businessEntityKey}:${type}`"
      v-bind="context" :deliverable-type="type" :readonly="readonly" @changed="$emit('changed')" />
  </template>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { deliveryContext, type DeliveryScope } from '@/api/pms/platform/businessmodel/delivery'
import DefaultDeliveryUpload from './DefaultDeliveryUpload.vue'
const props = withDefaults(defineProps<{ ownerModule: string; entityType: string; entityId: string | number; deliverableType?: string; readonly?: boolean }>(), { deliverableType: 'ATTACHMENT' })
defineEmits<{ changed: [] }>()
const context = ref<Omit<DeliveryScope, 'deliverableType'>>(), error = ref(''), type = ref(props.deliverableType)
const uploader = ref<InstanceType<typeof DefaultDeliveryUpload>>()
let generation = 0
watch(() => [props.ownerModule, props.entityType, props.entityId], async () => {
  const active = ++generation; context.value = undefined; error.value = ''
  try {
    const result = await deliveryContext(props.ownerModule, props.entityType, props.entityId)
    if (!result || !/^[1-9][0-9]*$/.test(String(result.projectId)) || !result.businessType || String(result.businessEntityKey) !== String(props.entityId)) throw new Error('交付件业务上下文不匹配')
    if (active === generation) context.value = result
  }
  catch (failure: any) { if (active === generation) error.value = failure?.message || '交付件业务上下文不可用' }
}, { immediate: true })
</script>
