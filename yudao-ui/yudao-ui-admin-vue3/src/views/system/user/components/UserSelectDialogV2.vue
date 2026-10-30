<template>
  <SystemUserDialog
    ref="dialog"
    v-bind="$attrs"
    @selected="(rows, activityId) => emit('selected', rows, activityId)"
  />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import SystemUserDialog from '@/components/SystemUserSelect/SystemUserDialog.vue'
defineOptions({ name: 'UserSelectDialogV2', inheritAttrs: false })
const emit = defineEmits<{ selected: [rows: any[], activityId?: any] }>()
const dialog = ref<InstanceType<typeof SystemUserDialog>>()
defineExpose({
  open: (...args: Parameters<InstanceType<typeof SystemUserDialog>['open']>) =>
    dialog.value?.open(...args)
})
</script>
