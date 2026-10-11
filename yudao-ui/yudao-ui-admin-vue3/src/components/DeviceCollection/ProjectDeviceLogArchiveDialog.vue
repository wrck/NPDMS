<template>
  <Dialog
    v-model="visible"
    title="项目设备日志与文件历史"
    width="min(1280px, 96vw)"
    scroll
    max-height="calc(90vh - 160px)"
  >
    <DeviceLogArchive v-if="visible && projectId" :project-id="projectId" :readonly="readonly" />
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </Dialog>
</template>

<script setup lang="ts">
import DeviceLogArchive from '@/views/pms/asset/device-log-archive/index.vue'
import type { Identifier } from '@/api/pms/asset/device-log-archive'

defineProps<{ projectId: Identifier; readonly?: boolean }>()
const visible = defineModel<boolean>({ default: false })
</script>
