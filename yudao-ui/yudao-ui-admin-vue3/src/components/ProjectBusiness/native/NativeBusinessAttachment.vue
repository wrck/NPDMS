<template>
  <ProjectBusinessNativeAttachments v-if="record.id" ref="attachments" :api="bridge.api" :entity-id="record.id" :policy="policy"
    :readonly="readonly" :legacy="legacy" @changed="changed" />
  <span v-else>保存后可上传附件。</span>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import ProjectBusinessNativeAttachments, { type NativeAttachmentPolicy } from '../ProjectBusinessNativeAttachments.vue'
import { useNativeBusiness, type NativeValues } from './nativeBusinessContext'
const props = defineProps<{ record: NativeValues; policy: NativeAttachmentPolicy; readonly?: boolean; legacy?: string; kind?: string }>()
const emit = defineEmits<{ version: [version: number | undefined] }>()
const context = useNativeBusiness()!, attachments = ref<InstanceType<typeof ProjectBusinessNativeAttachments>>()
const bridge = context.getBridge(props.kind)
const changed = async () => {
  const { row } = await bridge.read(props.record.id)
  emit('version', row.concurrencyBasis)
}
const unregister = context.registerForm({ kind: props.kind, buildInput: async () => ({}), requestLeave: async () => await attachments.value?.requestLeave() ?? true })
onBeforeUnmount(unregister)
</script>
