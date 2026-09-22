<template>
  <DeviceWorkbench v-if="projectId != null" :project-id="projectId" />
</template>
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DeviceWorkbench from '../index.vue'
defineOptions({ name: 'PmsAssetDeviceArchive' })
const props = defineProps<{ projectId?: number | string }>()
const route = useRoute()
const router = useRouter()
onMounted(() => {
  if (props.projectId == null) {
    router.replace({ path: '/customer-asset/devices', query: route.query, hash: route.hash })
  }
})
</script>
