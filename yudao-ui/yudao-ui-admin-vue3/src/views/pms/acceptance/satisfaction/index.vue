<template>
  <CompletionConfirmationWorkbench ref="workbench" :project-id="props.projectId" :readonly="props.readonly" @changed="emit('changed')" @dirty-change="value => emit('dirty-change', value)" />
</template>
<script setup lang="ts">
import CompletionConfirmationWorkbench from '../completion-certificate/index.vue'
defineOptions({ name: 'PmsSatisfactionWorkbench' })
const props = defineProps<{ projectId?: number; readonly?: boolean }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const workbench = ref<InstanceType<typeof CompletionConfirmationWorkbench>>()
defineExpose({ requestLeave: () => workbench.value?.requestLeave() ?? true,
  isDirty: () => workbench.value?.isDirty() ?? false, discardChanges: () => workbench.value?.discardChanges() ?? true })
</script>
