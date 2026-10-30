<template>
  <BusinessCollectionDialog ref="dialog" v-bind="props" />
</template>

<script setup lang="ts">
import BusinessCollectionDialog from './BusinessCollectionDialog.vue'
import type { Entry, Id } from '@/api/pms/platform/deviceCollection'

const props = defineProps<{ entry: Entry; deviceId?: Id; readonly?: boolean }>()
const dialog = ref<InstanceType<typeof BusinessCollectionDialog>>()
const open = (...args: Parameters<InstanceType<typeof BusinessCollectionDialog>['open']>) =>
  dialog.value?.open(...args)
defineExpose({ open })
</script>
