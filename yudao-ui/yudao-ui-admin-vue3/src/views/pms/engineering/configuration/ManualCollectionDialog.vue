<template><CollectionDialog ref="dialog" entry="configuration" /></template>
<script setup lang="ts">
import CollectionDialog from '@/components/DeviceCollection/CollectionDialog.vue'
const dialog = ref<InstanceType<typeof CollectionDialog>>()
defineExpose({ open: (id: number) => dialog.value!.open(id) })
</script>
